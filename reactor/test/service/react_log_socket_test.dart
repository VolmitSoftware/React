library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:reactor/model/server_credential.dart';
import 'package:reactor/service/react_log_socket.dart';

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

    test('lines is a Stream<String>', () {
      final ILogSocket socket = createLogSocket(_fakeCred());
      expect(socket.lines, isA<Stream<String>>());
    });

    test('close() completes normally', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      await expectLater(socket.close(), completes);
    });

    test('lines stream does not emit errors', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      bool errored = false;
      final StreamSubscription<String> sub = socket.lines.listen(
        (_) {},
        onError: (_) => errored = true,
      );
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      expect(errored, isFalse);
    });

    test('close() after lines subscription completes normally', () async {
      final ILogSocket socket = createLogSocket(_fakeCred());
      final StreamSubscription<String> sub = socket.lines.listen((_) {});
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      await expectLater(socket.close(), completes);
    });
  });
}
