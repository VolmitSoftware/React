library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_socket.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

ServerCredential _fakeCred() => const ServerCredential(
  id: 'test',
  label: 'Test',
  host: 'localhost',
  port: 8080,
  bearer: 'tok',
);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('createMetricsSocket (stub path under dart test)', () {
    test('returns an IMetricsSocket', () {
      final IMetricsSocket socket = createMetricsSocket(_fakeCred());
      expect(socket, isA<IMetricsSocket>());
    });

    test('frames is a Stream<ServerSnapshot>', () {
      final IMetricsSocket socket = createMetricsSocket(_fakeCred());
      expect(socket.frames, isA<Stream<ServerSnapshot>>());
    });

    test('close() completes normally', () async {
      final IMetricsSocket socket = createMetricsSocket(_fakeCred());
      await expectLater(socket.close(), completes);
    });

    test('frames stream does not emit errors', () async {
      final IMetricsSocket socket = createMetricsSocket(_fakeCred());
      bool errored = false;
      final StreamSubscription<ServerSnapshot> sub = socket.frames.listen(
        (_) {},
        onError: (_) => errored = true,
      );
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      expect(errored, isFalse);
    });

    test('close() after frames subscription completes normally', () async {
      final IMetricsSocket socket = createMetricsSocket(_fakeCred());
      // Listen (may or may not complete immediately on stub)
      final StreamSubscription<ServerSnapshot> sub = socket.frames.listen(
        (_) {},
      );
      await Future<void>.delayed(Duration.zero);
      await sub.cancel();
      await expectLater(socket.close(), completes);
    });
  });
}
