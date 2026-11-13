library;

import 'dart:async';
import 'dart:convert';
import 'dart:js_interop';

import 'package:web/web.dart' as web;

import '../model/log_line.dart';
import '../model/server_credential.dart';
import 'direct_web_socket_handshake.dart';
import 'react_log_socket_interface.dart';
import 'web_socket_close.dart';

class _WebLogSocket implements ILogSocket {
  final StreamController<LogBatch> _controller =
      StreamController<LogBatch>.broadcast();
  late final web.WebSocket _ws;
  bool _closed = false;

  _WebLogSocket(ServerCredential cred) {
    final DirectWebSocketHandshake handshake = DirectWebSocketHandshake(
      cred,
      'ws/logs',
      query: const <String, String>{'batch': '1'},
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
          final LogBatch? batch = LogBatch.fromFrame(json);
          if (batch != null) _controller.add(batch);
        } on Object {
          return;
        }
      }).toJS,
    );

    _ws.addEventListener(
      'error',
      ((web.Event _) {
        if (_controller.isClosed) return;
        _controller.addError(Exception('WebSocket error on /ws/logs'));
        if (!_controller.isClosed) {
          _controller.close();
        }
      }).toJS,
    );

    _ws.addEventListener(
      'close',
      ((web.CloseEvent event) {
        if (_controller.isClosed) return;
        final Exception? error = socketCloseError(event.code);
        if (error != null) _controller.addError(error);
        _controller.close();
      }).toJS,
    );
  }

  @override
  Stream<LogBatch> get batches => _controller.stream;

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

ILogSocket createLogSocket(ServerCredential cred) => _WebLogSocket(cred);
