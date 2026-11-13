library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show StatefulBuilder;
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/incidents.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/server_scope.dart';
import 'package:react_web/state/server_session.dart';
import 'package:react_web/state/server_session_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/mode.dart';

import '../state/support/fake_react_client.dart';
import 'monitor_support.dart' show fleetOf;

const ReactorStylesheet _sheet = ReactorStylesheet();

SamplerSample _sample(String id, double value, {String suffix = ''}) =>
    SamplerSample(
      id: id,
      name: id,
      value: value,
      display: value.toStringAsFixed(1),
      suffix: suffix,
      min: 0.0,
      max: 100.0,
      history: <double>[value * 0.9, value, value * 1.1],
    );

ServerSnapshot _snapshot({double score = 12.0}) {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('incident-score', score),
    _sample('scheduler-backlog', 3.0, suffix: 'tasks'),
    _sample('backlog-growth-rate', 0.5, suffix: '/s'),
  ];
  return ServerSnapshot(
    byId: <String, SamplerSample>{
      for (final SamplerSample sample in samples) sample.id: sample,
    },
    at: DateTime(2026, 11, 12, 12),
    seq: 1,
  );
}

final DateTime _now = DateTime(2026, 11, 12, 12);

final IncidentStatus _status = IncidentStatus(
  score: 55.0,
  scoreAvailable: true,
  sampledAtMs: _now.millisecondsSinceEpoch,
  state: 'ACTIVE',
  contributors: const <IncidentContributor>[
    IncidentContributor(
      id: 'tick-ms-p95',
      label: 'Tick P95',
      available: true,
      weight: 0.6,
      value: 120,
      display: '120 ms',
      pressure: 0.7,
      scorePoints: 21,
      minimum: 50,
      maximum: 150,
    ),
  ],
  incidents: <IncidentRecord>[
    IncidentRecord(
      id: 'event-id',
      incidentId: 'incident-id',
      kind: 'REDSTONE_CIRCUIT',
      phase: 'THROTTLED',
      severity: 'WARNING',
      occurredAtMs: _now.millisecondsSinceEpoch - 60000,
      startedAtMs: _now.millisecondsSinceEpoch - 90000,
      source: 'circuit-manager',
      title: 'Redstone activity component throttled',
      summary: 'React temporarily blocked the busiest component.',
      cause: 'Redstone event span crossed its threshold.',
      location: const IncidentLocation(
        worldId: 'world-id',
        world: 'world',
        x: 10,
        y: 64,
        z: 20,
      ),
      evidence: const <IncidentContributor>[
        IncidentContributor(
          id: 'redstone-span',
          label: 'Redstone span',
          available: true,
          weight: 1,
          value: 8,
          display: '8 ms',
          pressure: 0.5,
          scorePoints: 4,
          minimum: 0,
          maximum: 16,
        ),
      ],
      actions: const <IncidentAction>[
        IncidentAction(
          id: 'circuit-throttle',
          label: 'Temporary circuit throttle',
          status: 'ACTIVE',
          detail: 'Blocked for 10000 ms.',
          occurredAtMs: 1770000000000,
        ),
      ],
      context: const <String, String>{'activeNodes': '8'},
    ),
  ],
);

Widget _wrap(
  Widget child, {
  ReactorMode mode = ReactorMode.simple,
  ServerSnapshot? snapshot,
}) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ModeScope(
    mode: mode,
    onChanged: (ReactorMode _) {},
    child: ServerScope(
      snapshot: snapshot ?? _snapshot(),
      state: ConnState.live,
      child: child,
    ),
  ),
);

Future<String> _render(
  ServerTester tester,
  Widget child, {
  ReactorMode mode = ReactorMode.simple,
  ServerSnapshot? snapshot,
}) async {
  tester.pumpComponent(_wrap(child, mode: mode, snapshot: snapshot));
  final DocumentResponse response = await tester.request('/');
  expect(response.statusCode, equals(200));
  return response.body;
}

