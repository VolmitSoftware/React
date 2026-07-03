library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/world_settings.dart';
import 'package:reactor/screen/world_overrides.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/control_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrapView(Widget child) => ArcaneThemeProvider(
      stylesheet: _sheet,
      child: child,
    );

Widget _wrapScreen({required Widget child}) => ArcaneThemeProvider(
      stylesheet: _sheet,
      child: ControlScope(
        client: null,
        child: child,
      ),
    );

final List<WorldSettings> _fakeWorlds = <WorldSettings>[
  const WorldSettings(
    name: 'world',
    pressureMode: PressureMode.normal,
    budgetMs: 50.0,
    panicMs: 55.0,
    releaseMs: 45.0,
  ),
  const WorldSettings(
    name: 'world_nether',
    pressureMode: PressureMode.panic,
    budgetMs: 50.0,
    panicMs: 55.0,
    releaseMs: 45.0,
  ),
];

void main() {
  group('WorldOverridesScreen route', () {
    test('kRouteServerWorldOverrides constant is correct', () {
      expect(
        kRouteServerWorldOverrides,
        equals('/server/:id/world-overrides'),
      );
    });

    test('buildReactorRoutes contains world-overrides route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasRoute = routes.any(
        (RouteBase r) =>
            r is Route && r.path == kRouteServerWorldOverrides,
      );
      expect(
        hasRoute,
        isTrue,
        reason:
            'World Overrides screen requires /server/:id/world-overrides route',
      );
    });
  });

  group('WorldOverridesScreen sidebar', () {
    testServer(
      'ReactorShell with a server renders World Overrides sidebar item',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(
              id: 'srv1',
              name: 'Test Server',
              state: ConnState.live,
            ),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('World Overrides'),
          isTrue,
          reason:
              'World Overrides sidebar item must appear in per-server sidebar group',
        );
      },
    );
  });

  group('WorldOverridesView render', () {
    testServer(
      'renders world names, pressure badges, field labels, and values',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(WorldOverridesView(
          worlds: _fakeWorlds,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('world'),
          isTrue,
          reason: 'world name must appear in rendered HTML',
        );
        expect(
          res.body.contains('world_nether'),
          isTrue,
          reason: 'world_nether name must appear in rendered HTML',
        );
        expect(
          res.body.contains('NORMAL'),
          isTrue,
          reason: 'NORMAL pressure badge must appear for world',
        );
        expect(
          res.body.contains('PANIC'),
          isTrue,
          reason: 'PANIC pressure badge must appear for world_nether',
        );
        expect(
          res.body.contains('Budget (ms)'),
          isTrue,
          reason: 'Budget (ms) field label must appear in rendered HTML',
        );
        expect(
          res.body.contains('Panic (ms)'),
          isTrue,
          reason: 'Panic (ms) field label must appear in rendered HTML',
        );
        expect(
          res.body.contains('Release (ms)'),
          isTrue,
          reason: 'Release (ms) field label must appear in rendered HTML',
        );
        expect(
          res.body.contains('50'),
          isTrue,
          reason: 'budgetMs value 50 must appear in rendered HTML',
        );
      },
    );

    testServer(
      'empty worlds renders No worlds empty state',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(const WorldOverridesView(
          worlds: <WorldSettings>[],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('No worlds'),
          isTrue,
          reason: 'No worlds empty state must appear when worlds list is empty',
        );
      },
    );
  });

  group('WorldOverridesScreen no-connection', () {
    testServer(
      'renders require a live connection note when ControlScope client is null',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapScreen(child: const WorldOverridesScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('require a live connection'),
          isTrue,
          reason:
              'connection note must appear when ControlScope client is null',
        );
      },
    );
  });
}
