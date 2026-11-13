library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/log_line.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/react_log_socket.dart';
import 'package:react_web/state/log_controller.dart';

import 'support/fake_react_client.dart';

Future<void> settle([int ms = 30]) =>
    Future<void>.delayed(Duration(milliseconds: ms));

class _Socket implements ILogSocket {
  final StreamController<LogBatch> controller =
      StreamController<LogBatch>.broadcast();
  bool closed = false;

  @override
  Stream<LogBatch> get batches => controller.stream;

  void send(List<int> seqs, {int dropped = 0}) => controller.add(
    LogBatch(<LogLine>[
      for (final int seq in seqs) LogLine('line $seq', seq: seq),
    ], dropped: dropped),
  );

  @override
  Future<void> close() async {
    closed = true;
    if (!controller.isClosed) await controller.close();
  }
}

List<LogLine> lines(Iterable<int> seqs) => <LogLine>[
  for (final int seq in seqs) LogLine('line $seq', seq: seq),
];

void main() {
  group('LogController reconnect', () {
    test('a closed socket reconnects and fills the gap by seq', () async {
      final FakeReactClient client = FakeReactClient(logs: lines(<int>[1, 2]));
      final List<_Socket> sockets = <_Socket>[];
      final LogController logs = LogController(
        client,
        socketFactory: () {
          final _Socket socket = _Socket();
          sockets.add(socket);
          return socket;
        },
        reconnectDelays: const <Duration>[Duration(milliseconds: 5)],
      );
      await logs.load();
      logs.start();
      sockets.single.send(<int>[3]);
      await settle();

      client.logBacklog = lines(<int>[1, 2, 3, 4, 5]);
      await sockets.single.controller.close();
      await settle(2);
      expect(logs.streamState, LogStreamState.reconnecting);
      await settle();

      expect(sockets, hasLength(2));
      expect(client.calls, contains('GET /logs?after=3'));
      sockets.last.send(<int>[5, 6]);
      await settle();
      expect(logs.lines, <String>[
        'line 1',
        'line 2',
        'line 3',
        'line 4',
        'line 5',
        'line 6',
      ]);
      expect(logs.streamState, LogStreamState.live);
      logs.dispose();
    });

    test('backoff grows 1 s, 2 s, then holds at 5 s', () {
      final LogController logs = LogController(FakeReactClient());
      expect(logs.reconnectDelays, const <Duration>[
        Duration(seconds: 1),
        Duration(seconds: 2),
        Duration(seconds: 5),
      ]);
      logs.dispose();
    });

    test('a dropped count fills the hole in order', () async {
      final FakeReactClient client = FakeReactClient(logs: lines(<int>[1]));
      final _Socket socket = _Socket();
      final LogController logs = LogController(client, socket: socket);
      await logs.load();
      logs.start();
      client.logBacklog = lines(<int>[1, 2, 3, 4]);
      socket.send(<int>[4], dropped: 2);
      await settle();
      expect(logs.lines, <String>['line 1', 'line 2', 'line 3', 'line 4']);
      expect(logs.droppedCount, 2);
      logs.dispose();
    });

    test('a server restart (seq starts over) keeps the new lines', () async {
      final FakeReactClient client = FakeReactClient(
        logs: lines(<int>[40, 41]),
      );
      final _Socket socket = _Socket();
      final LogController logs = LogController(client, socket: socket);
      await logs.load();
      logs.start();
      socket.send(<int>[1, 2]);
      await settle();
      expect(logs.lines, <String>['line 40', 'line 41', 'line 1', 'line 2']);
      logs.dispose();
    });

    test('4401 stops everything with the revoked state', () async {
      final FakeReactClient client = FakeReactClient();
      int opened = 0;
      _Socket? last;
      final LogController logs = LogController(
        client,
        socketFactory: () {
          opened++;
          return last = _Socket();
        },
        reconnectDelays: const <Duration>[Duration(milliseconds: 1)],
      );
      logs.start();
      last!.controller.addError(const ReactRevoked());
      await last!.controller.close();
      await settle();
      expect(logs.streamState, LogStreamState.revoked);
      expect(opened, 1);
      logs.dispose();
    });

    test('a single socket that closes falls back to polling', () async {
      final FakeReactClient client = FakeReactClient(logs: lines(<int>[1]));
      final _Socket socket = _Socket();
      final LogController logs = LogController(
        client,
        socket: socket,
        pollInterval: const Duration(milliseconds: 5),
      );
      await logs.load();
      logs.start();
      client.logBacklog = lines(<int>[1, 2]);
      await socket.controller.close();
      await settle();
      expect(logs.streamState, LogStreamState.polling);
      expect(logs.lines, <String>['line 1', 'line 2']);
      logs.dispose();
    });
  });

  group('LogController without seq (old plugin)', () {
    test('polling appends only lines after the last one shown', () async {
      final FakeReactClient client = FakeReactClient(
        logs: <LogLine>[const LogLine('a'), const LogLine('b')],
      );
      final LogController logs = LogController(
        client,
        pollInterval: const Duration(milliseconds: 5),
      );
      await logs.load();
      logs.start();
      client.logBacklog = <LogLine>[
        const LogLine('a'),
        const LogLine('b'),
        const LogLine('c'),
      ];
      await settle();
      expect(logs.lines, <String>['a', 'b', 'c']);
      logs.clear();
      await settle();
      expect(logs.lines, isEmpty, reason: 'cleared lines do not come back');
      logs.dispose();
    });
  });

  group('LogController autoSuspend', () {
    test('first listener loads and opens; last one leaving closes', () async {
      final FakeReactClient client = FakeReactClient(logs: lines(<int>[1]));
      final List<_Socket> sockets = <_Socket>[];
      final LogController logs = LogController(
        client,
        socketFactory: () {
          final _Socket socket = _Socket();
          sockets.add(socket);
          return socket;
        },
        autoSuspend: true,
        suspendAfter: const Duration(milliseconds: 5),
      );
      void listener() {}
      logs.addListener(listener);
      await settle();
      expect(sockets, hasLength(1));
      expect(logs.lines, <String>['line 1']);

      logs.removeListener(listener);
      await settle();
      expect(sockets.single.closed, isTrue);

      logs.addListener(listener);
      await settle();
      expect(sockets, hasLength(2));
      expect(client.calls.last, 'GET /logs?after=1');
      logs.dispose();
    });
  });
}
