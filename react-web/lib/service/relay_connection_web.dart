library;

import 'dart:async';
import 'dart:js_interop';

import 'package:web/web.dart' as web;

import '../model/relay_frame.dart';
import 'relay_connection_interface.dart';

class _WebRelayConnection implements IRelayConnection, IServerPresence {
  late final RelayRpcMux _mux;
  final String _url;
  final String _serverId;
  final List<RelayFrame> _outbound = <RelayFrame>[];
  final StreamController<bool> _presence = StreamController<bool>.broadcast();
  web.WebSocket? _ws;
  Timer? _reconnectTimer;
  bool _closed = false;
  int _reconnectAttempt = 0;

  /// Tries left at a short interval after a relay restart (about 6 s).
  int _quickRetries = 0;

  _WebRelayConnection(String relayUrl, String serverId)
    : _url = _appUrl(relayUrl),
      _serverId = serverId {
    _mux = RelayRpcMux(serverId: serverId, sendFrame: _sendFrame);
    _connect();
  }

  static String _appUrl(String relayUrl) {
    final Uri base = Uri.parse(relayUrl);
    final String basePath = base.path.endsWith('/')
        ? base.path.substring(0, base.path.length - 1)
        : base.path;
    return base.replace(path: '$basePath/app').toString();
  }

  void _connect() {
    if (_closed) return;
    final web.WebSocket socket = web.WebSocket(_url);
    _ws = socket;

    socket.addEventListener(
      'open',
      ((web.Event _) {
        if (_closed || _ws != socket) return;
        _reconnectAttempt = 0;
        _quickRetries = 0;
        socket.send(
          RelayFrame(
            type: RelayFrameType.subscribe,
            serverId: _serverId,
          ).encode().toJS,
        );
        for (final RelayFrame frame in _outbound) {
          if (_mux.isPending(frame.requestId)) socket.send(frame.encode().toJS);
        }
        _outbound.clear();
      }).toJS,
    );

    socket.addEventListener(
      'message',
      ((web.MessageEvent e) {
        if (_closed || _ws != socket) return;
        final Object? raw = e.data.dartify();
        if (raw is! String) return;
        final RelayFrame? frame = RelayFrame.decode(raw);
        if (frame == null) return;
        final bool? online = frame.online;
        if (frame.type == RelayFrameType.status && online != null) {
          if (!_presence.isClosed) _presence.add(online);
          return;
        }
        if (frame.type == RelayFrameType.error &&
            frame.requestId == null &&
            !_presence.isClosed) {
          _presence.add(false);
        }
        _mux.onFrame(frame);
      }).toJS,
    );

    socket.addEventListener(
      'error',
      ((web.Event _) {
        if (_closed || _ws != socket) return;
        _mux.failAll(
          RelayResponse(
            status: 503,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'WebSocket error on /app'},
            },
          ),
        );
        socket.close();
      }).toJS,
    );

    socket.addEventListener(
      'close',
      ((web.CloseEvent event) {
        if (_closed || _ws != socket) return;
        // A relay restarting says so: come back as soon as it listens.
        if (event.code == relayRestartCloseCode) {
          _reconnectAttempt = 0;
          _quickRetries = 40;
        }
        _ws = null;
        _outbound.clear();
        _mux.failAll(
          RelayResponse(
            status: 503,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'relay connection closed'},
            },
          ),
        );
        _scheduleReconnect();
      }).toJS,
    );
  }

  @override
  Stream<bool> get serverOnline => _presence.stream;

  void _sendFrame(RelayFrame frame) {
    final web.WebSocket? socket = _ws;
    if (socket != null && socket.readyState == web.WebSocket.OPEN) {
      socket.send(frame.encode().toJS);
      return;
    }
    _outbound.removeWhere(
      (RelayFrame queued) => !_mux.isPending(queued.requestId),
    );
    if (!_closed && _outbound.length < 64) {
      _outbound.add(frame);
    }
  }

  void _scheduleReconnect() {
    if (_closed || _reconnectTimer != null) return;
    final Duration delay;
    if (_quickRetries > 0) {
      _quickRetries--;
      delay = const Duration(milliseconds: 150);
    } else {
      final int exponent = _reconnectAttempt.clamp(0, 4);
      delay = Duration(milliseconds: 500 * (1 << exponent));
      _reconnectAttempt++;
    }
    _reconnectTimer = Timer(delay, () {
      _reconnectTimer = null;
      _connect();
    });
  }

  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) {
    if (_closed) {
      return Future<RelayResponse>.value(
        RelayResponse(
          status: 503,
          body: <String, dynamic>{
            'error': <String, dynamic>{'message': 'relay connection closed'},
          },
        ),
      );
    }
    return _mux.request(
      method: method,
      path: path,
      headers: headers,
      body: body,
    );
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _outbound.clear();
    _mux.failAll(
      RelayResponse(
        status: 503,
        body: <String, dynamic>{
          'error': <String, dynamic>{'message': 'relay connection closed'},
        },
      ),
    );
    final web.WebSocket? socket = _ws;
    _ws = null;
    socket?.close();
    await _presence.close();
  }
}

IRelayConnection createRelayConnection(String relayUrl, String serverId) =>
    _WebRelayConnection(relayUrl, serverId);
