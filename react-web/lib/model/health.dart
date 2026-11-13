/// The one place a reading becomes Healthy, Warning or Critical. Tiles,
/// alerts, the Overview verdict and the server group all ask here, so a
/// value never reads Warning on one screen and Critical on another.
library;

import 'alert_thresholds.dart';
import 'sampler_sample.dart';
import 'server_snapshot.dart';

enum HealthLevel {
  /// No reading yet.
  unknown,
  healthy,
  warning,
  critical,
}

/// The incident score is critical from here on, whatever the warning line.
const double kIncidentScoreCritical = 70.0;

/// A metric that has a warning (and maybe a critical) line.
enum HealthMetric {
  tps('ticks-per-second'),
  tickTime('tick-time'),
  incidentScore('incident-score'),
  gcPercent('gc-time-percent'),
  pingP95('player-ping-p95'),

  /// Heap in use as a share of the maximum. The plugin's `memory-pressure`
  /// sampler is the allocation rate in bytes per second, not a share.
  memoryPressure('jvm-heap-utilization');

  const HealthMetric(this.samplerId);

  /// The sampler that reads it.
  final String samplerId;

  /// The metric [samplerId] reads, or null when it has no line.
  static HealthMetric? ofSampler(String samplerId) {
    for (final HealthMetric metric in HealthMetric.values) {
      if (metric.samplerId == samplerId) return metric;
    }
    return null;
  }

  /// Lower is worse (TPS); every other metric is worse when higher.
  bool get lowIsBad => this == HealthMetric.tps;

  /// The line past which it warns.
  double warnLine(AlertThresholds t) => switch (this) {
    HealthMetric.tps => t.tpsWarn,
    HealthMetric.tickTime => t.msptWarn,
    HealthMetric.incidentScore => t.incidentScoreWarn,
    HealthMetric.gcPercent => t.gcPercentWarn,
    HealthMetric.pingP95 => t.pingP95Warn,
    HealthMetric.memoryPressure => t.memoryPressureWarn,
  };

  /// The line past which it is critical; null when it only warns.
  double? criticalLine(AlertThresholds t) => switch (this) {
    HealthMetric.tps => t.tpsCrit,
    HealthMetric.incidentScore => kIncidentScoreCritical,
    _ => null,
  };
}

/// [value] as the screens show it (one decimal), so the verdict matches the
/// figure the user reads.
double shownValue(double value) => (value * 10).roundToDouble() / 10;

HealthLevel metricHealth(
  HealthMetric metric,
  double value,
  AlertThresholds thresholds,
) {
  final double shown = shownValue(value);
  bool past(double line) => metric.lowIsBad ? shown < line : shown > line;
  final double? critical = metric.criticalLine(thresholds);
  if (critical != null &&
      (metric.lowIsBad ? shown < critical : shown >= critical)) {
    return HealthLevel.critical;
  }
  if (past(metric.warnLine(thresholds))) return HealthLevel.warning;
  return HealthLevel.healthy;
}

/// The level of [sample] for [metric]; unknown without a reading.
HealthLevel sampleHealth(
  HealthMetric metric,
  SamplerSample? sample,
  AlertThresholds thresholds,
) => sample == null
    ? HealthLevel.unknown
    : metricHealth(metric, sample.value, thresholds);

/// One reading past its line.
class HealthCause {
  const HealthCause({
    required this.metric,
    required this.level,
    required this.sample,
    required this.threshold,
  });

  final HealthMetric metric;
  final HealthLevel level;
  final SamplerSample sample;

  /// The line it crossed.
  final double threshold;
}

/// A server's state and what drives it, worst first.
class HealthVerdict {
  const HealthVerdict(this.level, this.causes);

  final HealthLevel level;
  final List<HealthCause> causes;
}

/// Every reading of [snapshot] past its line, worst first, and the worst
/// level among them (healthy when none is).
HealthVerdict assessHealth(
  ServerSnapshot? snapshot,
  AlertThresholds thresholds,
) {
  if (snapshot == null) {
    return const HealthVerdict(HealthLevel.unknown, <HealthCause>[]);
  }
  final List<HealthCause> causes = <HealthCause>[];
  for (final HealthMetric metric in HealthMetric.values) {
    final SamplerSample? sample = snapshot.sampler(metric.samplerId);
    if (sample == null) continue;
    final HealthLevel level = metricHealth(metric, sample.value, thresholds);
    if (level != HealthLevel.warning && level != HealthLevel.critical) {
      continue;
    }
    causes.add(
      HealthCause(
        metric: metric,
        level: level,
        sample: sample,
        threshold: level == HealthLevel.critical
            ? metric.criticalLine(thresholds)!
            : metric.warnLine(thresholds),
      ),
    );
  }
  // Stable: within a level, the order of [HealthMetric] (TPS first).
  final List<HealthCause> sorted = <HealthCause>[
    ...causes.where((HealthCause c) => c.level == HealthLevel.critical),
    ...causes.where((HealthCause c) => c.level == HealthLevel.warning),
  ];
  return HealthVerdict(
    sorted.isEmpty ? HealthLevel.healthy : sorted.first.level,
    sorted,
  );
}
