import 'dart:convert';

import '../model/server_credential.dart';

final class DirectWebSocketHandshake {
  final Uri endpoint;
  final String authFrame;

  DirectWebSocketHandshake(ServerCredential credential, String path)
    : endpoint = credential.directWebSocketEndpoint(path),
      authFrame = jsonEncode(<String, String>{
        'type': 'auth',
        'token': credential.bearer,
      }) {
    if (endpoint.hasQuery) {
      throw ArgumentError.value(path, 'path', 'Socket endpoint has a query');
    }
  }
}
