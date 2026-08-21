library;

import 'package:test/test.dart';

import 'package:react_web/model/alert.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';

SamplerSample _sample(String id, double value) => SamplerSample(
  id: id,
  name: id,
  suffix: '',
  value: value,
  display: value.toString(),
  min: 0.0,
  max: 100.0,
  history: <double>[value],
);

ServerSnapshot _snapshotFrom(Map<String, double> values) {
  final Map<String, SamplerSample> byId = <String, SamplerSample>{};
  for (final MapEntry<String, double> e in values.entries) {
    byId[e.key] = _sample(e.key, e.value);
  }
  return ServerSnapshot(byId: byId, at: DateTime(2026, 6, 28), seq: 1);
}

FleetServerLive _liveServer(
  String id,
  String name, {
  ConnState state = ConnState.live,
  Map<String, double>? samplers,
}) => FleetServerLive(
  id: id,
  name: name,
  state: state,
  snapshot: samplers != null ? _snapshotFrom(samplers) : null,
  lastSeen: DateTime(2026, 6, 28),
);

FleetAlert _alert(String serverId, AlertSeverity severity) => FleetAlert(
  serverId: serverId,
  serverName: serverId,
  metricId: 'ticks-per-second',
  severity: severity,
  title: 'Test Alert',
  message: 'test',
  value: 0.0,
  threshold: 0.0,
  firstSeen: DateTime(2026, 6, 28),
  lastSeen: DateTime(2026, 6, 28),
);

void main() {
  group('FleetRollup.compute', () {
    test('two live servers tps 20 players 10+5 no penalties', () {
      final List<FleetServerLive> servers = <FleetServerLive>[
        _liveServer(
          'a',
          'Alpha',
          samplers: <String, double>{'ticks-per-second': 20.0, 'players': 10.0},
        ),
        _liveServer(
          'b',
          'Beta',
          samplers: <String, double>{'ticks-per-second': 20.0, 'players': 5.0},
        ),
      ];

      final FleetRollup r = FleetRollup.compute(
        servers: servers,
        openAlerts: const <FleetAlert>[],
      );

      expect(r.meanTps, equals(20.0));
      expect(r.worstTps, equals(20.0));
      expect(r.totalPlayers, equals(15));
      expect(r.worstMspt, equals(0.0));
      expect(r.compositeHealth, equals(100));
      expect(r.servers[0].health, equals(FleetHealth.healthy));
      expect(r.servers[1].health, equals(FleetHealth.healthy));
      expect(r.needsAttention, isEmpty);
    });

    test('one server tps 10 (critical) one tps 20 (healthy)', () {
      final List<FleetServerLive> servers = <FleetServerLive>[
        _liveServer(
          'a',
          'Alpha',
          samplers: <String, double>{'ticks-per-second': 10.0},
        ),
        _liveServer(
          'b',
          'Beta',
          samplers: <String, double>{'ticks-per-second': 20.0},
        ),
      ];

      final FleetRollup r = FleetRollup.compute(
        servers: servers,
        openAlerts: const <FleetAlert>[],
      );

      expect(r.meanTps, equals(15.0));
      expect(r.worstTps, equals(10.0));
      expect(r.compositeHealth, equals(75));
      expect(r.servers[0].health, equals(FleetHealth.critical));
      expect(r.servers[1].health, equals(FleetHealth.healthy));
      expect(r.needsAttention.length, equals(1));
      expect(r.needsAttention.first.id, equals('a'));
    });

    test(
      'offline server scores 0 health offline counted as 0 in composite',
      () {
        final List<FleetServerLive> servers = <FleetServerLive>[
          _liveServer(
            'a',
            'Alpha',
            samplers: <String, double>{'ticks-per-second': 20.0},
          ),
          _liveServer(
            'b',
            'Beta',
            state: ConnState.offline,
            samplers: <String, double>{'ticks-per-second': 20.0},
          ),
        ];

        final FleetRollup r = FleetRollup.compute(
          servers: servers,
          openAlerts: const <FleetAlert>[],
        );

        expect(r.servers[1].health, equals(FleetHealth.offline));
        expect(r.compositeHealth, equals(50));
        expect(
          r.needsAttention.any((FleetServerHealth s) => s.id == 'b'),
          isTrue,
        );
      },
    );

    test('alerts grouped by severity per-server alertCount', () {
      final List<FleetServerLive> servers = <FleetServerLive>[
        _liveServer(
          'a',
          'Alpha',
          samplers: <String, double>{'ticks-per-second': 20.0},
        ),
      ];
      final List<FleetAlert> alerts = <FleetAlert>[
        _alert('a', AlertSeverity.critical),
        _alert('a', AlertSeverity.warning),
      ];

      final FleetRollup r = FleetRollup.compute(
        servers: servers,
        openAlerts: alerts,
      );

      expect(r.alertCounts[AlertSeverity.critical], equals(1));
      expect(r.alertCounts[AlertSeverity.warning], equals(1));
      expect(r.alertCounts[AlertSeverity.info], equals(0));
      expect(r.servers[0].alertCount, equals(2));
      expect(
        r.needsAttention.any((FleetServerHealth s) => s.id == 'a'),
        isTrue,
      );
    });

    test(
      'empty servers list produces zero compositeHealth meanTps and empty needsAttention',
      () {
        final FleetRollup r = FleetRollup.compute(
          servers: const <FleetServerLive>[],
          openAlerts: const <FleetAlert>[],
        );

        expect(r.compositeHealth, equals(0));
        expect(r.meanTps, equals(0.0));
        expect(r.needsAttention, isEmpty);
        expect(r.alertCounts[AlertSeverity.critical], equals(0));
        expect(r.alertCounts[AlertSeverity.warning], equals(0));
        expect(r.alertCounts[AlertSeverity.info], equals(0));
      },
    );
  });
}
