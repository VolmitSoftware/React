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
  state: 'CRITICAL',
  timeline: <String>['spike at t0'],
  contributors: <IncidentContributor>[
    IncidentContributor(name: 'mspt', weight: 0.6, value: 18.2),
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
    testServer('renders CRITICAL state label', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('CRITICAL'),
        isTrue,
        reason: 'CRITICAL state string must appear as chip label',
      );
    });

    testServer('renders spike at t0 timeline entry', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('spike at t0'),
        isTrue,
        reason: 'timeline entry must appear in Incident Timeline section',
      );
    });

    testServer('renders contributor name mspt', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('mspt'),
        isTrue,
        reason: 'contributor name must appear in Contributing Factors section',
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

    testServer('renders Incident Timeline section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(const IncidentCenterView(status: _fakeStatus)),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Incident Timeline'),
        isTrue,
        reason: 'Incident Timeline section heading must appear',
      );
    });
  });
}
