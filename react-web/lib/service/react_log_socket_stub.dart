library;

import 'dart:async';

import '../model/log_line.dart';
import '../model/server_credential.dart';
import 'react_log_socket_interface.dart';

class _StubLogSocket implements ILogSocket {
  final StreamController<LogBatch> _controller =
      StreamController<LogBatch>.broadcast();

  @override
  Stream<LogBatch> get batches => _controller.stream;

  @override
  Future<void> close() async {}
}

ILogSocket createLogSocket(ServerCredential cred) => _StubLogSocket();
