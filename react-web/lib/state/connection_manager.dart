import 'dart:async' show StreamController, StreamSubscription, Timer, unawaited;
import 'dart:math' show min;

import '../model/ring_buffer.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_socket.dart';

enum ConnState { connecting, live, degraded, offline }

class ConnectionManager {
  final IMetricsClient client;
  final Duration pollInterval;
  final Duration maxBackoff;
  final IMetricsSocket? socket;

  final IMetricsSocket Function()? socketFactory;

  ConnState _state = ConnState.connecting;
  bool _running = false;
  bool _disposed = false;
  bool _usedInitialSocket = false;
  int? _pollingGeneration;
  int _generation = 0;
  Timer? _pollTimer;
  Timer? _reconnectTimer;
  ServerSnapshot? _latest;
  ServerSnapshot? get latestSnapshot => _latest;
  int _failures = 0;
  bool _wsHadFrames = false;

  IMetricsSocket? _activeSocket;

  StreamSubscription<ServerSnapshot>? _socketSub;

  final StreamController<ServerSnapshot> _controller =
      StreamController<ServerSnapshot>.broadcast();
  final StreamController<ConnState> _stateController =
      StreamController<ConnState>.broadcast();
  final Map<String, RingBuffer> _rings = <String, RingBuffer>{};

  ConnectionManager(
    this.client, {
    this.pollInterval = const Duration(seconds: 2),
    this.maxBackoff = const Duration(seconds: 30),
    this.socket,
    this.socketFactory,
  });

  Stream<ServerSnapshot> get snapshots => _controller.stream;

  Stream<ConnState> get stateChanges => _stateController.stream;

  ConnState get state => _state;

  List<double> samplerHistory(String id) {
    final RingBuffer? ring = _rings[id];
    return ring == null ? <double>[] : ring.toList();
  }

  void start() {
    if (_running || _disposed) return;
    _running = true;
    _generation++;
    _state = ConnState.connecting;
    _failures = 0;

    final IMetricsSocket? initial = !_usedInitialSocket && socket != null
        ? socket
        : socketFactory?.call();
    _usedInitialSocket = true;
    if (initial != null) {
      _attachSocket(initial);
    } else {
      _startPolling();
    }
  }

  void stop() {
    _running = false;
    _generation++;
    _pollTimer?.cancel();
    _pollTimer = null;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _closeSocket();
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    stop();
    _controller.close();
    _stateController.close();
    _rings.clear();
    _latest = null;
  }

  void _attachSocket(IMetricsSocket next) {
    _closeSocket();
    _activeSocket = next;
    _wsHadFrames = false;
    final int generation = _generation;
    _socketSub = next.frames.listen(
      (ServerSnapshot snapshot) {
        if (generation == _generation && identical(_activeSocket, next)) {
          _onWsFrame(snapshot);
        }
      },
      onError: (Object error, StackTrace stack) {
        if (generation == _generation && identical(_activeSocket, next)) {
          _onWsError(error, stack);
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
    _socketSub?.cancel();
    _socketSub = null;
    _activeSocket?.close();
    _activeSocket = null;
  }

  void _onWsFrame(ServerSnapshot snapshot) {
    if (!_running) return;
    _wsHadFrames = true;
    _onSuccess(snapshot);
  }

  void _onWsError(Object error, StackTrace stack) => _onWsDone();

  void _onWsDone() {
    if (!_running) return;
    _closeSocket();
    if (_wsHadFrames) _onFailure();
    _startPolling();
    _scheduleWsReconnect();
  }

  void _scheduleWsReconnect() {
    if (socketFactory == null || _reconnectTimer != null) return;
    final int generation = _generation;
    final Duration delay = _wsHadFrames ? _backoffDuration() : pollInterval;
    _reconnectTimer = Timer(delay, () {
      _reconnectTimer = null;
      if (!_running || generation != _generation) return;
      _attachSocket(socketFactory!());
    });
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
    } on Exception {
      if (!_running || generation != _generation || _socketSub != null) return;
      _onFailure();
      delay = _backoffDuration();
    } finally {
      if (_pollingGeneration == generation) _pollingGeneration = null;
    }
    if (_running && generation == _generation && _socketSub == null) {
      _pollTimer = Timer(delay, () => unawaited(_poll(generation)));
    }
  }

  void _onSuccess(ServerSnapshot raw) {
    _failures = 0;
    final ConnState prev = _state;
    _state = ConnState.live;
    if (prev != _state && !_stateController.isClosed) {
      _stateController.add(_state);
    }
    if (_latest?.seq == raw.seq && _latest?.at == raw.at) return;
    _rings.removeWhere((String id, RingBuffer _) => !raw.byId.containsKey(id));
    final Map<String, SamplerSample> samples = <String, SamplerSample>{};
    for (final MapEntry<String, SamplerSample> entry in raw.byId.entries) {
      final RingBuffer ring = _rings.putIfAbsent(
        entry.key,
        () => RingBuffer(128),
      );
      if (entry.value.available) {
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
    );
    _latest = stamped;
    if (!_controller.isClosed) {
      _controller.add(stamped);
    }
  }

  void _onFailure() {
    _failures++;
    final ConnState prev = _state;
    if (_failures == 1) {
      _state = ConnState.degraded;
    } else {
      _state = ConnState.offline;
    }
    if (prev != _state && !_stateController.isClosed) {
      _stateController.add(_state);
    }
  }

  Duration _backoffDuration() {
    if (_failures <= 0) return Duration.zero;
    final int ms = pollInterval.inMilliseconds;
    if (ms == 0) return Duration.zero;
    final int backoffMs = (ms * (1 << (_failures - 1).clamp(0, 20))).clamp(
      0,
      maxBackoff.inMilliseconds,
    );
    return Duration(milliseconds: min(backoffMs, maxBackoff.inMilliseconds));
  }
}
