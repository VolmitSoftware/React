import 'package:react_web/model/server_summary.dart';
import 'package:react_web/screen/pairing/continue_checks.dart';

import 'package:test/test.dart';

ServerSummary _summary({int? players = 12, double? tps = 19.8}) =>
    ServerSummary(
      serverName: 'Lobby',
      fingerprint: 'fp',
      players: players,
      tps: tps,
      openIncidents: 1,
    );

void main() {
  late DateTime now;
  late ContinueChecks checks;

  setUp(() {
    now = DateTime.utc(2026, 11, 13, 12);
    checks = ContinueChecks(clock: () => now);
  });

  test('a drop keeps the last counts, dimmed, and asks again at once', () {
    checks.answered('a', _summary());
    expect(checks.next('a', live: true), ContinueChecks.checkEvery);

    checks.dropped('a');
    final RowCheck row = checks.of('a');
    expect(row.answered, isNull, reason: 'Checking until the next answer');
    expect(row.summary!.players, 12);
    expect(row.stale, isTrue);
  });

  test('one failed summary after a drop says Not reachable', () {
    checks.answered('a', _summary());
    checks.dropped('a');
    checks.failed('a');
    expect(checks.of('a').answered, ServerReach.unreachable);
    expect(checks.next('a', live: false), ContinueChecks.retryEvery);
  });

  test('a server still answering after its socket dropped is asked again '
      'quickly for a while, then at the usual pace', () {
    checks.answered('a', _summary());
    checks.dropped('a');
    checks.answered('a', _summary());
    expect(checks.next('a', live: false), ContinueChecks.retryEvery);
    now = now.add(ContinueChecks.dropWindow);
    expect(checks.next('a', live: false), ContinueChecks.checkEvery);
  });

  test('a summary without players or TPS (a server that just started) '
      'keeps the counts from before, dimmed, and asks again in a second', () {
    checks.answered('a', _summary(players: 30, tps: 20));
    checks.dropped('a');
    checks.answered('a', _summary(players: null, tps: null));
    final RowCheck row = checks.of('a');
    expect(row.answered, ServerReach.online);
    expect(row.summary!.players, 30);
    expect(row.stale, isTrue);
    expect(checks.next('a', live: true), ContinueChecks.staleEvery);

    checks.answered('a', _summary(players: 31, tps: 19.9));
    expect(checks.of('a').summary!.players, 31);
    expect(checks.of('a').stale, isFalse);
    expect(checks.next('a', live: true), ContinueChecks.checkEvery);
  });

  test('missing values with nothing from before show as they are', () {
    checks.answered('a', _summary(players: null, tps: null));
    expect(checks.of('a').summary!.players, isNull);
    expect(checks.of('a').stale, isFalse);
  });

  test('counts kept from before give way after the stale window', () {
    checks.answered('a', _summary(players: 30, tps: 20));
    checks.answered('a', _summary(players: null, tps: null));
    expect(checks.of('a').stale, isTrue);
    now = now.add(ContinueChecks.staleWindow);
    checks.answered('a', _summary(players: null, tps: null));
    expect(checks.of('a').stale, isFalse);
    expect(checks.of('a').summary!.players, isNull);
  });

  test('a refused token ends the row and stops asking', () {
    checks.answered('a', _summary());
    checks.failed('a', ended: true);
    expect(checks.of('a').answered, ServerReach.ended);
    expect(checks.next('a', live: false), isNull);
  });
}
