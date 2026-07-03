library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/fleet_live_model.dart';
import 'package:reactor/state/fleet_rollup.dart';

ServerSnapshot _makeSnapshot() => ServerSnapshot(
      byId: <String, SamplerSample>{
        'cpu': SamplerSample(
          id: 'cpu',
          name: 'CPU',
          suffix: '%',
          value: 42.0,
          display: '42.0',
          min: 0.0,
          max: 100.0,
          history: <double>[],
        ),
      },
      at: DateTime.now(),
    );

void main() {
  group('FleetLiveModel — initial state', () {
    test('servers has correct length and all entries have null snapshot and null lastSeen', () {
      final StreamController<ServerSnapshot> snapA =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateA =
          StreamController<ConnState>.broadcast();
      final StreamController<ServerSnapshot> snapB =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateB =
          StreamController<ConnState>.broadcast();

      final List<FleetLiveSource> sources = <FleetLiveSource>[
        FleetLiveSource(
          id: 'a',
          name: 'Server A',
          initialState: ConnState.connecting,
          snapshots: snapA.stream,
          stateChanges: stateA.stream,
        ),
        FleetLiveSource(
          id: 'b',
          name: 'Server B',
          initialState: ConnState.connecting,
          snapshots: snapB.stream,
          stateChanges: stateB.stream,
        ),
      ];

      final FleetLiveModel model = FleetLiveModel(sources);

      expect(model.servers.length, equals(2));
      expect(model.servers[0].snapshot, isNull);
      expect(model.servers[0].lastSeen, isNull);
      expect(model.servers[0].state, equals(ConnState.connecting));
      expect(model.servers[1].snapshot, isNull);
      expect(model.servers[1].lastSeen, isNull);
      expect(model.servers[1].state, equals(ConnState.connecting));

      model.dispose();
      snapA.close();
      snapB.close();
      stateA.close();
      stateB.close();
    });
  });

  group('FleetLiveModel — snapshot delivery', () {
    test('onChange fires and servers[A].snapshot/lastSeen update after snapshot arrives', () async {
      final StreamController<ServerSnapshot> snapA =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateA =
          StreamController<ConnState>.broadcast();
      final StreamController<ServerSnapshot> snapB =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateB =
          StreamController<ConnState>.broadcast();

      int notifyCount = 0;
      final FleetLiveModel model = FleetLiveModel(
        <FleetLiveSource>[
          FleetLiveSource(
            id: 'a',
            name: 'Server A',
            initialState: ConnState.connecting,
            snapshots: snapA.stream,
            stateChanges: stateA.stream,
          ),
          FleetLiveSource(
            id: 'b',
            name: 'Server B',
            initialState: ConnState.connecting,
            snapshots: snapB.stream,
            stateChanges: stateB.stream,
          ),
        ],
        onChange: () => notifyCount++,
      );

      snapA.add(_makeSnapshot());
      await Future<void>.delayed(Duration.zero);

      expect(notifyCount, greaterThanOrEqualTo(1));
      final FleetServerLive serverA =
          model.servers.firstWhere((FleetServerLive s) => s.id == 'a');
      expect(serverA.snapshot, isNotNull);
      expect(serverA.lastSeen, isNotNull);
      final FleetServerLive serverB =
          model.servers.firstWhere((FleetServerLive s) => s.id == 'b');
      expect(serverB.snapshot, isNull);
      expect(serverB.lastSeen, isNull);

      model.dispose();
      snapA.close();
      snapB.close();
      stateA.close();
      stateB.close();
    });

    test('stateChanges updates servers[A].state', () async {
      final StreamController<ServerSnapshot> snapA =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateA =
          StreamController<ConnState>.broadcast();
      final StreamController<ServerSnapshot> snapB =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateB =
          StreamController<ConnState>.broadcast();

      final FleetLiveModel model = FleetLiveModel(
        <FleetLiveSource>[
          FleetLiveSource(
            id: 'a',
            name: 'Server A',
            initialState: ConnState.connecting,
            snapshots: snapA.stream,
            stateChanges: stateA.stream,
          ),
          FleetLiveSource(
            id: 'b',
            name: 'Server B',
            initialState: ConnState.connecting,
            snapshots: snapB.stream,
            stateChanges: stateB.stream,
          ),
        ],
      );

      stateA.add(ConnState.live);
      await Future<void>.delayed(Duration.zero);

      final FleetServerLive serverA =
          model.servers.firstWhere((FleetServerLive s) => s.id == 'a');
      expect(serverA.state, equals(ConnState.live));

      model.dispose();
      snapA.close();
      snapB.close();
      stateA.close();
      stateB.close();
    });
  });

  group('FleetLiveModel — update()', () {
    test('update() removing a source reduces servers list and old stream events are ignored', () async {
      final StreamController<ServerSnapshot> snapA =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateA =
          StreamController<ConnState>.broadcast();
      final StreamController<ServerSnapshot> snapB =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateB =
          StreamController<ConnState>.broadcast();

      int notifyCount = 0;
      final FleetLiveModel model = FleetLiveModel(
        <FleetLiveSource>[
          FleetLiveSource(
            id: 'a',
            name: 'Server A',
            initialState: ConnState.connecting,
            snapshots: snapA.stream,
            stateChanges: stateA.stream,
          ),
          FleetLiveSource(
            id: 'b',
            name: 'Server B',
            initialState: ConnState.connecting,
            snapshots: snapB.stream,
            stateChanges: stateB.stream,
          ),
        ],
        onChange: () => notifyCount++,
      );

      model.update(<FleetLiveSource>[
        FleetLiveSource(
          id: 'b',
          name: 'Server B',
          initialState: ConnState.connecting,
          snapshots: snapB.stream,
          stateChanges: stateB.stream,
        ),
      ]);
      await Future<void>.delayed(Duration.zero);

      expect(model.servers.length, equals(1));
      expect(model.servers[0].id, equals('b'));

      final int countBeforeStaleEvent = notifyCount;
      snapA.add(_makeSnapshot());
      await Future<void>.delayed(Duration.zero);

      expect(notifyCount, equals(countBeforeStaleEvent));
      expect(model.servers.length, equals(1));

      model.dispose();
      snapA.close();
      snapB.close();
      stateA.close();
      stateB.close();
    });

    test('update() adding a new source preserves existing server snapshots', () async {
      final StreamController<ServerSnapshot> snapB =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateB =
          StreamController<ConnState>.broadcast();
      final StreamController<ServerSnapshot> snapC =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateC =
          StreamController<ConnState>.broadcast();

      final FleetLiveModel model = FleetLiveModel(
        <FleetLiveSource>[
          FleetLiveSource(
            id: 'b',
            name: 'Server B',
            initialState: ConnState.connecting,
            snapshots: snapB.stream,
            stateChanges: stateB.stream,
          ),
        ],
      );

      final ServerSnapshot bSnap = _makeSnapshot();
      snapB.add(bSnap);
      await Future<void>.delayed(Duration.zero);

      final FleetServerLive serverBBefore =
          model.servers.firstWhere((FleetServerLive s) => s.id == 'b');
      expect(serverBBefore.snapshot, isNotNull);

      model.update(<FleetLiveSource>[
        FleetLiveSource(
          id: 'b',
          name: 'Server B',
          initialState: ConnState.connecting,
          snapshots: snapB.stream,
          stateChanges: stateB.stream,
        ),
        FleetLiveSource(
          id: 'c',
          name: 'Server C',
          initialState: ConnState.connecting,
          snapshots: snapC.stream,
          stateChanges: stateC.stream,
        ),
      ]);
      await Future<void>.delayed(Duration.zero);

      expect(model.servers.length, equals(2));
      final FleetServerLive serverBAfter =
          model.servers.firstWhere((FleetServerLive s) => s.id == 'b');
      expect(serverBAfter.snapshot, isNotNull);

      model.dispose();
      snapB.close();
      snapC.close();
      stateB.close();
      stateC.close();
    });
  });

  group('FleetLiveModel — dispose()', () {
    test('dispose() prevents further onChange calls from stream events', () async {
      final StreamController<ServerSnapshot> snapA =
          StreamController<ServerSnapshot>.broadcast();
      final StreamController<ConnState> stateA =
          StreamController<ConnState>.broadcast();

      int notifyCount = 0;
      final FleetLiveModel model = FleetLiveModel(
        <FleetLiveSource>[
          FleetLiveSource(
            id: 'a',
            name: 'Server A',
            initialState: ConnState.connecting,
            snapshots: snapA.stream,
            stateChanges: stateA.stream,
          ),
        ],
        onChange: () => notifyCount++,
      );

      model.dispose();

      snapA.add(_makeSnapshot());
      await Future<void>.delayed(Duration.zero);

      expect(notifyCount, equals(0));

      snapA.close();
      stateA.close();
    });
  });
}
