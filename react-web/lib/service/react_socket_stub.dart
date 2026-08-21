library;

import 'dart:async';

import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import 'react_socket_interface.dart';

class _StubMetricsSocket implements IMetricsSocket {
  final StreamController<ServerSnapshot> _controller =
      StreamController<ServerSnapshot>.broadcast();

  @override
  Stream<ServerSnapshot> get frames => _controller.stream;

  @override
  Future<void> close() async {}
}

IMetricsSocket createMetricsSocket(ServerCredential cred) =>
    _StubMetricsSocket();
