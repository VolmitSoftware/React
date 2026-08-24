library;

import 'dart:async';
import 'dart:convert';
import 'dart:js_interop';

import 'package:web/web.dart' as web;

import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import 'direct_web_socket_handshake.dart';
import 'react_socket_interface.dart';

class _WebMetricsSocket implements IMetricsSocket {
  final StreamController<ServerSnapshot> _controller =
      StreamController<ServerSnapshot>.broadcast();
  late final web.WebSocket _ws;
  bool _closed = false;

  _WebMetricsSocket(ServerCredential cred) {
    final DirectWebSocketHandshake handshake = DirectWebSocketHandshake(
      cred,
      'ws/metrics',
    );
    _ws = web.WebSocket(handshake.endpoint.toString());

    _ws.addEventListener(
      'open',
      ((web.Event _) {
        if (_closed) return;
        _ws.send(handshake.authFrame.toJS);
      }).toJS,
    );

    _ws.addEventListener(
      'message',
      ((web.MessageEvent e) {
        if (_controller.isClosed) return;
        final Object? raw = e.data.dartify();
        if (raw is! String) return;
        try {
          final Map<String, dynamic> json =
              jsonDecode(raw) as Map<String, dynamic>;
          _controller.add(ServerSnapshot.fromJson(json));
        } on Object {
          return;
        }
      }).toJS,
    );

    _ws.addEventListener(
      'error',
      ((web.Event _) {
        if (_controller.isClosed) return;
        _controller.addError(Exception('WebSocket error on /ws/metrics'));
        if (!_controller.isClosed) {
          _controller.close();
        }
      }).toJS,
    );

    _ws.addEventListener(
      'close',
      ((web.Event _) {
        if (!_controller.isClosed) {
          _controller.close();
        }
      }).toJS,
    );
  }

  @override
  Stream<ServerSnapshot> get frames => _controller.stream;

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _ws.close();
    if (!_controller.isClosed) {
      await _controller.close();
    }
  }
}

IMetricsSocket createMetricsSocket(ServerCredential cred) =>
    _WebMetricsSocket(cred);
