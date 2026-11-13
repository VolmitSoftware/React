library;

import '../model/server_snapshot.dart';

abstract interface class IMetricsSocket {
  Stream<ServerSnapshot> get frames;
  Future<void> close();
}
