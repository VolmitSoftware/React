library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/log_line.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/service/react_log_socket.dart';

ServerCredential _fakeCred() => const ServerCredential(
  id: 'test',
  label: 'Test',
  host: 'localhost',
  port: 8080,
  bearer: 'tok',
);

void main() {
  group('createLogSocket (stub path under dart test)', () {
    test('returns an ILogSocket', () {
      final ILogSocket socket = createLogSocket(_fakeCred());
      expect(socket, isA<ILogSocket>());
    });

    test('batches is a Stream<LogBatch>', () {
      final ILogSocket socket = createLogSocket(_fakeCred());
      expect(socket.batches, isA<Stream<LogBatch>>());
    });

    test('close() completes normally', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      await expectLater(socket.close(), completes);
    });

    test('batches stream does not emit errors', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      bool errored = false;
      final StreamSubscription<LogBatch> sub = socket.batches.listen(
        (_) {},
        onError: (_) => errored = true,
      );
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      expect(errored, isFalse);
    });

    test('close() after batches subscription completes normally', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      final StreamSubscription<LogBatch> sub = socket.batches.listen((_) {});
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      await expectLater(socket.close(), completes);
    });
  });
}
