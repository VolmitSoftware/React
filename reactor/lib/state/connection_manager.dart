import 'dart:async' show StreamController, StreamSubscription, unawaited;
import 'dart:math' show min;

import '../model/ring_buffer.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
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
  bool _pollActive = false;
  int _failures = 0;
  int _seq = 0;

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
    if (_running) return;
    _running = true;
    _state = ConnState.connecting;
    _failures = 0;

    if (socket != null) {
      _activeSocket = socket;
      _wsHadFrames = false;
      _socketSub = socket!.frames.listen(
        _onWsFrame,
        onError: _onWsError,
        onDone: _onWsDone,
      );
    } else {
      _startPolling();
    }
  }

  void stop() {
    _running = false;
    _socketSub?.cancel();
    _socketSub = null;
    _activeSocket?.close();
    _activeSocket = null;
  }

  void dispose() {
    _running = false;
    _socketSub?.cancel();
    _socketSub = null;
    _activeSocket?.close();
    _activeSocket = null;
    if (!_controller.isClosed) {
      _controller.close();
    }
    if (!_stateController.isClosed) {
      _stateController.close();
    }
  }

  void _onWsFrame(ServerSnapshot snapshot) {
    if (!_running) return;
    _wsHadFrames = true;
    _onSuccess(snapshot);
  }

  void _onWsError(Object error, StackTrace stack) {
    if (!_running) return;
    _socketSub?.cancel();
    _socketSub = null;
    if (_wsHadFrames) {
      _onFailure();
    }
    _startPolling();
    _scheduleWsReconnect();
  }

  void _onWsDone() {
    if (!_running) return;
    _socketSub?.cancel();
    _socketSub = null;
    if (_wsHadFrames) {
      _onFailure();
    }
    _startPolling();
    _scheduleWsReconnect();
  }

  void _scheduleWsReconnect() {
    if (socketFactory == null) return;
    final Duration delay = _wsHadFrames
        ? _backoffDuration()
        : pollInterval;
    unawaited(Future<void>.delayed(delay).then((_) {
      if (!_running) return;
      _wsHadFrames = false;
      final IMetricsSocket newSocket = socketFactory!();
      _activeSocket = newSocket;
      _socketSub?.cancel();
      _socketSub = newSocket.frames.listen(
        _onWsFrame,
        onError: _onWsError,
        onDone: _onWsDone,
      );
    }));
  }

  void _startPolling() {
    if (!_pollActive) {
      _pollActive = true;
      unawaited(_pollLoop());
    }
  }

  Future<void> _pollLoop() async {
    try {
      while (_running) {
        if (_socketSub != null) {
          return;
        }
        try {
          final ServerSnapshot raw = await client.metrics();
          if (!_running) break;
          if (_socketSub != null) break;
          _onSuccess(raw);
        } on ReactUnavailable {
          if (!_running) break;
          _onFailure();
          await Future<void>.delayed(_backoffDuration());
          continue;
        }
        await Future<void>.delayed(pollInterval);
      }
    } finally {
      _pollActive = false;
    }
  }

  void _onSuccess(ServerSnapshot raw) {
    _failures = 0;
    _seq++;
    for (final MapEntry<String, SamplerSample> entry in raw.byId.entries) {
      _rings
          .putIfAbsent(entry.key, () => RingBuffer(128))
          .add(entry.value.value);
    }
    final ServerSnapshot stamped = ServerSnapshot(
      byId: raw.byId,
      at: raw.at,
      seq: _seq,
    );
    final ConnState prev = _state;
    _state = ConnState.live;
    if (prev != _state && !_stateController.isClosed) {
      _stateController.add(_state);
    }
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
    final int backoffMs = (ms * (1 << (_failures - 1))).clamp(0, maxBackoff.inMilliseconds);
    return Duration(milliseconds: min(backoffMs, maxBackoff.inMilliseconds));
  }
}
