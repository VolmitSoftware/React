/// `GET /api/v1/summary`: one small answer per server for its row on the
/// start page. Values the server has not sampled yet are null.
library;

class ServerSummary {
  const ServerSummary({
    required this.serverName,
    required this.fingerprint,
    this.players,
    this.tps,
    this.tickMs,
    this.incidentState,
    this.openIncidents = 0,
    this.pairingEndsAt,
  });

  final String serverName;
  final String fingerprint;
  final int? players;
  final double? tps;
  final double? tickMs;

  /// NORMAL, ACTIVE, DISABLED or UNKNOWN.
  final String? incidentState;
  final int openIncidents;

  /// When the pairing ends unless used; it moves with every request.
  final DateTime? pairingEndsAt;

  factory ServerSummary.fromJson(Map<String, dynamic> json) {
    final Object? incident = json['incident'];
    final Object? pairing = json['pairing'];
    final Object? ends = pairing is Map<String, dynamic>
        ? pairing['expiresAt']
        : null;
    final Object? players = json['players'];
    final Object? tps = json['tps'];
    final Object? tick = json['tickMs'];
    final Object? open = incident is Map<String, dynamic>
        ? incident['open']
        : null;
    final Object? state = incident is Map<String, dynamic>
        ? incident['state']
        : null;
    return ServerSummary(
      serverName: json['serverName'] as String? ?? '',
      fingerprint: json['fingerprint'] as String? ?? '',
      players: players is num ? players.toInt() : null,
      tps: tps is num ? tps.toDouble() : null,
      tickMs: tick is num ? tick.toDouble() : null,
      incidentState: state is String ? state : null,
      openIncidents: open is num ? open.toInt() : 0,
      pairingEndsAt: ends is String ? DateTime.tryParse(ends)?.toUtc() : null,
    );
  }
}
