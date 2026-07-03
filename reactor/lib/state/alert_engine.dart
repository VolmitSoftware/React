import '../model/alert.dart';
import '../model/alert_thresholds.dart';
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

    final List<FleetAlert> results = <FleetAlert>[];

    final SamplerSample? tps = snapshot.sampler('ticks-per-second');
    if (tps != null) {
      if (tps.value < thresholds.tpsCrit) {
        results.add(_make(
          serverId: serverId,
          serverName: serverName,
          sampler: tps,
          severity: AlertSeverity.critical,
          title: 'Low TPS',
          threshold: thresholds.tpsCrit,
          ltThreshold: true,
          now: now,
        ));
      } else if (tps.value < thresholds.tpsWarn) {
        results.add(_make(
          serverId: serverId,
          serverName: serverName,
          sampler: tps,
          severity: AlertSeverity.warning,
          title: 'Low TPS',
          threshold: thresholds.tpsWarn,
          ltThreshold: true,
          now: now,
        ));
      }
    }

    final SamplerSample? mspt = snapshot.sampler('tick-time');
    if (mspt != null && mspt.value > thresholds.msptWarn) {
      results.add(_make(
        serverId: serverId,
        serverName: serverName,
        sampler: mspt,
        severity: AlertSeverity.warning,
        title: 'High MSPT',
        threshold: thresholds.msptWarn,
        ltThreshold: false,
        now: now,
      ));
    }

    final SamplerSample? incident = snapshot.sampler('incident-score');
    if (incident != null && incident.value > thresholds.incidentScoreWarn) {
      results.add(_make(
        serverId: serverId,
        serverName: serverName,
        sampler: incident,
        severity: AlertSeverity.warning,
        title: 'Elevated incident score',
        threshold: thresholds.incidentScoreWarn,
        ltThreshold: false,
        now: now,
      ));
    }

    final SamplerSample? gc = snapshot.sampler('gc-time-percent');
    if (gc != null && gc.value > thresholds.gcPercentWarn) {
      results.add(_make(
        serverId: serverId,
        serverName: serverName,
        sampler: gc,
        severity: AlertSeverity.warning,
        title: 'High GC time',
        threshold: thresholds.gcPercentWarn,
        ltThreshold: false,
        now: now,
      ));
    }

    final SamplerSample? ping = snapshot.sampler('player-ping-p95');
    if (ping != null && ping.value > thresholds.pingP95Warn) {
      results.add(_make(
        serverId: serverId,
        serverName: serverName,
        sampler: ping,
        severity: AlertSeverity.warning,
        title: 'High ping p95',
        threshold: thresholds.pingP95Warn,
        ltThreshold: false,
        now: now,
      ));
    }

    final SamplerSample? mem = snapshot.sampler('memory-pressure');
    if (mem != null && mem.value > thresholds.memoryPressureWarn) {
      results.add(_make(
        serverId: serverId,
        serverName: serverName,
        sampler: mem,
        severity: AlertSeverity.warning,
        title: 'Memory pressure',
        threshold: thresholds.memoryPressureWarn,
        ltThreshold: false,
        now: now,
      ));
    }

    return results;
  }

  static List<FleetAlert> computeFleet({
    required List<({String id, String name, ServerSnapshot? snapshot})> servers,
    required AlertThresholds thresholds,
    required DateTime now,
  }) {
    final List<FleetAlert> all = <FleetAlert>[];
    for (final ({String id, String name, ServerSnapshot? snapshot}) server
        in servers) {
      all.addAll(computeForServer(
        serverId: server.id,
        serverName: server.name,
        snapshot: server.snapshot,
        thresholds: thresholds,
        now: now,
      ));
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
    final String suffix =
        sampler.suffix.isEmpty ? '' : ' ${sampler.suffix}';
    final String comparison =
        ltThreshold ? '(< $threshold)' : '(> $threshold)';
    return FleetAlert(
      serverId: serverId,
      serverName: serverName,
      metricId: sampler.id,
      severity: severity,
      title: title,
      message: '${sampler.display}$suffix $comparison',
      value: sampler.value,
      threshold: threshold,
      firstSeen: now,
      lastSeen: now,
    );
  }
}
