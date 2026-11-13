part of 'fleet.dart';

/// One console line with its server-wide sequence number.
final class _LogLine {
  final int seq;
  final String line;

  const _LogLine(this.seq, this.line);

  Map<String, Object?> toJson() => <String, Object?>{'seq': seq, 'line': line};
}

/// An authenticated `/ws/logs` socket. Lines wait in a bounded FIFO and go
/// out as `{"type":"logs","lines":[{seq, line}]}` batches when the socket
/// asked for `?batch=1`, else one `{"type":"log","line","seq"}` frame per
/// line. A full FIFO drops its oldest lines; the next frame says how many
/// with `"dropped"`. A positive [idleTimeout] closes the socket with 1001
/// once that long passes without a data frame, as Jetty's idle timeout does
/// for a plugin that does not ping.
final class _LogSession {
  static const int capacity = 500;
  static const int backlogFrameLines = 100;
  static const Duration flushDelay = Duration(milliseconds: 50);

  final WebSocket socket;
  final String tokenId;
  final Duration idleTimeout;
  final bool batch;
  final ListQueue<_LogLine> _pending = ListQueue<_LogLine>();
  int _dropped = 0;
  Timer? _flush;
  Timer? _idle;

  _LogSession(
    this.socket,
    this.tokenId,
    this.idleTimeout, {
    required this.batch,
  });

  void sendBacklog(List<_LogLine> lines) {
    for (int start = 0; start < lines.length; start += backlogFrameLines) {
      _send(
        lines.sublist(start, math.min(lines.length, start + backlogFrameLines)),
        0,
      );
    }
    if (lines.isEmpty) _armIdle();
  }

  void enqueue(_LogLine line) {
    _pending.add(line);
    while (_pending.length > capacity) {
      _pending.removeFirst();
      _dropped++;
    }
    _flush ??= Timer(flushDelay, _flushPending);
  }

  void _flushPending() {
    _flush = null;
    if (_pending.isEmpty) return;
    final List<_LogLine> lines = List<_LogLine>.of(_pending);
    final int dropped = _dropped;
    _pending.clear();
    _dropped = 0;
    _send(lines, dropped);
  }

  void _send(List<_LogLine> lines, int dropped) {
    if (socket.readyState != WebSocket.open) return;
    if (batch) {
      socket.add(
        jsonEncode(<String, Object?>{
          'type': 'logs',
          'lines': <Map<String, Object?>>[
            for (final _LogLine line in lines) line.toJson(),
          ],
          if (dropped > 0) 'dropped': dropped,
        }),
      );
    } else {
      for (final (int index, _LogLine line) in lines.indexed) {
        socket.add(
          jsonEncode(<String, Object?>{
            'type': 'log',
            'line': line.line,
            'seq': line.seq,
            if (index == 0 && dropped > 0) 'dropped': dropped,
          }),
        );
      }
    }
    _armIdle();
  }

  void _armIdle() {
    _idle?.cancel();
    if (idleTimeout <= Duration.zero) return;
    _idle = Timer(
      idleTimeout,
      () => unawaited(
        close(WebSocketStatus.goingAway, 'Connection Idle Timeout'),
      ),
    );
  }

  void stop() {
    _flush?.cancel();
    _flush = null;
    _idle?.cancel();
    _idle = null;
  }

  Future<void> close(int code, String reason) {
    stop();
    return socket.close(code, reason);
  }
}

extension _ServerSockets on _FakeServer {
  /// Closes the open log and/or metrics sockets, only those of [tokenId]
  /// when given, and returns how many it closed.
  int closeSockets({
    required bool logs,
    required bool metrics,
    required int code,
    required String reason,
    String? tokenId,
  }) {
    int closed = 0;
    if (metrics) {
      for (final MapEntry<WebSocket, String> entry
          in List<MapEntry<WebSocket, String>>.of(metricsSockets.entries)) {
        if (tokenId != null && entry.value != tokenId) continue;
        metricsSockets.remove(entry.key);
        unawaited(entry.key.close(code, reason));
        closed++;
      }
    }
    if (logs) {
      for (final _LogSession session in List<_LogSession>.of(logSessions)) {
        if (tokenId != null && session.tokenId != tokenId) continue;
        logSessions.remove(session);
        unawaited(session.close(code, reason));
        closed++;
      }
    }
    return closed;
  }

