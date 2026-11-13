library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/react_socket.dart';
import 'package:react_web/state/connection_manager.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class _FakeMetricsSocket implements IMetricsSocket {
  final StreamController<ServerSnapshot> _controller =
      StreamController<ServerSnapshot>.broadcast();

  @override
  Stream<ServerSnapshot> get frames => _controller.stream;

  @override
  Future<void> close() async {
    if (!_controller.isClosed) {
      await _controller.close();
    }
  }

  void emit(ServerSnapshot snapshot) {
    if (!_controller.isClosed) {
      _controller.add(snapshot);
    }
  }

  void emitError(Object error) {
    if (!_controller.isClosed) {
      _controller.addError(error);
    }
  }

  Future<void> closeStream() => close();
}

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

ServerSnapshot _makeSnapshot({
  String id = 'cpu',
  double value = 50.0,
  int seq = 1,
}) => ServerSnapshot(
  byId: <String, SamplerSample>{
    id: SamplerSample(
      id: id,
      name: id.toUpperCase(),
      suffix: '%',
      value: value,
      display: value.toString(),
      min: 0.0,
      max: 100.0,
      history: <double>[],
    ),
  },
  at: DateTime.now(),
  seq: seq,
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
  // (a) Transitions to live on first WS frame
  // -------------------------------------------------------------------------
  group('ConnectionManager with socket — live on first WS frame', () {
    test('transitions connecting -> live on first injected WS frame', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() async {
        manager.dispose();
        await socket.close();
      });

      manager.start();
      expect(manager.state, equals(ConnState.connecting));

      socket.emit(_makeSnapshot());
      await pump();

      expect(manager.state, equals(ConnState.live));
    });
  });

  // -------------------------------------------------------------------------
  // (b) Injected snapshot emitted with incrementing seq and seeded rings
  // -------------------------------------------------------------------------
  group('ConnectionManager with socket — snapshot delivery', () {
    test(
      'emitted WS frame appears on snapshots stream with incremented seq',
      () async {
        final _FakeMetricsSocket socket = _FakeMetricsSocket();
        final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
          _makeSnapshot(),
        ]);

        final ConnectionManager manager = ConnectionManager(
          client,
          pollInterval: Duration.zero,
          socket: socket,
        );

        addTearDown(() async {
          manager.dispose();
          await socket.close();
        });

        final List<ServerSnapshot> received = <ServerSnapshot>[];
        manager.snapshots.listen(received.add);

        manager.start();
        socket.emit(_makeSnapshot(value: 42.0));
        await pump();

        expect(received, isNotEmpty);
        expect(received.last.byId['cpu']!.value, equals(42.0));
        expect(received.last.seq, greaterThan(0));
      },
    );

    test('seq increments on successive WS frames', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      // A poll would never answer here: only the socket's frames count.
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        Completer<ServerSnapshot>().future,
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() async {
        manager.dispose();
        await socket.close();
      });

      final List<int> seqs = <int>[];
      manager.snapshots.listen((ServerSnapshot s) => seqs.add(s.seq));

      manager.start();
      socket.emit(_makeSnapshot(value: 1.0, seq: 1));
      await pump();
      socket.emit(_makeSnapshot(value: 2.0, seq: 2));
      await pump();

      expect(seqs.length, greaterThanOrEqualTo(2));
      expect(seqs[0], lessThan(seqs[1]));
    });

    test('ring buffer is seeded from WS frames', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() async {
        manager.dispose();
        await socket.close();
      });

      manager.start();
      for (int i = 0; i < 5; i++) {
        socket.emit(_makeSnapshot(value: i.toDouble()));
        await pump();
      }

      expect(manager.samplerHistory('cpu').length, greaterThanOrEqualTo(5));
    });
  });

  // -------------------------------------------------------------------------
  // (c) Fallback to HTTP polling when WS stream closes
  // -------------------------------------------------------------------------
  group('ConnectionManager with socket — HTTP fallback on WS close', () {
    test('closing WS stream with no prior frames stays in connecting and '
        'recovers live via HTTP polling (no degraded flash)', () async {
      // The Completer keeps the first HTTP poll pending long enough to observe
      // that state has NOT jumped to degraded.
      final Completer<ServerSnapshot> firstPollBlocker =
          Completer<ServerSnapshot>();
      addTearDown(() {
        if (!firstPollBlocker.isCompleted) {
          firstPollBlocker.complete(_makeSnapshot(value: 99.0));
        }
      });

      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        firstPollBlocker.future,
        _makeSnapshot(value: 99.0),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() => manager.dispose());

      manager.start();
      await pump();

      // Close WS stream without having emitted any frames.
      // _wsHadFrames = false → _onWsDone skips _onFailure() → no degraded flash.
      await socket.closeStream();
      await pump(10);

      // Poll loop started but blocked on firstPollBlocker; state must still be
      // connecting (not degraded).
      expect(manager.state, equals(ConnState.connecting));

      // Unblock the first HTTP call — manager recovers to live.
      firstPollBlocker.complete(_makeSnapshot(value: 99.0));
      await pump(40);
      expect(manager.state, equals(ConnState.live));
      expect(client.callCount, greaterThan(0));
    });

    test('a socket that drops after frames stays live while the fallback '
        'poll answers (a path switch shows nothing)', () async {
      final Completer<ServerSnapshot> firstPollBlocker =
          Completer<ServerSnapshot>();
      addTearDown(() {
        if (!firstPollBlocker.isCompleted) {
          firstPollBlocker.complete(_makeSnapshot(value: 88.0));
        }
      });

      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        firstPollBlocker.future,
        _makeSnapshot(value: 88.0),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() => manager.dispose());

      manager.start();

      // Emit a frame so _wsHadFrames = true, then close the stream.
      socket.emit(_makeSnapshot(value: 1.0));
      await pump();
      expect(manager.state, equals(ConnState.live));

      await socket.closeStream();
      await pump(10);

      // Only a failed poll counts: the drop alone changes nothing.
      expect(manager.state, equals(ConnState.live));

      firstPollBlocker.complete(_makeSnapshot(value: 88.0));
      await pump(40);
      expect(manager.state, equals(ConnState.live));
    });

    test('a socket that drops after frames degrades when the fallback poll '
        'fails too', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        const ReactUnavailable('down'),
        Completer<ServerSnapshot>().future,
      ]);
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(milliseconds: 20),
        socket: socket,
      );
      addTearDown(() => manager.dispose());
      manager.start();
      socket.emit(_makeSnapshot(value: 1.0));
      await pump();
      await socket.closeStream();
      await pump(10);
      expect(manager.state, equals(ConnState.degraded));
    });

    test(
      'WS error transitions to degraded and HTTP fallback recovers live',
      () async {
        final _FakeMetricsSocket socket = _FakeMetricsSocket();
        // Emit a frame first so _wsHadFrames = true, then error.
        final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
          _makeSnapshot(value: 77.0),
        ]);

        final ConnectionManager manager = ConnectionManager(
          client,
          pollInterval: Duration.zero,
          socket: socket,
        );

        addTearDown(() async {
          manager.dispose();
          await socket.close();
        });

        manager.start();
        socket.emit(_makeSnapshot(value: 1.0));
        await pump();
        expect(manager.state, equals(ConnState.live));

        socket.emitError(Exception('ws error'));
        await pump(40);

        expect(manager.state, equals(ConnState.live));
      },
    );

    test(
      'WS error with no prior frames stays in connecting and recovers live',
      () async {
        final _FakeMetricsSocket socket = _FakeMetricsSocket();
        final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
          _makeSnapshot(value: 55.0),
        ]);

        final ConnectionManager manager = ConnectionManager(
          client,
          pollInterval: Duration.zero,
          socket: socket,
        );

        addTearDown(() async {
          manager.dispose();
          await socket.close();
        });

        manager.start();
        socket.emitError(Exception('ws error before any frames'));
        await pump(40);

        expect(manager.state, equals(ConnState.live));
      },
    );
  });

  // -------------------------------------------------------------------------
  // WS reconnect via socketFactory
  // -------------------------------------------------------------------------
  group('ConnectionManager with socketFactory — reconnect', () {
    test('after WS drop, socketFactory is called to reconnect and live is '
        'restored from the new socket', () async {
      final _FakeMetricsSocket firstSocket = _FakeMetricsSocket();
      _FakeMetricsSocket? secondSocket;

      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: firstSocket,
        socketFactory: () {
          secondSocket = _FakeMetricsSocket();
          return secondSocket!;
        },
      );

      addTearDown(() async {
        manager.dispose();
        await firstSocket.close();
        await secondSocket?.close();
      });

      manager.start();

      // Deliver a frame on the first socket to make it live.
      firstSocket.emit(_makeSnapshot(value: 10.0));
      await pump();
      expect(manager.state, equals(ConnState.live));

      // Drop the first socket — schedules reconnect via socketFactory.
      await firstSocket.closeStream();
      await pump(40);

      // socketFactory must have been called to create a new socket.
      expect(secondSocket, isNotNull);

      // Deliver a frame on the reconnected socket — state recovers to live.
      secondSocket!.emit(_makeSnapshot(value: 20.0));
      await pump(20);
      expect(manager.state, equals(ConnState.live));
    });

    test('after a drop the direct path is tried again at 1, 2 and 5 s, '
        'then at the long interval once the first minute is over', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final List<DateTime> asked = <DateTime>[];
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: const Duration(seconds: 10),
        socket: socket,
        socketFactory: () {
          asked.add(DateTime.now());
          return null;
        },
        socketRetryDelay: const Duration(seconds: 10),
        reprobeSteps: const <Duration>[
          Duration(milliseconds: 20),
          Duration(milliseconds: 40),
          Duration(milliseconds: 100),
        ],
        quickWindow: const Duration(milliseconds: 400),
      );
      addTearDown(manager.dispose);
      manager.start();
      socket.emit(_makeSnapshot(value: 1.0));
      await pump();

      final DateTime dropped = DateTime.now();
      await socket.closeStream();
      await Future<void>.delayed(const Duration(milliseconds: 900));

      // Each try asks twice: on its timer, and once more right after the
      // poll it starts answers. Count the tries.
      final List<int> after = <int>[];
      for (final DateTime at in asked) {
        final int ms = at.difference(dropped).inMilliseconds;
        if (after.isEmpty || ms - after.last > 8) after.add(ms);
      }
      expect(after.length, inInclusiveRange(4, 8), reason: '$after');
      final List<int> gaps = <int>[
        for (int i = 1; i < after.length; i++) after[i] - after[i - 1],
      ];
      expect(gaps.first, lessThan(80), reason: '$after');
      expect(gaps.where((int gap) => gap > 200), isEmpty, reason: '$after');
      expect(after.last, lessThan(560), reason: 'none past the window: $after');
    });

    test('a try that finds the relay still in use polls at once and opens '
        'the socket as soon as that poll settled on the direct path', () async {
      final _FakeMetricsSocket first = _FakeMetricsSocket();
      _FakeMetricsSocket? second;
      bool directBack = false;
      DateTime? opened;
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[_makeSnapshot()]),
        pollInterval: const Duration(seconds: 10),
        socket: first,
        socketFactory: () {
          if (!directBack) {
            // The client races both paths on its next request; the direct
            // path answers this time.
            directBack = true;
            return null;
          }
          opened ??= DateTime.now();
          return second = _FakeMetricsSocket();
        },
        socketRetryDelay: const Duration(seconds: 10),
        reprobeSteps: const <Duration>[
          Duration(milliseconds: 20),
          Duration(seconds: 5),
        ],
      );
      addTearDown(() async {
        manager.dispose();
        await second?.close();
      });
      manager.start();
      first.emit(_makeSnapshot(value: 1.0));
      await pump();

      final DateTime dropped = DateTime.now();
      await first.closeStream();
      await Future<void>.delayed(const Duration(milliseconds: 300));

      expect(second, isNotNull);
      expect(opened!.difference(dropped).inMilliseconds, lessThan(250));
    });

    test('stop() prevents reconnect from firing after socket drop', () async {
      final _FakeMetricsSocket firstSocket = _FakeMetricsSocket();
      int factoryCallCount = 0;

      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: firstSocket,
        socketFactory: () {
          factoryCallCount++;
          return _FakeMetricsSocket();
        },
      );

      addTearDown(() async {
        manager.dispose();
        await firstSocket.close();
      });

      manager.start();
      firstSocket.emit(_makeSnapshot(value: 1.0));
      await pump();

      // Drop socket, then stop immediately before reconnect fires.
      await firstSocket.closeStream();
      manager.stop();
      await pump(40);

      expect(factoryCallCount, equals(0));
    });
  });

  // -------------------------------------------------------------------------
  // (d) Regression: no socket = pure polling (unchanged behavior)
  // -------------------------------------------------------------------------
  group('ConnectionManager without socket — regression', () {
    test('no-socket constructor call compiles and polls normally', () async {
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(value: 55.0),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
      );

      addTearDown(manager.dispose);

      final List<ServerSnapshot> received = <ServerSnapshot>[];
      manager.snapshots.listen(received.add);

      manager.start();
      await pump();

      expect(manager.state, equals(ConnState.live));
      expect(received, isNotEmpty);
      expect(received.last.byId['cpu']!.value, equals(55.0));
    });

    test('no-socket: degraded + offline on HTTP failures', () async {
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        const ReactUnavailable('fail1'),
        const ReactUnavailable('fail2'),
        const ReactUnavailable('fail3'),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
      );

      addTearDown(manager.dispose);

      manager.start();
      await pump(60);

      expect(manager.state, equals(ConnState.offline));
    });
  });

  // -------------------------------------------------------------------------
  // Lifecycle: stop() and dispose() clean up socket
  // -------------------------------------------------------------------------
  group('ConnectionManager with socket — lifecycle', () {
    test('stop() cancels socket subscription', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      addTearDown(() async {
        manager.dispose();
        await socket.close();
      });

      manager.start();
      await pump();
      manager.stop();

      // Emitting after stop should not crash or change state
      socket.emit(_makeSnapshot());
      await pump();
    });

    test('dispose() cancels socket subscription and closes streams', () async {
      final _FakeMetricsSocket socket = _FakeMetricsSocket();
      final _FakeMetricsClient client = _FakeMetricsClient(<Object>[
        _makeSnapshot(),
      ]);

      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: Duration.zero,
        socket: socket,
      );

      bool snapshotsDone = false;
      manager.snapshots.listen((_) {}, onDone: () => snapshotsDone = true);

      manager.start();
      await pump();
      manager.dispose();
      await pump();

      expect(snapshotsDone, isTrue);
    });
  });
}
