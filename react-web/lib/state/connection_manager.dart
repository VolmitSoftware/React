import 'dart:async' show StreamController, StreamSubscription, Timer, unawaited;
import 'dart:math' show min;

import '../model/ring_buffer.dart';
import '../model/sampler_sample.dart';
import '../model/server_revisions.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../service/react_socket.dart';
import '../service/relay_connection_interface.dart';

enum ConnState { connecting, live, degraded, offline }

/// Whether the server still accepts this entry's credential. Anything but
/// [granted] keeps [ConnState.offline]; [revoked] is terminal.
enum ConnAccess { granted, revoked, wrongServer }

class ConnTransition {
  final ConnState from;
  final ConnState to;

  /// True only when the entry was live before, dropped, and is live again.
  /// The first connect after a page load is not a reconnect.
  final bool reconnected;

  /// The entry was live at some point since the page loaded. A server found
  /// down or a pairing found ended at first sight is a state, not news.
  final bool wasLive;

  const ConnTransition({
    required this.from,
    required this.to,
    required this.reconnected,
    required this.wasLive,
  });
}

class ConnectionManager {
  final IMetricsClient client;
  final Duration pollInterval;
  final Duration maxBackoff;
  final IMetricsSocket? socket;

  /// Opens a metrics socket, or returns null when the socket path is not
  /// usable right now (a dual entry currently on the relay); the manager then
  /// polls at once, asks again as soon as that poll answers (the client has
  /// raced both paths by then), and else after the next retry delay.
  final IMetricsSocket? Function()? socketFactory;
  final Duration socketRetryDelay;

  /// Retry delays for the socket path in the [quickWindow] after a socket
  /// that had frames dropped (the last one repeats), so a direct path that
  /// comes back is used again within seconds; after that window every
  /// [socketRetryDelay].
  final List<Duration> reprobeSteps;
  DateTime? _droppedAt;
  int _reprobes = 0;
  bool _socketAfterPoll = false;

  /// Unauthenticated check that the address still serves the paired server.
  /// Called once on a 401; false means [ConnAccess.wrongServer].
  final Future<bool> Function()? identityCheck;

  /// A socket that sends nothing within this time is dropped for polling.
  final Duration firstFrameTimeout;

  /// While the server has been away for less than [quickWindow] (a
  /// restart), it is asked again every [quickRetry] instead of backing off,
  /// so the page is live as soon as the server is back.
  final Duration quickRetry;
  final Duration quickWindow;
  DateTime? _awaySince;

  ConnState _state = ConnState.connecting;
  ConnAccess _access = ConnAccess.granted;
  bool _running = false;
  bool _disposed = false;
  bool _usedInitialSocket = false;
  bool _everLive = false;
  int? _pollingGeneration;
  int _generation = 0;
  Timer? _pollTimer;
  Timer? _reconnectTimer;
  Timer? _firstFrameTimer;
  ServerSnapshot? _latest;
  ServerSnapshot? get latestSnapshot => _latest;
  int _failures = 0;
  bool _wsHadFrames = false;
  bool _pollAgainAtOnce = false;

  IMetricsSocket? _activeSocket;

  StreamSubscription<ServerSnapshot>? _socketSub;
  StreamSubscription<bool>? _presenceSub;

  final StreamController<ServerSnapshot> _controller =
      StreamController<ServerSnapshot>.broadcast();
  final StreamController<ConnState> _stateController =
      StreamController<ConnState>.broadcast();
  final StreamController<ConnTransition> _transitions =
      StreamController<ConnTransition>.broadcast();
  final Map<String, RingBuffer> _rings = <String, RingBuffer>{};
  final RingBuffer _times = RingBuffer(128);

