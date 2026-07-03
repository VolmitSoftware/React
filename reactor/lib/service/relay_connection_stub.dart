library;

import '../model/relay_frame.dart';
import 'relay_connection_interface.dart';

class _StubRelayConnection implements IRelayConnection {
  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) async {
    return RelayResponse(
      status: 503,
      body: <String, dynamic>{
        'error': <String, dynamic>{
          'message': 'relay unavailable (non-web)',
        }
      },
    );
  }

  @override
  Future<void> close() async {}
}

IRelayConnection createRelayConnection(String relayUrl, String serverId) =>
    _StubRelayConnection();
