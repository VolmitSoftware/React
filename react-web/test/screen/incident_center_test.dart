library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/incident_center.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/operate_scope.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

Widget _wrapScreen(Widget child) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: OperateScope(
    client: null,
    logSocketFactory: null,
    child: ServerScope(
      snapshot: ServerSnapshot(
        byId: const <String, SamplerSample>{},
        at: DateTime(2026),
        seq: 1,
      ),
      state: ConnState.live,
      child: child,
    ),
  ),
);

const IncidentStatus _fakeStatus = IncidentStatus(
  score: 55.0,
  scoreAvailable: true,
  sampledAtMs: 1770000000000,
  state: 'ACTIVE',
  contributors: <IncidentContributor>[
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
      occurredAtMs: 1770000000000,
      startedAtMs: 1770000000000,
      source: 'circuit-manager',
      title: 'Redstone activity component throttled',
      summary: 'React temporarily blocked the busiest component.',
      cause: 'Redstone event span crossed its threshold.',
      location: IncidentLocation(
        worldId: 'world-id',
        world: 'world',
        x: 10,
        y: 64,
        z: 20,
      ),
      evidence: <IncidentContributor>[],
      actions: <IncidentAction>[
        IncidentAction(
          id: 'circuit-throttle',
          label: 'Temporary circuit throttle',
          status: 'ACTIVE',
          detail: 'Blocked for 10000 ms.',
          occurredAtMs: 1770000000000,
        ),
      ],
      context: <String, String>{'activeNodes': '8'},
    ),
  ],
);

void main() {
  group('IncidentCenterScreen route wiring', () {
    test('kRouteServerIncidentCenter constant is correct', () {
      expect(kRouteServerIncidentCenter, equals('/server/:id/incident-center'));
    });

    test('buildReactorRoutes includes server incident-center route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasRoute = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerIncidentCenter,
      );
      expect(
        hasRoute,
        isTrue,
        reason:
            'per-server Incident Center requires /server/:id/incident-center route',
      );
    });
  });

  group('IncidentCenterScreen sidebar', () {
    testServer(
      'ReactorShell with a server renders Incident Center sidebar item',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapView(
            ReactorShell(
              currentPath: '/server/srv1/incident-center',
              servers: const <ServerEntry>[
                ServerEntry(
                  id: 'srv1',
                  name: 'Test Server',
                  state: ConnState.live,
                ),
              ],
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Incident Center'),
          isTrue,
          reason:
              'Incident Center sidebar item must appear in per-server sidebar group',
        );
      },
    );
  });

  group('IncidentCenterScreen no-connection', () {
    testServer(
      'renders live connection note when OperateScope client is null',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapScreen(const IncidentCenterScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('live connection'),
          isTrue,
          reason: 'connection note must appear when OperateScope has no client',
        );
      },
    );
  });

  group('IncidentCenterView render', () {
    testServer('renders localized active state label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Active'),
        isTrue,
        reason: 'Localized active state must appear as chip label',
      );
    });

    testServer('renders structured incident cause and location', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Redstone event span crossed its threshold.') &&
            res.body.contains('world · 10, 64, 20'),
        isTrue,
        reason: 'structured cause and location must appear in incident history',
      );
    });

    testServer('renders contributor label and actual score points', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Tick P95') && res.body.contains('21.0 points'),
        isTrue,
        reason: 'contributor evidence must show its actual score points',
      );
    });

    testServer('renders Contributing Factors section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Contributing Factors'),
        isTrue,
        reason: 'Contributing Factors section heading must appear',
      );
    });

    testServer('renders Incident history section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Incident history'),
        isTrue,
        reason: 'Incident history section heading must appear',
      );
    });
  });
}
