import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import 'connection_manager.dart';

class FleetServerLive {
  final String id;
  final String name;
  final ConnState state;
  final ServerSnapshot? snapshot;
  final DateTime? lastSeen;
  final String? fingerprint;
  final String? role;
  final ServerTransport? transport;
  final ConnAccess access;

  const FleetServerLive({
    required this.id,
    required this.name,
    required this.state,
    this.snapshot,
    this.lastSeen,
    this.fingerprint,
    this.role,
    this.transport,
    this.access = ConnAccess.granted,
  });

  /// Entries paired to the same server share this key; rollups count each
  /// server once.
  String get serverKey =>
      fingerprint == null || fingerprint!.isEmpty ? 'entry:$id' : fingerprint!;
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
  final String serverKey;
  final String? role;
  final ServerTransport? transport;
  final ConnAccess access;

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
    String? serverKey,
    this.role,
    this.transport,
    this.access = ConnAccess.granted,
  }) : serverKey = serverKey ?? 'entry:$id';
}

/// Fleet-wide numbers. Entries paired to the same server (same fingerprint,
/// for example an admin and a viewer token) are counted once in every
/// aggregate, in [needsAttention] and in [alertCounts]; [servers] keeps one
/// row per entry. Every surface (header, badges, side panels) reads
/// [attentionCount] and [overall] from here so they never disagree.
class FleetRollup {
  final double? meanTps;
  final double? worstTps;
  final int? totalPlayers;
  final double? worstMspt;
  final Map<AlertSeverity, int> alertCounts;
  final List<FleetServerHealth> servers;
  final List<FleetServerHealth> needsAttention;

  /// Distinct servers behind the entries.
  final int serverCount;
  final FleetHealth overall;

  const FleetRollup({
    required this.meanTps,
    required this.worstTps,
    required this.totalPlayers,
    required this.worstMspt,
    required this.alertCounts,
    required this.servers,
    required this.needsAttention,
    this.serverCount = 0,
    this.overall = FleetHealth.pending,
  });

  int get attentionCount => needsAttention.length;

  static Map<AlertSeverity, int> _emptyCounts() => <AlertSeverity, int>{
    AlertSeverity.critical: 0,
    AlertSeverity.warning: 0,
    AlertSeverity.info: 0,
  };

  static int _healthOrder(FleetHealth h) => switch (h) {
    FleetHealth.offline => 0,
    FleetHealth.critical => 1,
    FleetHealth.warning => 2,
    FleetHealth.pending => 3,
    FleetHealth.healthy => 4,
  };

  /// An entry's state: offline, pending until its first reading, else the
  /// threshold verdict every other screen shows ([assessHealth]).
  static FleetServerHealth _entryHealth(
    FleetServerLive srv,
    int alertCount,
    AlertThresholds thresholds,
  ) {
    final ServerSnapshot? currentSnapshot = currentFleetSnapshot(srv);
    final double? tps = currentSnapshot?.sampler('ticks-per-second')?.value;
    final double? mspt = currentSnapshot?.sampler('tick-time')?.value;
    final double? playerValue = currentSnapshot?.sampler('players')?.value;
    final FleetHealth health;
    if (srv.state == ConnState.offline) {
      health = FleetHealth.offline;
    } else if (currentSnapshot == null || tps == null) {
      health = FleetHealth.pending;
    } else {
      health = switch (assessHealth(currentSnapshot, thresholds).level) {
        HealthLevel.critical => FleetHealth.critical,
        HealthLevel.warning => FleetHealth.warning,
        HealthLevel.healthy => FleetHealth.healthy,
        HealthLevel.unknown => FleetHealth.pending,
      };
    }
    return FleetServerHealth(
      id: srv.id,
      name: srv.name,
      state: srv.state,
      tps: tps,
      mspt: mspt,
      players: playerValue?.round(),
      health: health,
      alertCount: alertCount,
      lastSeen: srv.lastSeen,
      serverKey: srv.serverKey,
      role: srv.role,
      transport: srv.transport,
      access: srv.access,
    );
  }

