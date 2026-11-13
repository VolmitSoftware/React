import 'package:react_web/model/server_summary.dart';
import 'package:test/test.dart';

void main() {
  test('reads the summary the plugin sends', () {
    final ServerSummary summary = ServerSummary.fromJson(<String, dynamic>{
      'serverName': 'Lobby',
      'fingerprint': 'ab' * 32,
      'capturedAtMs': 1763030400000,
      'players': 14,
      'tps': 20,
      'tickMs': 21.3,
      'incident': <String, dynamic>{'state': 'ACTIVE', 'open': 2},
      'pairing': <String, dynamic>{
        'tokenId': 'tok_x',
        'role': 'operator',
        'scopes': <String>['read'],
        'expiresAt': '2026-11-20T10:00:00Z',
        'lifetimeSeconds': 604800,
      },
    });
    expect(summary.serverName, 'Lobby');
    expect(summary.players, 14);
    expect(summary.tps, 20.0);
    expect(summary.tickMs, 21.3);
    expect(summary.incidentState, 'ACTIVE');
    expect(summary.openIncidents, 2);
    expect(summary.pairingEndsAt, DateTime.utc(2026, 11, 20, 10));
  });

  test('values not sampled yet stay null', () {
    final ServerSummary summary = ServerSummary.fromJson(<String, dynamic>{
      'serverName': 'Folia',
      'fingerprint': 'cd' * 32,
      'players': null,
      'tps': null,
      'tickMs': null,
      'incident': <String, dynamic>{'state': 'UNKNOWN', 'open': 0},
    });
    expect(summary.players, isNull);
    expect(summary.tps, isNull);
    expect(summary.tickMs, isNull);
    expect(summary.openIncidents, 0);
    expect(summary.pairingEndsAt, isNull);
  });
}
