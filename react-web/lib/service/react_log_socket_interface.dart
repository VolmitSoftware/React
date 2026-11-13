library;

import '../model/log_line.dart';

abstract interface class ILogSocket {
  /// Batches in server order. Ends when the socket closes; a revoked token
  /// surfaces as a `ReactRevoked` error first.
  Stream<LogBatch> get batches;
  Future<void> close();
}
