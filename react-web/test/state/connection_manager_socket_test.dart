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

    test('closing WS stream AFTER prior frames transitions to degraded then '
        'recovers live via HTTP polling', () async {
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

      // _wsHadFrames was true → _onFailure() called → degraded.
      expect(manager.state, equals(ConnState.degraded));

      firstPollBlocker.complete(_makeSnapshot(value: 88.0));
      await pump(40);
      expect(manager.state, equals(ConnState.live));
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
