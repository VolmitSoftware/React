library;

import '../model/identity_info.dart';
import '../model/relay_frame.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import 'monotonic_counter.dart';
import 'react_client.dart';
import 'react_exceptions.dart';
import 'relay_connection.dart';

class RelayReactClient implements IReactClient {
  final IRelayConnection _connection;
  final ServerCredential _cred;

  RelayReactClient(
    IRelayConnection connection,
    ServerCredential cred, {
    MonotonicCounter? counter,
  })  : _connection = connection,
        _cred = cred;

  Map<String, String> get _getHeaders => <String, String>{
        'Authorization': 'Bearer ${_cred.bearer}',
      };

  Future<RelayResponse> _get(String path) async {
    final RelayResponse response = await _connection.request(
      method: 'GET',
      path: '/api/v1$path',
      headers: _getHeaders,
    );
    switch (response.status) {
      case 200:
        return response;
      case 401:
        throw const ReactAuthException();
      case 403:
        throw ReactForbidden(_errorMessage(response.body));
      case 404:
        throw ReactNotFound(_errorMessage(response.body));
      case 409:
        throw ReactConflict(_errorMessage(response.body));
      default:
        throw ReactUnavailable(_errorMessage(response.body));
    }
  }

  String _errorMessage(Map<String, dynamic> body) {
    final Object? error = body['error'];
    if (error is Map<String, dynamic>) {
      final Object? message = error['message'];
      if (message is String) {
        return message;
      }
    }
    return 'Request failed';
  }

  Map<String, dynamic> _decodeData(Map<String, dynamic> body) {
    final Object? data = body['data'];
    if (data is! Map<String, dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data object');
    }
    return data;
  }

  @override
  Future<IdentityInfo> identity() async {
    final RelayResponse response = await _get('/identity');
    return IdentityInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<ServerSnapshot> metrics() async {
    final RelayResponse response = await _get('/metrics');
    return ServerSnapshot.fromJson(response.body);
  }
}