  ConnectionManager(
    this.client, {
    this.pollInterval = const Duration(seconds: 2),
    this.maxBackoff = const Duration(seconds: 30),
    this.socket,
    this.socketFactory,
    this.identityCheck,
    this.firstFrameTimeout = const Duration(seconds: 5),
    this.socketRetryDelay = const Duration(seconds: 15),
    this.reprobeSteps = const <Duration>[
      Duration(seconds: 1),
      Duration(seconds: 2),
      Duration(seconds: 5),
    ],
    this.quickRetry = const Duration(milliseconds: 250),
    this.quickWindow = const Duration(minutes: 1),
  });

  Stream<ServerSnapshot> get snapshots => _controller.stream;

  /// State changes. Also fires (with [ConnState.offline]) when [access]
  /// changes, so listeners re-read it.
  Stream<ConnState> get stateChanges => _stateController.stream;

  Stream<ConnTransition> get transitions => _transitions.stream;

  ConnState get state => _state;

  ConnAccess get access => _access;

  ServerRevisions? get revisions => _latest?.revisions;

  List<double> samplerHistory(String id) {
    final RingBuffer? ring = _rings[id];
    return ring == null ? <double>[] : ring.toList();
  }

  void start() {
    if (_running || _disposed || _access == ConnAccess.revoked) return;
    _running = true;
    _generation++;
    _setState(ConnState.connecting);
    _failures = 0;
    _listenPresence();

    final IMetricsSocket? initial = !_usedInitialSocket && socket != null
        ? socket
        : socketFactory?.call();
    _usedInitialSocket = true;
    if (initial != null) {
      _attachSocket(initial);
    } else {
      _startPolling();
      if (socketFactory != null) _scheduleWsReconnect(socketRetryDelay);
    }
  }

  /// The pairing ended before this page opened (an entry kept for a tab
  /// that closed, or paired before links): no request is ever sent.
  void endPairing() => _setAccess(ConnAccess.revoked);

  void stop() {
    _running = false;
    _generation++;
    _socketAfterPoll = false;
    _pollTimer?.cancel();
    _pollTimer = null;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _presenceSub?.cancel();
    _presenceSub = null;
    _closeSocket();
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    stop();
    _controller.close();
    _stateController.close();
    _transitions.close();
    _rings.clear();
    _latest = null;
  }

  void _listenPresence() {
    final IMetricsClient source = client;
    if (source is! IServerPresence) return;
    _presenceSub?.cancel();
    final int generation = _generation;
    _presenceSub = (source as IServerPresence).serverOnline.listen((
      bool online,
    ) {
      if (!_running || generation != _generation) return;
      _onPresence(online);
    });
  }

  /// The relay saw the server's agent come or go. Coming back skips the
  /// remaining backoff; going away is checked at once.
  void _onPresence(bool online) {
    if (_socketSub != null) return;
    if (online) _failures = 0;
    if (_pollingGeneration == _generation) {
      _pollAgainAtOnce = true;
      return;
    }
    _pollTimer?.cancel();
    _pollTimer = null;
    _startPolling();
  }

  void _attachSocket(IMetricsSocket next) {
    _closeSocket();
    _activeSocket = next;
    _wsHadFrames = false;
    final int generation = _generation;
    _firstFrameTimer = Timer(firstFrameTimeout, () {
      _firstFrameTimer = null;
      if (generation == _generation &&
          identical(_activeSocket, next) &&
          !_wsHadFrames) {
        _onWsDone();
      }
    });
    _socketSub = next.frames.listen(
      (ServerSnapshot snapshot) {
        if (generation == _generation && identical(_activeSocket, next)) {
          _onWsFrame(snapshot);
        }
      },
      onError: (Object error, StackTrace stack) {
        if (generation == _generation && identical(_activeSocket, next)) {
          _onWsError(error);
        }
      },
      onDone: () {
        if (generation == _generation && identical(_activeSocket, next)) {
          _onWsDone();
        }
      },
    );
  }

  void _closeSocket() {
    _firstFrameTimer?.cancel();
    _firstFrameTimer = null;
    _socketSub?.cancel();
    _socketSub = null;
    _activeSocket?.close();
    _activeSocket = null;
  }