  /// The entry that speaks for its server: the one with live data, else
  /// the first in fleet order.
  static bool _betterRepresentative(
    FleetServerHealth candidate,
    FleetServerHealth current,
  ) {
    final bool candidateLive = candidate.tps != null;
    final bool currentLive = current.tps != null;
    return candidateLive && !currentLive;
  }

  /// Not healthy, or not reachable as it should be. A healthy server is
  /// never listed, whatever alerts the user left open.
  static bool _needsAttention(FleetServerHealth s) =>
      s.health != FleetHealth.healthy ||
      s.state == ConnState.offline ||
      s.state == ConnState.degraded;

  static FleetRollup compute({
    required List<FleetServerLive> servers,
    required List<FleetAlert> openAlerts,
    AlertThresholds thresholds = AlertThresholds.defaults,
  }) {
    if (servers.isEmpty) {
      return FleetRollup(
        meanTps: null,
        worstTps: null,
        totalPlayers: null,
        worstMspt: null,
        alertCounts: _emptyCounts(),
        servers: const <FleetServerHealth>[],
        needsAttention: const <FleetServerHealth>[],
      );
    }

    final Map<String, int> alertsByServer = <String, int>{};
    for (final FleetAlert a in openAlerts) {
      alertsByServer.update(
        a.serverId,
        (int count) => count + 1,
        ifAbsent: () => 1,
      );
    }

    final List<FleetServerHealth> healthList = <FleetServerHealth>[
      for (final FleetServerLive srv in servers)
        _entryHealth(srv, alertsByServer[srv.id] ?? 0, thresholds),
    ];

    final Map<String, FleetServerHealth> representatives =
        <String, FleetServerHealth>{};
    for (final FleetServerHealth entry in healthList) {
      final FleetServerHealth? current = representatives[entry.serverKey];
      if (current == null || _betterRepresentative(entry, current)) {
        representatives[entry.serverKey] = entry;
      }
    }
    final Set<String> representativeIds = <String>{
      for (final FleetServerHealth r in representatives.values) r.id,
    };

    final Map<AlertSeverity, int> alertCounts = _emptyCounts();
    for (final FleetAlert a in openAlerts) {
      if (!representativeIds.contains(a.serverId)) continue;
      alertCounts[a.severity] = (alertCounts[a.severity] ?? 0) + 1;
    }

    double tpsSum = 0.0;
    int tpsCount = 0;
    double? worstTps;
    double? worstMspt;
    int totalPlayers = 0;
    int playersCount = 0;
    FleetHealth? overall;
    for (final FleetServerHealth s in representatives.values) {
      final double? tps = s.tps;
      final double? mspt = s.mspt;
      if (tps != null) {
        tpsSum += tps;
        tpsCount++;
        if (worstTps == null || tps < worstTps) worstTps = tps;
      }
      if (mspt != null && (worstMspt == null || mspt > worstMspt)) {
        worstMspt = mspt;
      }
      final int? players = s.players;
      if (players != null) {
        totalPlayers += players;
        playersCount++;
      }
      if (overall == null || _healthOrder(s.health) < _healthOrder(overall)) {
        overall = s.health;
      }
    }

    // A revoked token needs the user (pair again or remove) even when
    // another entry of the same server speaks for it.
    final List<FleetServerHealth> needsAttention =
        <FleetServerHealth>[
          ...representatives.values.where(_needsAttention),
          for (final FleetServerHealth entry in healthList)
            if (entry.access == ConnAccess.revoked &&
                !representativeIds.contains(entry.id))
              entry,
        ]..sort((FleetServerHealth a, FleetServerHealth b) {
          final int hcmp = _healthOrder(a.health)
              .compareTo(_healthOrder(b.health));
          if (hcmp != 0) return hcmp;
          return b.alertCount.compareTo(a.alertCount);
        });

    return FleetRollup(
      meanTps: tpsCount > 0 ? tpsSum / tpsCount : null,
      worstTps: worstTps,
      totalPlayers: playersCount > 0 ? totalPlayers : null,
      worstMspt: worstMspt,
      alertCounts: alertCounts,
      servers: healthList,
      needsAttention: needsAttention,
      serverCount: representatives.length,
      overall: overall ?? FleetHealth.pending,
    );
  }
}
