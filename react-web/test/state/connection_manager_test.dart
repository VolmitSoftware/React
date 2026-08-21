library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';

// ---------------------------------------------------------------------------
// Fake client
// ---------------------------------------------------------------------------

/// Responses may be:
///   - [ServerSnapshot]  → returned as-is
///   - [Exception]       → thrown
///   - [Future<ServerSnapshot>] → awaited (use a Completer to block indefinitely)
class _FakeMetricsClient implements IMetricsClient {
  final List<Object> _responses;
  int _index = 0;
  int callCount = 0;

  _FakeMetricsClient(this._responses);

  @override
  Future<ServerSnapshot> metrics() async {
    callCount++;
    final Object resp = _index < _responses.length
        ? _responses[_index++]
        : _responses.last;
    if (resp is Future) return await (resp as Future<ServerSnapshot>);
    if (resp is ServerSnapshot) return resp;
    throw resp as Exception;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

ServerSnapshot _makeSnapshot({int seq = 0, double cpuValue = 50.0}) =>
    ServerSnapshot(
      byId: <String, SamplerSample>{
        'cpu': SamplerSample(
          id: 'cpu',
          name: 'CPU',
          suffix: '%',
          value: cpuValue,
          display: cpuValue.toString(),
          min: 0.0,
          max: 100.0,
          history: <double>[],
        ),
      },
      at: DateTime.now(),
      seq: seq,
    );

/// Pump the Dart event queue a number of times so background futures resolve.
Future<void> pump([int count = 20]) async {
  for (int i = 0; i < count; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('ConnectionManager — initial state', () {
    test('starts in connecting state before start() is called', () {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: Duration.zero,
      );
      expect(manager.state, equals(ConnState.connecting));
    });
  });

  group('ConnectionManager — success path', () {
    test('transitions connecting → live on first successful poll', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump();
      expect(manager.state, equals(ConnState.live));
    });

    test('emits snapshot to stream on success', () async {
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(cpuValue: 42.0),
      ]);
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
      );

      ServerSnapshot? received;
      manager.snapshots.listen((ServerSnapshot s) => received = s);

      manager.start();
      await pump();

      expect(received, isNotNull);
      expect(received!.byId.containsKey('cpu'), isTrue);
      expect(received!.byId['cpu']!.value, equals(42.0));
    });

    test('emitted snapshot has incrementing seq', () async {
      final List<Object> responses = <Object>[
        _makeSnapshot(cpuValue: 1.0),
        _makeSnapshot(cpuValue: 2.0),
      ];
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(responses),
        pollInterval: Duration.zero,
      );

      final List<int> seqs = <int>[];
      manager.snapshots.listen((ServerSnapshot s) => seqs.add(s.seq));

      manager.start();
      await pump(40);

      expect(seqs.length, greaterThanOrEqualTo(2));
      expect(seqs[0], lessThan(seqs[1]));
    });

    test('seeds ring buffer per sampler (capacity 128)', () async {
      final List<Object> responses = List<Object>.generate(
        5,
        (int i) => _makeSnapshot(cpuValue: i.toDouble()),
      );

      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(responses),
        pollInterval: Duration.zero,
      );

      manager.start();
      await pump(60);

      final List<double> history = manager.samplerHistory('cpu');
      expect(history.length, greaterThanOrEqualTo(5));
    });

    test('ring buffer has max capacity of 128', () async {
      final List<Object> responses = List<Object>.generate(
        130,
        (int i) => _makeSnapshot(cpuValue: i.toDouble()),
      );

      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(responses),
        pollInterval: Duration.zero,
      );

      manager.start();
      await pump(300);

      expect(manager.samplerHistory('cpu').length, lessThanOrEqualTo(128));
    });
  });

  group('ConnectionManager — error path', () {
    test('transitions to degraded on first ReactUnavailable', () async {
      // Block the second call so the manager stays in degraded long enough to observe.
      final Completer<ServerSnapshot> secondCallBlocker =
          Completer<ServerSnapshot>();
      addTearDown(() {
        if (!secondCallBlocker.isCompleted) {
          secondCallBlocker.complete(_makeSnapshot());
        }
      });
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('unreachable'),
          secondCallBlocker.future, // blocks so state remains degraded
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(5);
      expect(manager.state, equals(ConnState.degraded));
      manager.stop();
    });

    test('transitions from degraded to offline on continued failure', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('unreachable'),
          const ReactUnavailable('unreachable'),
          const ReactUnavailable('unreachable'),
          const ReactUnavailable('unreachable'),
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(60);
      expect(manager.state, equals(ConnState.offline));
    });

    test('does not throw from poll errors — stream remains open', () async {
      bool streamErrored = false;
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('unreachable'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );

      manager.snapshots.listen((_) {}, onError: (_) => streamErrored = true);

      manager.start();
      await pump(30);

      expect(streamErrored, isFalse);
    });
  });

  group('ConnectionManager — recovery', () {
    test('recovers from degraded back to live after success', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('unreachable'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(40);
      expect(manager.state, equals(ConnState.live));
    });

    test('recovers from offline back to live after success', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          const ReactUnavailable('fail3'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(80);
      expect(manager.state, equals(ConnState.live));
    });
  });

  group('ConnectionManager — stop()', () {
    test('stop() halts the poll loop', () async {
      final _FakeMetricsClient client = _FakeMetricsClient(
        List<Object>.generate(1000, (_) => _makeSnapshot()),
      );
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
      );

      manager.start();
      await pump(5);
      final int countAtStop = client.callCount;
      manager.stop();
      await pump(10);
      // At most a couple of in-flight calls may finish, but no significant polling
      expect(client.callCount, lessThanOrEqualTo(countAtStop + 2));
    });

    test('stop() before start() does not throw', () {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: Duration.zero,
      );
      expect(() => manager.stop(), returnsNormally);
    });
  });

  group('ConnectionManager — dispose()', () {
    test('dispose() closes the snapshots and stateChanges streams', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump();

      bool snapshotsDone = false;
      bool stateDone = false;
      manager.snapshots.listen((_) {}, onDone: () => snapshotsDone = true);
      manager.stateChanges.listen((_) {}, onDone: () => stateDone = true);

      manager.dispose();
      await pump();

      expect(snapshotsDone, isTrue);
      expect(stateDone, isTrue);
    });

    test('dispose() halts the poll loop', () async {
      final _FakeMetricsClient client = _FakeMetricsClient(
        List<Object>.generate(1000, (_) => _makeSnapshot()),
      );
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
      );

      manager.start();
      await pump(5);
      final int countAtDispose = client.callCount;
      manager.dispose();
      await pump(10);
      expect(client.callCount, lessThanOrEqualTo(countAtDispose + 2));
    });

    test('dispose() before start() does not throw', () {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: Duration.zero,
      );
      expect(() => manager.dispose(), returnsNormally);
    });
  });
}
