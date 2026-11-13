import '../model/alert.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/reading.dart';
import '../model/units.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';

class AlertEngine {
  const AlertEngine._();

  static List<FleetAlert> computeForServer({
    required String serverId,
    required String serverName,
    required ServerSnapshot? snapshot,
    required AlertThresholds thresholds,
    required DateTime now,
  }) {
    if (snapshot == null) return const <FleetAlert>[];
    return <FleetAlert>[
      for (final HealthCause cause in assessHealth(snapshot, thresholds).causes)
        _make(
          serverId: serverId,
          serverName: serverName,
          sampler: cause.sample,
          severity: cause.level == HealthLevel.critical
              ? AlertSeverity.critical
              : AlertSeverity.warning,
          title: reactorText(_titles[cause.metric]!),
          threshold: cause.threshold,
          ltThreshold: cause.metric.lowIsBad,
          now: now,
        ),
    ];
  }

  static const Map<HealthMetric, ReactorText> _titles =
      <HealthMetric, ReactorText>{
        HealthMetric.tps: ReactorText.alertLowTps,
        HealthMetric.tickTime: ReactorText.alertHighMspt,
        HealthMetric.incidentScore: ReactorText.alertElevatedIncidentScore,
        HealthMetric.gcPercent: ReactorText.alertHighGcTime,
        HealthMetric.pingP95: ReactorText.alertHighPingP95,
        HealthMetric.memoryPressure: ReactorText.alertMemoryPressure,
      };

  static List<FleetAlert> computeFleet({
    required List<({String id, String name, ServerSnapshot? snapshot})> servers,
    required AlertThresholds thresholds,
    required DateTime now,
  }) {
    final List<FleetAlert> all = <FleetAlert>[];
    for (final ({String id, String name, ServerSnapshot? snapshot}) server
        in servers) {
      all.addAll(
        computeForServer(
          serverId: server.id,
          serverName: server.name,
          snapshot: server.snapshot,
          thresholds: thresholds,
          now: now,
        ),
      );
    }
    all.sort((FleetAlert a, FleetAlert b) {
      final int rankCmp = alertSeverityRank(b.severity)
          .compareTo(alertSeverityRank(a.severity));
      if (rankCmp != 0) return rankCmp;
      return a.serverName.compareTo(b.serverName);
    });
    return all;
  }

  static FleetAlert _make({
    required String serverId,
    required String serverName,
    required SamplerSample sampler,
    required AlertSeverity severity,
    required String title,
    required double threshold,
    required bool ltThreshold,
    required DateTime now,
  }) {
    final String sign = ltThreshold
        ? '<'
        : severity == AlertSeverity.critical
        ? '\u2265'
        : '>';
    return FleetAlert(
      serverId: serverId,
      serverName: serverName,
      metricId: sampler.id,
      severity: severity,
      title: title,
      message:
          '${readingOf(sampler).text} '
          '($sign ${compactNumber(threshold)})',
      value: sampler.value,
      threshold: threshold,
      firstSeen: now,
      lastSeen: now,
    );
  }
}
