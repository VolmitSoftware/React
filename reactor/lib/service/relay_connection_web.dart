library;

import 'dart:js_interop';

import 'package:web/web.dart' as web;

import '../model/relay_frame.dart';
import 'relay_connection_interface.dart';

class _WebRelayConnection implements IRelayConnection {
  late final RelayRpcMux _mux;
  late final web.WebSocket _ws;
  bool _closed = false;

  _WebRelayConnection(String relayUrl, String serverId) {
    final String url = '$relayUrl/app';
    _ws = web.WebSocket(url);
    _mux = RelayRpcMux(
      serverId: serverId,
      sendFrame: (RelayFrame f) {
        if (_ws.readyState == web.WebSocket.OPEN) {
          _ws.send(f.encode().toJS);
        }
      },
    );

    _ws.addEventListener(
      'open',
      ((web.Event _) {
        _ws.send(RelayFrame(
          type: RelayFrameType.subscribe,
          serverId: serverId,
        ).encode().toJS);
      }).toJS,
    );

    _ws.addEventListener(
      'message',
      ((web.MessageEvent e) {
        final Object? raw = e.data.dartify();
        if (raw is! String) return;
        final RelayFrame? frame = RelayFrame.decode(raw);
        if (frame == null) return;
        _mux.onFrame(frame);
      }).toJS,
    );

    _ws.addEventListener(
      'error',
      ((web.Event _) {
        _mux.failAll(RelayResponse(
          status: 503,
          body: <String, dynamic>{
            'error': <String, dynamic>{'message': 'WebSocket error on /app'},
          },
        ));
      }).toJS,
    );

    _ws.addEventListener(
      'close',
      ((web.Event _) {
        if (!_closed) {
          _mux.failAll(RelayResponse(
            status: 503,
            body: <String, dynamic>{
              'error': <String, dynamic>{
                'message': 'relay connection closed',
              },
            },
          ));
        }
      }).toJS,
    );
  }

  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) {
    return _mux.request(
        method: method, path: path, headers: headers, body: body);
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _mux.failAll(RelayResponse(
      status: 503,
      body: <String, dynamic>{
        'error': <String, dynamic>{'message': 'relay connection closed'},
      },
    ));
    _ws.close();
  }
}

IRelayConnection createRelayConnection(String relayUrl, String serverId) =>
    _WebRelayConnection(relayUrl, serverId);
