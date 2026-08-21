library;

import 'dart:async';

import '../model/server_credential.dart';
import 'react_log_socket_interface.dart';

class _StubLogSocket implements ILogSocket {
  final StreamController<String> _controller =
      StreamController<String>.broadcast();

  @override
  Stream<String> get lines => _controller.stream;

  @override
  Future<void> close() async {}
}

ILogSocket createLogSocket(ServerCredential cred) => _StubLogSocket();
