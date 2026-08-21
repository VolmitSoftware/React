library;

import '../model/server_credential.dart';
import '../model/server_snapshot.dart';

abstract interface class IMetricsSocket {
  Stream<ServerSnapshot> get frames;
  Future<void> close();
}

typedef MetricsSocketFactory = IMetricsSocket Function(ServerCredential cred);