void main() {
  group('IncidentsView', () {
    testServer('shows the three pressure tiles as top-level metric tiles', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(status: _status, updatedAt: _now, now: _now),
      );
      expect(body, contains('Score'));
      expect(body, contains('Backlog'));
      expect(body, contains('Backlog growth'));
      expect('class="ui-metric"'.allMatches(body).length, equals(3));
      expect(body, contains('>3.0<'));
      expect(body, contains('>tasks<'));
    });

    testServer('colours a critical score and says it in words', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(status: _status, updatedAt: _now, now: _now),
        snapshot: _snapshot(score: 85),
      );
      expect(body, contains('data-tone="error"'));
      expect(body, contains('Critical'));
    });

    testServer('states the state and primary cause in one status line', (
      ServerTester tester,
    ) async {
      // The banner's level is the score tile's (55 is past the warning
      // line); the plugin's own state follows the cause.
      final String body = await _render(
        tester,
        IncidentsView(status: _status, updatedAt: _now, now: _now),
        snapshot: _snapshot(score: 55),
      );
      expect(body, contains('data-alert="warning"'));
      expect(body, contains(ReactorText.commonWarning.english));
      expect(body, contains('Active'));
      expect(
        body,
        contains('Tick P95 at 120 ms contributed 21.0 score points.'),
      );
    });

    testServer('lists latest incidents as one card of accordion rows', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(status: _status, updatedAt: _now, now: _now),
      );
      expect(body, contains('Latest incidents'));
      expect(body, contains('arcane-accordion'));
      expect(body, contains('Redstone activity component throttled'));
      expect(body, contains('Redstone event span crossed its threshold.'));
      expect(body, contains('world · 10, 64, 20'));
      expect(body, contains('Temporary circuit throttle'));
      expect(body, contains('Warning'));
      expect(body, isNot(contains('<article')));
    });

    testServer('keeps contributors and evidence Advanced, no backlog chart', (
      ServerTester tester,
    ) async {
      final IncidentsView view = IncidentsView(
        status: _status,
        updatedAt: _now,
        now: _now,
      );
      final String simple = await _render(tester, view);
      expect(simple, isNot(contains('Contributing factors')));
      expect(simple, isNot(contains('data-chart-card')));
      expect(simple, isNot(contains('activeNodes')));
      expect(simple, isNot(contains('Redstone span')));

      final String advanced = await _render(
        tester,
        view,
        mode: ReactorMode.advanced,
      );
      expect(advanced, contains('Contributing factors'));
      expect(advanced, contains('Tick P95'));
      expect(advanced, isNot(contains('data-chart-card')));
      expect(advanced, contains('activeNodes'));
      expect(advanced, contains('Redstone span'));
    });

    testServer('asks for a live connection without an incidents client', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(live: false, now: _now),
      );
      expect(body, contains('Incident data requires a live connection.'));
    });

    testServer('shows the loading state before the first answer', (
      ServerTester tester,
    ) async {
      final String body = await _render(tester, IncidentsView(now: _now));
      expect(body, contains('ui-loading'));
      expect(body, contains('Loading incidents...'));
    });

    testServer('shows when the list was refreshed only once it is stale', (
      ServerTester tester,
    ) async {
      final String fresh = await _render(
        tester,
        IncidentsView(
          status: _status,
          updatedAt: _now.subtract(const Duration(seconds: 3)),
          now: _now,
        ),
      );
      expect(fresh, isNot(contains('Updated')));

      final String stale = await _render(
        tester,
        IncidentsView(
          status: _status,
          updatedAt: _now.subtract(const Duration(minutes: 2)),
          now: _now,
        ),
      );
      expect(stale, contains('Updated 2m ago'));
    });

    testServer('a failed refresh keeps the list and says when it is from', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(
          status: _status,
          error: const ReactUnavailable('down'),
          updatedAt: _now.subtract(const Duration(seconds: 20)),
          now: _now,
        ),
      );
      expect(body, contains('Incident refresh failed'));
      expect(body, contains('Updated 20s ago'));
      expect(body, contains('Redstone activity component throttled'));
    });

    testServer('a 403 reads as no access, not as unavailable', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        IncidentsView(error: const ReactForbidden(), now: _now),
      );
      expect(body, contains('This account does not have access.'));
      expect(body, isNot(contains('Incident request failed')));
      expect(body, isNot(contains('unavailable')));
    });
  });

  group('IncidentsScreen', () {
    testServer('asks for a live connection without a server session', (
      ServerTester tester,
    ) async {
      final String body = await _render(tester, const IncidentsScreen());
      expect(body, contains('Incident data requires a live connection.'));
      expect(body, contains('Score'));
    });

    testComponents('lists the incidents of the session server', (
      ComponentTester tester,
    ) async {
      final _IncidentsClient client = _IncidentsClient('a', _status);
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        'a': client,
      });
      addTearDown(fleet.dispose);
      tester.pumpComponent(
        _wrap(
          ServerSessionScope(
            session: fleet.sessionFor('a')!,
            child: const IncidentsScreen(),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(client.requests, equals(1));
      expect(
        find.text('Redstone activity component throttled'),
        findsOneComponent,
      );
    });

    testComponents('another server starts from nothing', (
      ComponentTester tester,
    ) async {
      final _IncidentsClient second = _IncidentsClient(
        'b',
        _status,
        hold: true,
      );
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        'a': _IncidentsClient('a', _status),
        'b': second,
      });
      addTearDown(fleet.dispose);
      late void Function(void Function()) rebuild;
      ServerSession current = fleet.sessionFor('a')!;
      tester.pumpComponent(
        StatefulBuilder(
          builder: (BuildContext context, void Function(void Function()) set) {
            rebuild = set;
            return _wrap(
              ServerSessionScope(
                session: current,
                child: const IncidentsScreen(),
              ),
            );
          },
        ),
      );
      await tester.pump();
      await tester.pump();
      expect(
        find.text('Redstone activity component throttled'),
        findsOneComponent,
      );

      rebuild(() => current = fleet.sessionFor('b')!);
      await tester.pump();
      expect(second.requests, equals(1));
      expect(find.text('Redstone activity component throttled'), findsNothing);
    });
  });
}

/// A server whose incident answer is [status]; [hold] never answers.
class _IncidentsClient extends FakeReactClient {
  _IncidentsClient(String id, this.status, {this.hold = false})
    : super(fingerprint: 'fp-$id');

  final IncidentStatus status;
  final bool hold;
  int requests = 0;

  @override
  Future<IncidentStatus> incidents() {
    requests++;
    return hold ? Completer<IncidentStatus>().future : Future.value(status);
  }
}
