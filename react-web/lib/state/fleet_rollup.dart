import '../model/alert.dart';
import '../model/server_snapshot.dart';
import 'connection_manager.dart';

class FleetServerLive {
  final String id;
  final String name;
  final ConnState state;
  final ServerSnapshot? snapshot;
  final DateTime? lastSeen;

  const FleetServerLive({
    required this.id,
    required this.name,
    required this.state,
    this.snapshot,
    this.lastSeen,
  });
}

ServerSnapshot? currentFleetSnapshot(FleetServerLive server) {
  if (server.state == ConnState.connecting ||
      server.state == ConnState.offline) {
    return null;
  }
  return server.snapshot;
}

enum FleetHealth { healthy, warning, critical, pending, offline }

class FleetServerHealth {
  final String id;
  final String name;
  final ConnState state;
  final double? tps;
  final double? mspt;
  final int? players;
  final FleetHealth health;
  final int alertCount;
  final DateTime? lastSeen;

  const FleetServerHealth({
    required this.id,
    required this.name,
    required this.state,
    required this.tps,
    required this.mspt,
    required this.players,
    required this.health,
    required this.alertCount,
    required this.lastSeen,
  });
}

class FleetRollup {
  final double? meanTps;
  final double? worstTps;
  final int? totalPlayers;
  final double? worstMspt;
  final int? compositeHealth;
  final Map<AlertSeverity, int> alertCounts;
  final List<FleetServerHealth> servers;
  final List<FleetServerHealth> needsAttention;

  const FleetRollup({
    required this.meanTps,
    required this.worstTps,
    required this.totalPlayers,
    required this.worstMspt,
    required this.compositeHealth,
    required this.alertCounts,
    required this.servers,
    required this.needsAttention,
  });

  static FleetRollup compute({
    required List<FleetServerLive> servers,
    required List<FleetAlert> openAlerts,
  }) {
    if (servers.isEmpty) {
      return FleetRollup(
        meanTps: null,
        worstTps: null,
        totalPlayers: null,
        worstMspt: null,
        compositeHealth: null,
        alertCounts: <AlertSeverity, int>{
          AlertSeverity.critical: 0,
          AlertSeverity.warning: 0,
          AlertSeverity.info: 0,
        },
        servers: const <FleetServerHealth>[],
        needsAttention: const <FleetServerHealth>[],
      );
    }

    final Map<AlertSeverity, int> alertCounts = <AlertSeverity, int>{
      AlertSeverity.critical: 0,
      AlertSeverity.warning: 0,
      AlertSeverity.info: 0,
    };
    for (final FleetAlert a in openAlerts) {
      alertCounts[a.severity] = (alertCounts[a.severity] ?? 0) + 1;
    }

    final List<FleetServerHealth> healthList = <FleetServerHealth>[];
    int totalScore = 0;
    int scoreCount = 0;
    double tpsSum = 0.0;
    int tpsCount = 0;
    double? worstTps;
    double? worstMspt;
    int totalPlayers = 0;
    int playersCount = 0;

    for (final FleetServerLive srv in servers) {
      final ServerSnapshot? currentSnapshot = currentFleetSnapshot(srv);
      final double? tps = currentSnapshot?.sampler('ticks-per-second')?.value;
      final double? mspt = currentSnapshot?.sampler('tick-time')?.value;
      final double? playerValue = currentSnapshot?.sampler('players')?.value;
      final int? players = playerValue?.round();
      final double incident =
          currentSnapshot?.sampler('incident-score')?.value ?? 0.0;

      int? score;
      if (srv.state == ConnState.offline) {
        score = null;
      } else if (currentSnapshot == null || tps == null) {
        score = null;
      } else {
        double base = (tps / 20.0).clamp(0.0, 1.0) * 100.0;
        if ((mspt ?? 0.0) > 50.0) base -= 20.0;
        if (incident > 50.0) base -= 20.0;
        score = base.clamp(0.0, 100.0).round();
      }

      FleetHealth health;
      if (srv.state == ConnState.offline) {
        health = FleetHealth.offline;
      } else if (score == null) {
        health = FleetHealth.pending;
      } else if (score <= 50) {
        health = FleetHealth.critical;
      } else if (score < 80) {
        health = FleetHealth.warning;
      } else {
        health = FleetHealth.healthy;
      }

      final int serverAlertCount = openAlerts
          .where((FleetAlert a) => a.serverId == srv.id)
          .length;

      if (tps != null) {
        tpsSum += tps;
        tpsCount++;
        if (worstTps == null || tps < worstTps) worstTps = tps;
      }
      if (mspt != null && (worstMspt == null || mspt > worstMspt)) {
        worstMspt = mspt;
      }
      if (players != null) {
        totalPlayers += players;
        playersCount++;
      }
      if (score != null) {
        totalScore += score;
        scoreCount++;
      }

      healthList.add(
        FleetServerHealth(
          id: srv.id,
          name: srv.name,
          state: srv.state,
          tps: tps,
          mspt: mspt,
          players: players,
          health: health,
          alertCount: serverAlertCount,
          lastSeen: srv.lastSeen,
        ),
      );
    }

    final double? meanTps = tpsCount > 0 ? tpsSum / tpsCount : null;
    final int? compositeHealth = scoreCount > 0
        ? (totalScore / scoreCount).round()
        : null;

    int healthOrder(FleetHealth h) {
      switch (h) {
        case FleetHealth.offline:
          return 0;
        case FleetHealth.critical:
          return 1;
        case FleetHealth.warning:
          return 2;
        case FleetHealth.pending:
          return 3;
        case FleetHealth.healthy:
          return 4;
      }
    }

    final List<FleetServerHealth> needsAttention =
        healthList
            .where(
              (FleetServerHealth s) =>
                  s.health != FleetHealth.healthy ||
                  s.state == ConnState.offline ||
                  s.state == ConnState.degraded ||
                  s.alertCount > 0,
            )
            .toList()
          ..sort((FleetServerHealth a, FleetServerHealth b) {
            final int hcmp = healthOrder(
              a.health,
            ).compareTo(healthOrder(b.health));
            if (hcmp != 0) return hcmp;
            return b.alertCount.compareTo(a.alertCount);
          });

    return FleetRollup(
      meanTps: meanTps,
      worstTps: worstTps,
      totalPlayers: playersCount > 0 ? totalPlayers : null,
      worstMspt: worstMspt,
      compositeHealth: compositeHealth,
      alertCounts: alertCounts,
      servers: healthList,
      needsAttention: needsAttention,
    );
  }
}
