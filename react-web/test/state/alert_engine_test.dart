library;

import 'package:test/test.dart';

import 'package:react_web/model/alert.dart';
import 'package:react_web/model/alert_thresholds.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/alert_engine.dart';

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  double min = 0.0,
  double max = 100.0,
  bool available = true,
}) => SamplerSample(
  id: id,
  name: id,
  value: value,
  display: value.toString(),
  suffix: suffix,
  min: min,
  max: max,
  history: <double>[value],
  available: available,
);

ServerSnapshot _snapshotFrom(Map<String, double> values) {
  final Map<String, SamplerSample> byId = <String, SamplerSample>{};
  for (final MapEntry<String, double> e in values.entries) {
    byId[e.key] = _sample(e.key, e.value);
  }
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

const AlertThresholds _thresholds = AlertThresholds.defaults;
final DateTime _now = DateTime(2026, 6, 28, 12, 0, 0);

void main() {
  group('AlertEngine.computeForServer', () {
    test('null snapshot returns empty list', () {
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: null,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test(
      'TPS 9.0 produces exactly one critical TPS alert with threshold 10',
      () {
        final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
          'ticks-per-second': 9.0,
        });
        final List<FleetAlert> alerts = AlertEngine.computeForServer(
          serverId: 's1',
          serverName: 'Server 1',
          snapshot: snapshot,
          thresholds: _thresholds,
          now: _now,
        );
        expect(alerts.length, equals(1));
        final FleetAlert a = alerts.first;
        expect(a.severity, equals(AlertSeverity.critical));
        expect(a.metricId, equals('ticks-per-second'));
        expect(a.threshold, equals(_thresholds.tpsCrit));
        expect(a.value, equals(9.0));
      },
    );

    test('TPS 15.0 produces one warning TPS alert with threshold 18', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'ticks-per-second': 15.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      final FleetAlert a = alerts.first;
      expect(a.severity, equals(AlertSeverity.warning));
      expect(a.metricId, equals('ticks-per-second'));
      expect(a.threshold, equals(_thresholds.tpsWarn));
    });

    test('TPS 19.9 produces no TPS alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'ticks-per-second': 19.9,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(
        alerts.where((FleetAlert a) => a.metricId == 'ticks-per-second'),
        isEmpty,
      );
    });

    test('tick-time 60 produces warning High MSPT', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'tick-time': 60.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      expect(alerts.first.severity, equals(AlertSeverity.warning));
      expect(alerts.first.metricId, equals('tick-time'));
      expect(alerts.first.title, equals('High MSPT'));
    });

    test('tick-time 40 produces no MSPT alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'tick-time': 40.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test('incident-score 70 produces warning', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'incident-score': 70.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      expect(alerts.first.severity, equals(AlertSeverity.warning));
      expect(alerts.first.metricId, equals('incident-score'));
    });

    test('incident-score 30 produces no alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'incident-score': 30.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test('gc-time-percent 20 produces warning', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'gc-time-percent': 20.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      expect(alerts.first.severity, equals(AlertSeverity.warning));
      expect(alerts.first.metricId, equals('gc-time-percent'));
    });

    test('gc-time-percent 5 produces no alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'gc-time-percent': 5.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test('player-ping-p95 250 produces warning', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'player-ping-p95': 250.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      expect(alerts.first.severity, equals(AlertSeverity.warning));
      expect(alerts.first.metricId, equals('player-ping-p95'));
    });

    test('player-ping-p95 100 produces no alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'player-ping-p95': 100.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test('memory-pressure 95 produces warning', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'memory-pressure': 95.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(1));
      expect(alerts.first.severity, equals(AlertSeverity.warning));
      expect(alerts.first.metricId, equals('memory-pressure'));
    });

    test('memory-pressure 50 produces no alert', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'memory-pressure': 50.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts, isEmpty);
    });

    test('missing sampler tick-time produces no MSPT alert and no crash', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'ticks-per-second': 19.9,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(
        alerts.where((FleetAlert a) => a.metricId == 'tick-time'),
        isEmpty,
      );
    });

    test('unavailable samples never produce alerts', () {
      final ServerSnapshot snapshot = ServerSnapshot(
        byId: <String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            0.0,
            available: false,
          ),
          'tick-time': _sample('tick-time', 100.0, available: false),
          'incident-score': _sample('incident-score', 100.0, available: false),
        },
        at: _now,
      );

      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );

      expect(alerts, isEmpty);
    });

    test('snapshot tripping TPS-crit + incident + mspt produces 3 alerts', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'ticks-per-second': 9.0,
        'incident-score': 70.0,
        'tick-time': 60.0,
      });
      final List<FleetAlert> alerts = AlertEngine.computeForServer(
        serverId: 's1',
        serverName: 'Server 1',
        snapshot: snapshot,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(3));
    });
  });

  group('AlertEngine.computeFleet', () {
    test('orders critical alerts before warnings across two servers', () {
      final ServerSnapshot snapshot = _snapshotFrom(<String, double>{
        'ticks-per-second': 9.0,
        'incident-score': 70.0,
        'tick-time': 60.0,
      });
      final List<({String id, String name, ServerSnapshot? snapshot})> servers =
          <({String id, String name, ServerSnapshot? snapshot})>[
            (id: 'alpha', name: 'Alpha', snapshot: snapshot),
            (id: 'beta', name: 'Beta', snapshot: snapshot),
          ];
      final List<FleetAlert> alerts = AlertEngine.computeFleet(
        servers: servers,
        thresholds: _thresholds,
        now: _now,
      );
      expect(alerts.length, equals(6));
      expect(alerts.first.severity, equals(AlertSeverity.critical));
      final int firstWarningIndex = alerts.indexWhere(
        (FleetAlert a) => a.severity == AlertSeverity.warning,
      );
      final int lastCriticalIndex = alerts.lastIndexWhere(
        (FleetAlert a) => a.severity == AlertSeverity.critical,
      );
      expect(
        lastCriticalIndex < firstWarningIndex,
        isTrue,
        reason: 'all critical alerts must appear before warnings',
      );
    });
  });
}
