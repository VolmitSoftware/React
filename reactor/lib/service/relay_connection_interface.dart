library;

import 'dart:async';

import '../model/relay_frame.dart';

abstract interface class IRelayConnection {
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  });
  Future<void> close();
}

typedef RelayConnectionFactory = IRelayConnection Function(
    String relayUrl, String serverId);

class RelayRpcMux {
  final String _serverId;
  final void Function(RelayFrame) _sendFrame;
  final Map<String, Completer<RelayResponse>> _pending =
      <String, Completer<RelayResponse>>{};
  int _nextId = 0;

  RelayRpcMux({
    required String serverId,
    required void Function(RelayFrame) sendFrame,
  })  : _serverId = serverId,
        _sendFrame = sendFrame;

  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) {
    final String requestId = (_nextId++).toString();
    final Completer<RelayResponse> completer = Completer<RelayResponse>();
    _pending[requestId] = completer;
    _sendFrame(RelayFrame(
      type: RelayFrameType.route,
      serverId: _serverId,
      requestId: requestId,
      payload: buildRoutePayload(
          method: method, path: path, headers: headers, body: body),
    ));
    Future<void>.delayed(const Duration(seconds: 5)).then((_) {
      if (_pending.containsKey(requestId)) {
        _pending.remove(requestId);
        if (!completer.isCompleted) {
          completer.complete(RelayResponse(
            status: 504,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'relay request timeout'}
            },
          ));
        }
      }
    });
    return completer.future;
  }

  void onFrame(RelayFrame frame) {
    if (frame.type == RelayFrameType.data) {
      final String? requestId = frame.requestId;
      if (requestId == null) return;
      final Completer<RelayResponse>? completer = _pending.remove(requestId);
      if (completer != null && !completer.isCompleted) {
        completer.complete(RelayResponse.fromDataPayload(frame.payload!));
      }
    } else if (frame.type == RelayFrameType.error) {
      final String? requestId = frame.requestId;
      if (requestId != null) {
        final Completer<RelayResponse>? completer = _pending.remove(requestId);
        if (completer != null && !completer.isCompleted) {
          final String msg =
              (frame.payload?['message'] as String?) ?? 'relay error';
          completer.complete(RelayResponse(
            status: 502,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': msg}
            },
          ));
        }
      } else {
        failAll(RelayResponse(
          status: 503,
          body: <String, dynamic>{
            'error': <String, dynamic>{'message': 'server offline'}
          },
        ));
      }
    }
  }

  void failAll(RelayResponse response) {
    final List<Completer<RelayResponse>> completers =
        List<Completer<RelayResponse>>.from(_pending.values);
    _pending.clear();
    for (final Completer<RelayResponse> c in completers) {
      if (!c.isCompleted) {
        c.complete(response);
      }
    }
  }
}
