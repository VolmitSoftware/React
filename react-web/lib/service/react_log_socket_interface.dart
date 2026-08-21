library;

import '../model/server_credential.dart';

abstract interface class ILogSocket {
  Stream<String> get lines;
  Future<void> close();
}

typedef LogSocketFactory = ILogSocket Function(ServerCredential cred);
