library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/widget/status_dot.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('ReactorApp', () {
    test('is a StatelessWidget', () {
      const ReactorApp app = ReactorApp();
      expect(app, isA<StatelessWidget>());
    });

    test('kRouteRoot is "/"', () {
      expect(kRouteRoot, equals('/'));
    });

    test('kRouteAddServer is "/add-server"', () {
      expect(kRouteAddServer, equals('/add-server'));
    });

    test('buildReactorRoutes includes root fleet overview route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasRoot = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteRoot,
      );
      expect(
        hasRoot,
        isTrue,
        reason: 'Fleet Overview sidebar item requires / route',
      );
    });

    test('buildReactorRoutes includes add-server route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasAddServer = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteAddServer,
      );
      expect(
        hasAddServer,
        isTrue,
        reason: 'sidebar "Add Server" requires /add-server route',
      );
    });

    test('buildReactorRoutes includes server overview route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasOverview = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerOverview,
      );
      expect(
        hasOverview,
        isTrue,
        reason: 'per-server Overview tab requires route',
      );
    });

    test('buildReactorRoutes includes server performance route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasPerf = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerPerformance,
      );
      expect(
        hasPerf,
        isTrue,
        reason: 'per-server Performance tab requires route',
      );
    });

    test('buildReactorRoutes includes server memory route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasMem = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerMemory,
      );
      expect(hasMem, isTrue, reason: 'per-server Memory tab requires route');
    });

    testServer(
      'ReactorShell with no servers renders "Add Server" sidebar item',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const ReactorShell()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Add Server'),
          isTrue,
          reason: 'sidebar must contain "Add Server" item',
        );
      },
    );

    testServer(
      'routed shell at / renders empty-state body inside the scaffold',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(Router(routes: buildReactorShellRoutes())));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('No servers connected'),
          isTrue,
          reason:
              'body must show empty-state title when no servers are connected',
        );
        expect(
          res.body.contains('Add Server'),
          isTrue,
          reason: 'sidebar must remain present around the routed body',
        );
      },
    );

    testServer('ReactorShell with a server renders per-server sidebar group', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ReactorShell(
            servers: const <ServerEntry>[
              ServerEntry(
                id: 'srv1',
                name: 'Prod Server',
                state: ConnState.live,
              ),
            ],
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(
        res.body.contains('Prod Server'),
        isTrue,
        reason: 'server name must appear in per-server sidebar group label',
      );
      expect(
        res.body.contains('Performance'),
        isTrue,
        reason: 'Performance sub-item must appear for per-server group',
      );
      expect(
        res.body.contains('Memory'),
        isTrue,
        reason: 'Memory sub-item must appear for per-server group',
      );
    });

    testServer('ReactorShell keeps many servers in a compact selector', (
      ServerTester tester,
    ) async {
      final List<ServerEntry> servers = List<ServerEntry>.generate(
        24,
        (int i) =>
            ServerEntry(id: 'srv-$i', name: 'Server $i', state: ConnState.live),
      );
      tester.pumpComponent(
        _wrap(
          ReactorShell(servers: servers, currentPath: '/server/srv-7/overview'),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(
        res.body.contains('Servers (24)'),
        isTrue,
        reason: 'large fleets should render as a bounded server selector',
      );
      expect(
        res.body.contains('Server 23'),
        isTrue,
        reason: 'the selector should include all paired servers',
      );
      expect(
        'aria-label="Performance"'.allMatches(res.body).length,
        equals(1),
        reason: 'workspace navigation should render once for the active server',
      );
    });
  });

  group('StatusDot', () {
    test('can be constructed for every ConnState value', () {
      for (final ConnState s in ConnState.values) {
        expect(() => StatusDot(state: s), returnsNormally);
      }
    });

    test('labelFor live returns "Live"', () {
      expect(StatusDot.labelFor(ConnState.live), equals('Live'));
    });

    test('labelFor connecting returns "Connecting"', () {
      expect(StatusDot.labelFor(ConnState.connecting), equals('Connecting'));
    });

    test('labelFor degraded returns "Degraded"', () {
      expect(StatusDot.labelFor(ConnState.degraded), equals('Degraded'));
    });

    test('labelFor offline returns "Offline"', () {
      expect(StatusDot.labelFor(ConnState.offline), equals('Offline'));
    });

    testServer('StatusDot renders "Live" label for ConnState.live', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(StatusDot(state: ConnState.live)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(
        res.body.contains('Live'),
        isTrue,
        reason: 'ConnState.live must render "Live" label text',
      );
    });

    testServer('StatusDot renders "Offline" label for ConnState.offline', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(StatusDot(state: ConnState.offline)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(
        res.body.contains('Offline'),
        isTrue,
        reason: 'ConnState.offline must render "Offline" label text',
      );
    });
  });
}