  void _onWsFrame(ServerSnapshot snapshot) {
    if (!_running) return;
    _wsHadFrames = true;
    _droppedAt = null;
    _reprobes = 0;
    _firstFrameTimer?.cancel();
    _firstFrameTimer = null;
    _onSuccess(snapshot);
  }

  void _onWsError(Object error) {
    if (error is ReactRevoked) {
      _setAccess(ConnAccess.revoked);
      return;
    }
    _onWsDone();
  }

  /// The socket closed. Polling takes over at once and only a failed poll
  /// counts, so a dropped path that the other path covers (direct socket
  /// gone, relay answering) shows no gap.
  void _onWsDone() {
    if (!_running) return;
    if (_wsHadFrames) {
      _droppedAt = DateTime.now();
      _reprobes = 0;
    }
    _closeSocket();
    _startPolling();
    _scheduleWsReconnect();
  }

  void _scheduleWsReconnect([Duration? after]) {
    if (socketFactory == null || _reconnectTimer != null) return;
    final int generation = _generation;
    final Duration delay =
        after ?? (_wsHadFrames ? _backoffDuration() : pollInterval);
    _reconnectTimer = Timer(delay, () {
      _reconnectTimer = null;
      if (!_running || generation != _generation) return;
      final IMetricsSocket? next = socketFactory!();
      if (next == null) {
        _socketAfterPoll = true;
        _startPolling();
        _scheduleWsReconnect(_socketRetry());
        return;
      }
      _attachSocket(next);
    });
  }

  /// The wait before the socket path is tried again: short in the first
  /// [quickWindow] after a drop, [socketRetryDelay] otherwise.
  Duration _socketRetry() {
    final DateTime? dropped = _droppedAt;
    if (dropped == null ||
        reprobeSteps.isEmpty ||
        DateTime.now().difference(dropped) >= quickWindow) {
      return socketRetryDelay;
    }
    return reprobeSteps[min(_reprobes++, reprobeSteps.length - 1)];
  }

  /// After a poll that answered while the socket path was refused: the
  /// client has raced both paths for it, so ask for the socket again now.
  void _socketAfterAnswer() {
    if (!_socketAfterPoll || socketFactory == null || _socketSub != null) {
      return;
    }
    _socketAfterPoll = false;
    final IMetricsSocket? next = socketFactory!();
    if (next == null) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _attachSocket(next);
  }

  void _startPolling() {
    _pollTimer?.cancel();
    unawaited(_poll(_generation));
  }

  Future<void> _poll(int generation) async {
    if (!_running ||
        generation != _generation ||
        _socketSub != null ||
        _pollingGeneration == generation) {
      return;
    }
    _pollingGeneration = generation;
    Duration delay = pollInterval;
    try {
      final ServerSnapshot raw = await client.metrics();
      if (!_running || generation != _generation || _socketSub != null) return;
      _onSuccess(raw);
      _socketAfterAnswer();
    } on ReactAuthException catch (error) {
      if (!_running || generation != _generation) return;
      await _onAuthFailure(error);
      if (!_running) return;
      delay = maxBackoff;
    } on Exception {
      if (!_running || generation != _generation || _socketSub != null) return;
      _onFailure();
      delay = _backoffDuration();
    } finally {
      if (_pollingGeneration == generation) _pollingGeneration = null;
    }
    if (_pollAgainAtOnce) {
      _pollAgainAtOnce = false;
      delay = Duration.zero;
    }
    if (_running && generation == _generation && _socketSub == null) {
      _pollTimer = Timer(delay, () => unawaited(_poll(generation)));
    }
  }

