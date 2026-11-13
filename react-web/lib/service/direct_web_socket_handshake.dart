import 'dart:convert';

import '../model/server_credential.dart';

/// Endpoint and first frame of a direct socket. The token travels only in the
/// auth frame, never in the URL; [query] carries protocol options such as
/// `batch=1`.
final class DirectWebSocketHandshake {
  final Uri endpoint;
  final String authFrame;

  DirectWebSocketHandshake(
    ServerCredential credential,
    String path, {
    Map<String, String> query = const <String, String>{},
  }) : endpoint = _endpoint(credential, path, query),
       authFrame = jsonEncode(<String, String>{
         'type': 'auth',
         'token': credential.bearer,
       });

  static Uri _endpoint(
    ServerCredential credential,
    String path,
    Map<String, String> query,
  ) {
    if (Uri.parse(path).hasQuery) {
      throw ArgumentError.value(path, 'path', 'Socket endpoint has a query');
    }
    final Uri base = credential.directWebSocketEndpoint(path);
    if (query.values.contains(credential.bearer)) {
      throw ArgumentError.value(query, 'query', 'Token must not be in the URL');
    }
    return query.isEmpty ? base : base.replace(queryParameters: query);
  }
}