  void broadcastMetrics(DateTime now, {bool advance = true}) {
    if (metricsSockets.isEmpty) return;
    final String frame = jsonEncode(compactBody(now, advance: advance));
    for (final WebSocket socket in List<WebSocket>.of(metricsSockets.keys)) {
      if (socket.readyState == WebSocket.open) {
        socket.add(frame);
      } else {
        metricsSockets.remove(socket);
      }
    }
  }
}

extension _FleetSockets on FakeFleet {
  /// The plugin's `WsAuthenticationGate`: the first frame must be
  /// `{"type":"auth","token":"<bearer>"}` within 2 seconds, from a token
  /// with the scope the socket needs. An ended pairing closes it with 4401
  /// and its reason; anything else with 1008.
  Future<void> _handleSocket(
    HttpRequest request,
    _FakeServer state,
    String name,
  ) async {
    final String? scope = switch (name) {
      'metrics' => 'read',
      'logs' => 'console:read',
      _ => null,
    };
    if (scope == null) {
      throw const _Reject(HttpStatus.notFound, 'Socket not found');
    }
    if (!WebSocketTransformer.isUpgradeRequest(request)) {
      throw const _Reject(HttpStatus.badRequest, 'WebSocket upgrade required');
    }
    final bool batch = request.uri.queryParameters['batch'] == '1';
    final WebSocket socket = await WebSocketTransformer.upgrade(request);
    bool authenticated = false;
    _LogSession? session;
    final Timer timeout = Timer(const Duration(seconds: 2), () {
      if (!authenticated) {
        unawaited(
          socket.close(
            WebSocketStatus.policyViolation,
            'Authentication timed out',
          ),
        );
      }
    });
    void forget() {
      timeout.cancel();
      state.metricsSockets.remove(socket);
      final _LogSession? current = session;
      if (current != null) {
        current.stop();
        state.logSessions.remove(current);
      }
    }

    socket.listen(
      (Object? frame) {
        if (authenticated) return;
        final FakeToken? token = _socketToken(frame, state);
        timeout.cancel();
        final String? ended = token?.ended;
        if (ended != null) {
          unawaited(socket.close(4401, ended));
          return;
        }
        if (token == null) {
          unawaited(
            socket.close(WebSocketStatus.policyViolation, 'unauthorized'),
          );
          return;
        }
        if (!token.scopes.contains(scope)) {
          unawaited(
            socket.close(WebSocketStatus.policyViolation, 'scope_missing'),
          );
          return;
        }
        authenticated = true;
        socket.pingInterval = const Duration(seconds: 15);
        if (name == 'metrics') {
          state.metricsSockets[socket] = token.id;
          socket.add(jsonEncode(state.compactBody(DateTime.now())));
          return;
        }
        final _LogSession opened = _LogSession(
          socket,
          token.id,
          idleTimeout,
          batch: batch,
        );
        opened.sendBacklog(
          state.logs.sublist(math.max(0, state.logs.length - 200)),
        );
        state.logSessions.add(opened);
        session = opened;
      },
      onError: (Object _) => forget(),
      onDone: forget,
      cancelOnError: true,
    );
  }

  static FakeToken? _socketToken(Object? frame, _FakeServer state) {
    if (frame is! String) return null;
    try {
      final Object? decoded = jsonDecode(frame);
      if (decoded is! Map<String, dynamic> ||
          decoded.length != 2 ||
          decoded['type'] != 'auth') {
        return null;
      }
      final Object? bearer = decoded['token'];
      return bearer is String ? state.verify(bearer) : null;
    } on FormatException {
      return null;
    }
  }

  void _broadcastMetrics() {
    final DateTime now = DateTime.now();
    for (final _FakeServer state in _servers.values) {
      state.broadcastMetrics(now);
    }
  }
}
