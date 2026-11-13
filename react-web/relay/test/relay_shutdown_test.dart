@TestOn('vm')
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:test/test.dart';
import 'package:web_socket_channel/io.dart';

/// Starts bin/relay.dart on a free port and waits for its listening line.
Future<(Process, int)> _startRelay() async {
  final ServerSocket probe = await ServerSocket.bind(
    InternetAddress.loopbackIPv4,
    0,
  );
  final int port = probe.port;
  await probe.close();
  final Process process = await Process.start(
    Platform.resolvedExecutable,
    <String>['run', 'bin/relay.dart'],
    environment: <String, String>{'PORT': '$port', 'BIND_ADDRESS': '127.0.0.1'},
  );
  final Completer<void> listening = Completer<void>();
  process.stdout.transform(utf8.decoder).listen((String text) {
    if (text.contains('listening') && !listening.isCompleted) {
      listening.complete();
    }
  });
  process.stderr.drain<void>();
  await listening.future.timeout(const Duration(seconds: 60));
  return (process, port);
}

void main() {
  test('SIGTERM closes open sockets with 4012 and ends the process', () async {
    final (Process process, int port) = await _startRelay();
    addTearDown(() => process.kill(ProcessSignal.sigkill));
    final IOWebSocketChannel app = IOWebSocketChannel.connect(
      Uri.parse('ws://127.0.0.1:$port/app'),
    );
    await app.ready;
    final Completer<int?> closed = Completer<int?>();
    app.stream.listen(
      (Object? _) {},
      onDone: () {
        if (!closed.isCompleted) closed.complete(app.closeCode);
      },
      onError: (Object _) {
        if (!closed.isCompleted) closed.complete(null);
      },
    );

    process.kill(ProcessSignal.sigterm);

    final int? code = await closed.future.timeout(
      const Duration(seconds: 5),
      onTimeout: () => fail('the open socket stayed up after SIGTERM'),
    );
    expect(code, 4012, reason: 'sockets learn the relay is restarting');
    await process.exitCode.timeout(
      const Duration(seconds: 5),
      onTimeout: () => fail('the relay kept running after SIGTERM'),
    );
  }, timeout: const Timeout(Duration(seconds: 90)));
}
