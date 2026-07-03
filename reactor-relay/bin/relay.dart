import 'dart:io';

import 'package:reactor_relay/reactor_relay.dart';

Future<void> main(List<String> args) async {
  final int port = int.tryParse(Platform.environment['PORT'] ?? '') ?? 8787;
  final HttpServer server = await startRelayServer(port: port);
  print('reactor-relay listening on ${server.address.host}:${server.port}');
}
