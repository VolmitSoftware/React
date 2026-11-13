library;

import 'package:test/test.dart';

import 'package:react_web/model/alert_thresholds.dart';
import 'package:react_web/model/health.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';

ServerSnapshot _snapshot(Map<String, double> values) => ServerSnapshot(
  byId: <String, SamplerSample>{
    for (final MapEntry<String, double> entry in values.entries)
      entry.key: SamplerSample(
        id: entry.key,
        name: entry.key,
        suffix: '',
        value: entry.value,
        display: entry.value.toStringAsFixed(1),
        min: entry.value,
        max: entry.value,
        history: <double>[entry.value],
      ),
  },
  at: DateTime(2026, 11, 13),
);

const AlertThresholds _t = AlertThresholds.defaults;

void main() {
  group('metricHealth', () {
    test('reads the value as shown, so 9.96 and 10.04 agree', () {
      // Both show as "10.0": the verdict follows what the user reads.
      expect(metricHealth(HealthMetric.tps, 9.96, _t), HealthLevel.warning);
      expect(metricHealth(HealthMetric.tps, 10.04, _t), HealthLevel.warning);
      expect(metricHealth(HealthMetric.tps, 9.94, _t), HealthLevel.critical);
    });

    test('TPS below the warning line warns, below critical is critical', () {
      expect(metricHealth(HealthMetric.tps, 19.9, _t), HealthLevel.healthy);
      expect(metricHealth(HealthMetric.tps, 17.9, _t), HealthLevel.warning);
      expect(metricHealth(HealthMetric.tps, 9.0, _t), HealthLevel.critical);
    });

    test('tick time, GC, ping and memory warn above their lines', () {
      expect(
        metricHealth(HealthMetric.tickTime, 50.0, _t),
        HealthLevel.healthy,
      );
      expect(
        metricHealth(HealthMetric.tickTime, 50.1, _t),
        HealthLevel.warning,
      );
      expect(
        metricHealth(HealthMetric.gcPercent, 15.1, _t),
        HealthLevel.warning,
      );
      expect(metricHealth(HealthMetric.pingP95, 240, _t), HealthLevel.warning);
      expect(
        metricHealth(HealthMetric.memoryPressure, 90.5, _t),
        HealthLevel.warning,
      );
    });

    test('incident score warns above its line and is critical from 70', () {
      expect(
        metricHealth(HealthMetric.incidentScore, 50.0, _t),
        HealthLevel.healthy,
      );
      expect(
        metricHealth(HealthMetric.incidentScore, 69.9, _t),
        HealthLevel.warning,
      );
      expect(
        metricHealth(HealthMetric.incidentScore, 70.0, _t),
        HealthLevel.critical,
      );
    });
  });

  group('assessHealth', () {
    test('a server with nothing over a line is healthy with no causes', () {
      final HealthVerdict verdict = assessHealth(
        _snapshot(<String, double>{'ticks-per-second': 20, 'tick-time': 12}),
        _t,
      );
      expect(verdict.level, HealthLevel.healthy);
      expect(verdict.causes, isEmpty);
    });

    test('causes come worst first and set the level', () {
      final HealthVerdict verdict = assessHealth(
        _snapshot(<String, double>{
          'tick-time': 67.0,
          'ticks-per-second': 9.3,
          'gc-time-percent': 19.0,
        }),
        _t,
      );
      expect(verdict.level, HealthLevel.critical);
      expect(verdict.causes.first.metric, HealthMetric.tps);
      expect(
        verdict.causes.map((HealthCause cause) => cause.metric),
        containsAll(<HealthMetric>[
          HealthMetric.tickTime,
          HealthMetric.gcPercent,
        ]),
      );
    });

    test('no snapshot has no verdict', () {
      expect(assessHealth(null, _t).level, HealthLevel.unknown);
    });
  });
}
