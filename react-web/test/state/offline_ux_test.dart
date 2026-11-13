library;

import 'dart:async';

import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';

// ignore_for_file: avoid_redundant_argument_values

// ---------------------------------------------------------------------------
// Fake client
// ---------------------------------------------------------------------------

class _FakeMetricsClient implements IMetricsClient {
  final List<Object> _responses;
  int _index = 0;

  _FakeMetricsClient(this._responses);

  @override
  Future<ServerSnapshot> metrics() async {
    final Object resp = _index < _responses.length
        ? _responses[_index++]
        : _responses.last;
    if (resp is Future) return await (resp as Future<ServerSnapshot>);
    if (resp is ServerSnapshot) return resp;
    throw resp as Exception;
  }
}

ServerSnapshot _makeSnapshot() => ServerSnapshot(
  byId: <String, SamplerSample>{
    'cpu': SamplerSample(
      id: 'cpu',
      name: 'CPU',
      suffix: '%',
      value: 50.0,
      display: '50.0',
      min: 0.0,
      max: 100.0,
      history: <double>[],
    ),
  },
  at: DateTime.now(),
  seq: 0,
);

Future<void> pump([int count = 20]) async {
  for (int i = 0; i < count; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  // -------------------------------------------------------------------------
  // ConnectionManager → offline/recovery logic
  // -------------------------------------------------------------------------
  group('ConnectionManager offline transitions', () {
    test('reaches offline state after two consecutive failures', () async {
      final Completer<ServerSnapshot> blocker = Completer<ServerSnapshot>();
      addTearDown(() {
        if (!blocker.isCompleted) blocker.complete(_makeSnapshot());
      });
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          blocker.future,
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(60);
      expect(manager.state, equals(ConnState.offline));
      manager.stop();
    });

    test('recovers to live after successful poll following offline', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(80);
      expect(manager.state, equals(ConnState.live));
      manager.stop();
    });

    test(
      'stateChanges emits degraded then offline on consecutive failures',
      () async {
        final List<ConnState> emitted = <ConnState>[];
        final Completer<ServerSnapshot> blocker = Completer<ServerSnapshot>();
        addTearDown(() {
          if (!blocker.isCompleted) blocker.complete(_makeSnapshot());
        });
        final ConnectionManager manager = ConnectionManager(
          _FakeMetricsClient(<Object>[
            const ReactUnavailable('fail1'),
            const ReactUnavailable('fail2'),
            blocker.future,
          ]),
          pollInterval: Duration.zero,
        );
        final StreamSubscription<ConnState> sub = manager.stateChanges.listen(
          emitted.add,
        );
        manager.start();
        await pump(80);
        manager.stop();
        await sub.cancel();
        expect(
          emitted,
          containsAllInOrder(<ConnState>[
            ConnState.degraded,
            ConnState.offline,
          ]),
        );
      },
    );

    test('stateChanges emits live on recovery from offline', () async {
      final List<ConnState> emitted = <ConnState>[];
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      final StreamSubscription<ConnState> sub = manager.stateChanges.listen(
        emitted.add,
      );
      manager.start();
      await pump(100);
      manager.stop();
      await sub.cancel();
      expect(emitted.last, equals(ConnState.live));
    });
  });
}
