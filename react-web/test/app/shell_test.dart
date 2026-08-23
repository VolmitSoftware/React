library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component, DomComponent;
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/localization/reactor_locale.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_live_scope.dart';
import 'package:react_web/state/fleet_rollup.dart';
import 'package:react_web/theme/reactor_theme.dart';
import 'package:react_web/widget/status_dot.dart';
import 'package:react_web/widget/language_picker.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ReactorThemeScope(
    brightness: Brightness.dark,
    onChanged: (Brightness _) {},
    child: child,
  ),
);

SamplerSample _sample(String id, double value, String display) => SamplerSample(
  id: id,
  name: id,
  suffix: '',
  value: value,
  display: display,
  min: value,
  max: value,
  history: <double>[value],
);

Finder _domId(String id) => find.byComponentPredicate(
  (Component component) => component is DomComponent && component.id == id,
  description: 'DOM element with id $id',
);

void main() {
  group('ReactorApp', () {
    test('is a StatefulWidget', () {
      const ReactorApp app = ReactorApp();
      expect(app, isA<StatefulWidget>());
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

    testServer('ReactorShell exposes the light theme command', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const ReactorShell()));
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, 200);
      expect(response.body, contains('aria-label="Switch to light theme"'));
    });

    testServer('ReactorShell exposes every supported language at top right', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ReactorLocaleScope(
            locale: reactorEnglishLocale,
            loading: false,
            onChanged: (String _) async {},
            child: const ReactorShell(),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, 200);
      expect(response.body, contains('Open language menu — English'));
      expect(response.body, contains('aria-expanded="false"'));
      expect(
        response.body.lastIndexOf('reactor-language-picker'),
        greaterThan(response.body.lastIndexOf('Open inspector')),
      );
    });

    testComponents('ReactorLanguagePicker opens, selects, and closes', (
      ComponentTester tester,
    ) async {
      String? selected;
      tester.pumpComponent(
        _wrap(
          ReactorLocaleScope(
            locale: reactorEnglishLocale,
            loading: false,
            onChanged: (String locale) async => selected = locale,
            child: const ReactorLanguagePicker(),
          ),
        ),
      );

      expect(_domId('reactor-language-options'), findsNothing);
      await tester.click(_domId('reactor-language-trigger'));
      expect(_domId('reactor-language-options'), findsOneComponent);
      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.id == 'reactor-language-options' &&
              component.attributes?['role'] == 'menu' &&
              component.attributes?['tabindex'] == '-1',
          description: 'keyboard-managed language menu',
        ),
        findsOneComponent,
      );
      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.attributes?['role'] == 'menuitemradio' &&
              component.attributes?['tabindex'] == '-1',
          description: 'keyboard-managed language radio option',
        ),
        findsNComponents(reactorLocales.length),
      );
      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.id == 'reactor-language-en_US' &&
              component.attributes?['aria-checked'] == 'true',
          description: 'checked active language option',
        ),
        findsOneComponent,
      );
      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.id == 'reactor-language-he_IL' &&
              component.attributes?['lang'] == 'he-IL' &&
              component.attributes?['dir'] == 'rtl',
          description: 'Hebrew RTL language option',
        ),
        findsOneComponent,
      );

      await tester.click(_domId('reactor-language-de_DE'));
      expect(selected, 'de_DE');
      expect(_domId('reactor-language-options'), findsNothing);

      await tester.click(_domId('reactor-language-trigger'));
      await tester.click(_domId('reactor-language-backdrop'));
      expect(_domId('reactor-language-options'), findsNothing);
    });

    testServer('ReactorShell updates its picker copy after a locale switch', (
      ServerTester tester,
    ) async {
      addTearDown(() => reactorLocalizations.installOverlayJson('{}'));
      final ReactorOverlayResult installed = reactorLocalizations
          .installOverlayJson(
            '{"language.open":"Ouvrir le menu des langues — {language}",'
            '"language.close":"Fermer le menu des langues",'
            '"language.select":"Choisir la langue"}',
          );
      expect(installed.applied, isTrue);
      tester.pumpComponent(
        _wrap(
          ReactorLocaleScope(
            locale: 'fr_FR',
            loading: false,
            onChanged: (String _) async {},
            child: const ReactorShell(),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, 200);
      expect(response.body, contains('Ouvrir le menu des langues — Français'));
    });

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
            currentPath: '/server/srv1/overview',
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
        res.body.contains('Server count: 24'),
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

    testServer('active server inspector renders route context and telemetry', (
      ServerTester tester,
    ) async {
      final ServerSnapshot snapshot = ServerSnapshot(
        byId: <String, SamplerSample>{
          'ticks-per-second': _sample('ticks-per-second', 19.7, '19.7 TPS'),
          'tick-time': _sample('tick-time', 31.2, '31.2 ms'),
          'players': _sample('players', 12, '12'),
          'memory-used': _sample('memory-used', 4198, '4.1 GB'),
          'incident-score': _sample('incident-score', 22, '22 / 100'),
        },
        at: DateTime(2026, 8, 21, 12, 34),
        seq: 42,
      );
      tester.pumpComponent(
        _wrap(
          FleetLiveScope(
            servers: <FleetServerLive>[
              FleetServerLive(
                id: 'srv1',
                name: 'Prod Server',
                state: ConnState.live,
                snapshot: snapshot,
                lastSeen: snapshot.at,
              ),
            ],
            revision: 1,
            child: const ReactorShell(
              currentPath: '/server/srv1/tweaks',
              servers: <ServerEntry>[
                ServerEntry(
                  id: 'srv1',
                  name: 'Prod Server',
                  state: ConnState.live,
                ),
              ],
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(response.statusCode, 200);
      expect(response.body.contains('Quick telemetry'), isTrue);
      expect(response.body.contains('Active view'), isTrue);
      expect(response.body.contains('Tweaks'), isTrue);
      expect(response.body.contains('Control'), isTrue);
      expect(response.body.contains('19.7 TPS'), isTrue);
      expect(response.body.contains('31.2 ms'), isTrue);
      expect(response.body.contains('4.1 GB'), isTrue);
      expect(response.body.contains('22 / 100'), isTrue);
      expect(response.body.contains('#42'), isTrue);
    });

    testServer(
      'active server inspector reports unavailable telemetry honestly',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            const ReactorShell(
              currentPath: '/server/srv1/overview',
              servers: <ServerEntry>[
                ServerEntry(
                  id: 'srv1',
                  name: 'Prod Server',
                  state: ConnState.connecting,
                ),
              ],
            ),
          ),
        );
        final DocumentResponse response = await tester.request('/');
        expect(response.statusCode, 200);
        expect(response.body.contains('Quick telemetry'), isTrue);
        expect(response.body.contains('Unavailable'), isTrue);
      },
    );
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