  /// A 401 means the token was revoked, unless the address now serves a
  /// different server (its port moved, or another server took it).
  Future<void> _onAuthFailure(ReactAuthException error) async {
    if (error is ReactWrongServer) {
      _setAccess(ConnAccess.wrongServer);
      return;
    }
    if (error is! ReactRevoked && identityCheck != null) {
      bool same = true;
      try {
        same = await identityCheck!();
      } on Object {
        same = true;
      }
      if (!same) {
        _setAccess(ConnAccess.wrongServer);
        return;
      }
    }
    _setAccess(ConnAccess.revoked);
  }

  void _setAccess(ConnAccess access) {
    final bool changed = _access != access;
    _access = access;
    if (access == ConnAccess.revoked) stop();
    if (access == ConnAccess.wrongServer) _closeSocket();
    final ConnState prev = _state;
    _state = ConnState.offline;
    if ((changed || prev != _state) && !_stateController.isClosed) {
      _stateController.add(_state);
    }
    if (prev != _state) _emitTransition(prev, _state);
  }

  void _setState(ConnState next) {
    final ConnState prev = _state;
    _state = next;
    if (prev == next) return;
    if (!_stateController.isClosed) _stateController.add(next);
    _emitTransition(prev, next);
  }

  void _emitTransition(ConnState from, ConnState to) {
    final bool wasLive = _everLive;
    final bool reconnected =
        to == ConnState.live && _everLive && from != ConnState.connecting;
    if (to == ConnState.live) _everLive = true;
    if (!_transitions.isClosed) {
      _transitions.add(
        ConnTransition(
          from: from,
          to: to,
          reconnected: reconnected,
          wasLive: wasLive,
        ),
      );
    }
  }

  void _onSuccess(ServerSnapshot raw) {
    _failures = 0;
    _awaySince = null;
    if (_access == ConnAccess.wrongServer) _access = ConnAccess.granted;
    _setState(ConnState.live);
    final ServerSnapshot? previous = _latest;
    final bool repeat = previous?.seq == raw.seq && previous?.at == raw.at;
    if (repeat &&
        (raw.revisions == null ||
            ServerRevisions.same(previous?.revisions, raw.revisions))) {
      return;
    }
    _rings.removeWhere((String id, RingBuffer _) => !raw.byId.containsKey(id));
    if (!repeat) _times.add(raw.at.millisecondsSinceEpoch.toDouble());
    final Map<String, SamplerSample> samples = <String, SamplerSample>{};
    for (final MapEntry<String, SamplerSample> entry in raw.byId.entries) {
      final RingBuffer ring = _rings.putIfAbsent(
        entry.key,
        () => RingBuffer(128),
      );
      if (entry.value.available && !repeat) {
        ring.add(entry.value.value);
      }
      samples[entry.key] = entry.value.withLiveHistory(
        ring.snapshot(),
        minimumValue: ring.minimum,
        maximumValue: ring.maximum,
      );
    }
    final ServerSnapshot stamped = ServerSnapshot(
      byId: samples,
      at: raw.at,
      seq: raw.seq,
      revisions: raw.revisions ?? previous?.revisions,
      times: _times.snapshot(),
    );
    _latest = stamped;
    if (!_controller.isClosed) {
      _controller.add(stamped);
    }
  }

  void _onFailure() {
    _awaySince ??= DateTime.now();
    _failures++;
    _setState(_failures == 1 ? ConnState.degraded : ConnState.offline);
  }

  Duration _backoffDuration() {
    if (_failures <= 0) return Duration.zero;
    final int ms = pollInterval.inMilliseconds;
    if (ms == 0) return Duration.zero;
    final int backoffMs = (ms * (1 << (_failures - 1).clamp(0, 20))).clamp(
      0,
      maxBackoff.inMilliseconds,
    );
    final DateTime? away = _awaySince;
    final int quickMs =
        away != null && DateTime.now().difference(away) < quickWindow
        ? quickRetry.inMilliseconds
        : backoffMs;
    return Duration(
      milliseconds: min(min(backoffMs, quickMs), maxBackoff.inMilliseconds),
    );
  }
}
