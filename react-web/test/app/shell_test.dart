library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/app_shell.dart';
import 'package:react_web/app/fleet_observer.dart';
import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/app/routes.dart';
import 'package:react_web/localization/reactor_locale.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';
import 'package:react_web/state/fleet_live_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/theme/reactor_theme.dart';
import 'package:react_web/ui/kit/mode.dart';

const ReactorStylesheet _sheet = ReactorStylesheet();

Widget _wrap(Widget child, {ReactorMode mode = ReactorMode.simple}) =>
    ArcaneThemeProvider(
      stylesheet: _sheet,
      child: ReactorThemeScope(
        brightness: Brightness.dark,
        onChanged: (Brightness _) {},
        child: ModeScope(
          mode: mode,
          onChanged: (ReactorMode _) {},
          child: child,
        ),
      ),
    );

const List<ServerEntry> _one = <ServerEntry>[
  ServerEntry(id: 'srv1', name: 'Skyblock', state: ConnState.live),
];

Future<String> _render(
  ServerTester tester,
  Widget child, {
  ReactorMode mode = ReactorMode.simple,
}) async {
  tester.pumpComponent(_wrap(child, mode: mode));
  final DocumentResponse response = await tester.request('/');
  expect(response.statusCode, 200);
  return response.body;
}

/// The shell reads its servers from FleetLiveScope, as in the app.
Widget _shell(List<ServerEntry> servers, String currentPath) => FleetLiveScope(
  revision: 1,
  servers: <FleetServerLive>[
    for (final ServerEntry server in servers)
      FleetServerLive(id: server.id, name: server.name, state: server.state),
  ],
  child: AppShell(currentPath: currentPath),
);

ServerSnapshot _snapshot(double tps) => ServerSnapshot(
  byId: <String, SamplerSample>{
    'ticks-per-second': SamplerSample(
      id: 'ticks-per-second',
      name: 'TPS',
      suffix: '',
      value: tps,
      display: '$tps',
      min: 0,
      max: 20,
      history: <double>[tps],
    ),
  },
  at: DateTime.now(),
  seq: 1,
);

void main() {
  test('ReactorApp is a StatefulWidget', () {
    expect(const ReactorApp(), isA<StatefulWidget>());
  });

  group('AppShell', () {
    testServer('the server group shows its pages, the switcher and no '
        'inspector or status bar', (ServerTester tester) async {
      final String body = await _render(tester, _shell(_one, '/fleet'));
      expect(body, contains('<nav'));
      expect(body, contains('All servers'));
      expect(body, contains('href="/fleet"'));
      expect(body, contains('href="/alerts"'));
      expect(body, contains('href="/settings"'));
      expect(body, isNot(contains('href="/server/srv1/console"')));
      expect(body, isNot(contains('reactor-inspector')));
      expect(body, isNot(contains('reactor-status-bar')));
      expect(body, isNot(contains('reactor-pane-splitter')));
      expect(body, contains('id="reactor-main"'));
    });

    testServer('a long server name ends in an ellipsis in the switcher', (
      ServerTester tester,
    ) async {
      const String name = 'Purpur » react-ux-purpur-26-3 (Plugin)';
      final String body = await _render(
        tester,
        _shell(const <ServerEntry>[
          ServerEntry(id: 'srv1', name: name, state: ConnState.live),
        ], '/server/srv1'),
      );
      final RegExpMatch? label = RegExp(
        r'<button[^>]*id="ui-switcher-button"[^>]*>.*?<span ([^>]*)>'
        '${RegExp.escape(name)}</span>',
        dotAll: true,
      ).firstMatch(body);
      expect(label, isNotNull, reason: 'the label is cut in its own span');
      expect(label!.group(1), contains('title="$name"'));
      expect(label.group(1), contains('text-overflow: ellipsis'));
    });

    testServer('a server page lists Monitor, Operate and Configure with '
        'the current page marked', (ServerTester tester) async {
      final String body = await _render(
        tester,
        _shell(_one, '/server/srv1/tuning'),
      );
      for (final String label in <String>['Monitor', 'Operate', 'Configure']) {
        expect(body, contains(label));
      }
      for (final String href in <String>[
        '/server/srv1"',
        '/server/srv1/performance',
        '/server/srv1/activity',
        '/server/srv1/map',
        '/server/srv1/incidents',
        '/server/srv1/console',
        '/server/srv1/actions',
        '/server/srv1/tuning',
        '/server/srv1/plugins',
      ]) {
        expect(body, contains('href="$href'), reason: href);
      }
      expect(
        RegExp(r'href="/server/srv1/tuning"[^>]*aria-current="page"')
                .hasMatch(body) ||
            RegExp(r'aria-current="page"[^>]*href="/server/srv1/tuning"')
                .hasMatch(body),
        isTrue,
      );
      expect(body, isNot(contains('/server/srv1/metrics')));
    });

    testServer('Advanced adds Metrics to the nav', (ServerTester tester) async {
      final String body = await _render(
        tester,
        _shell(_one, '/server/srv1'),
        mode: ReactorMode.advanced,
      );
      expect(body, contains('href="/server/srv1/metrics"'));
    });

    testServer('breadcrumbs name the server and the page', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        _shell(_one, '/server/srv1/console'),
      );
      expect(body, contains('Skyblock'));
      expect(body, contains('Console'));
    });

    testServer('the top bar menu holds theme, mode and language', (
      ServerTester tester,
    ) async {
      final String body = await _render(tester, _shell(_one, '/fleet'));
      expect(body, contains('More options'));
      expect(body, contains('Switch to light theme'));
      expect(body, contains('Switch to Advanced'));
      expect(body, contains('Select language'));
    });

    testServer('a server down shows Connection lost and Reconnect on the '
        'server group pages', (ServerTester tester) async {
      final String body = await _render(
        tester,
        _shell(const <ServerEntry>[
          ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.offline),
        ], '/fleet'),
      );
      expect(body, contains('Connection lost'));
      expect(body, contains('Reconnect'));
    });

    testServer('a revoked entry does not say Connection lost', (
      ServerTester tester,
    ) async {
      // Reconnecting cannot bring a revoked token back; the group page lists
      // it under Needs attention with Pair again instead.
      final String body = await _render(
        tester,
        const FleetLiveScope(
          revision: 1,
          servers: <FleetServerLive>[
            FleetServerLive(
              id: 'srv1',
              name: 'Alpha',
              state: ConnState.offline,
              access: ConnAccess.revoked,
            ),
          ],
          child: AppShell(currentPath: '/fleet'),
        ),
      );
      expect(body, isNot(contains('Connection lost')));
      expect(body, isNot(contains('Reconnect')));
    });

    testServer('a degraded server shows Reconnect', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        _shell(const <ServerEntry>[
          ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.degraded),
        ], '/alerts'),
      );
      expect(body, contains('Connection degraded'));
      expect(body, contains('Reconnect'));
    });

    testServer('live servers and no servers show no Reconnect', (
      ServerTester tester,
    ) async {
      expect(
        await _render(tester, _shell(_one, '/fleet')),
        isNot(contains('Reconnect')),
      );
    });

    testServer('the Alerts entry counts open alerts', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        FleetLiveScope(
          revision: 1,
          servers: <FleetServerLive>[
            FleetServerLive(
              id: 'srv1',
              name: 'Skyblock',
              state: ConnState.live,
              snapshot: _snapshot(2),
              lastSeen: DateTime.now(),
            ),
          ],
          child: const AppShell(currentPath: '/fleet'),
        ),
      );
      expect(body, contains('sidebar-badge'));
    });

    testServer('the language dialog lists the languages by native name', (
      ServerTester tester,
    ) async {
      final String body = await _render(
        tester,
        ReactorLocaleScope(
          locale: 'en_US',
          loading: false,
          onChanged: (String _) async {},
          child: const LanguageDialog(open: true, onClose: _noop),
        ),
      );
      expect(body, contains('Deutsch'));
      expect(body, contains('日本語'));
    });
  });

  group('routes', () {
    testServer(
      '/ with no servers shows the Pairing link field and the command',
      (ServerTester tester) async {
        final String body = await _render(
          tester,
          Router(routes: buildReactorShellRoutes()),
        );
        expect(body, contains(ReactorText.pairLink.english));
        expect(body, contains('/react web pair'));
        expect(body, isNot(contains(ReactorText.startContinue.english)));
        expect(body, contains('<nav'));
      },
    );

    testServer('/pair without a link says so and offers the New link field', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(Router(routes: buildReactorShellRoutes())));
      final DocumentResponse response = await tester.request('/pair');
      expect(response.body, contains(ReactorText.pairProblemNotALink.english));
      expect(response.body, contains(ReactorText.pairNewLink.english));
    });

    testServer('an unknown server shows Server not connected', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(Router(routes: buildReactorShellRoutes())));
      final DocumentResponse response = await tester.request(
        '/server/nope/tuning',
      );
      expect(response.statusCode, 200);
      expect(response.body, contains('Server not connected'));
    });

    test('the table holds every page', () {
      final List<String> paths = <String>[
        for (final RouteBase route in buildReactorRoutes())
          if (route is Route) route.path,
      ];
      expect(paths, <String>[
        '/',
        '/pair',
        '/fleet',
        '/alerts',
        '/settings',
        '/server/:id',
        '/server/:id/performance',
        '/server/:id/activity',
        '/server/:id/map',
        '/server/:id/incidents',
        '/server/:id/console',
        '/server/:id/actions',
        '/server/:id/tuning',
        '/server/:id/plugins',
        '/server/:id/metrics',
      ]);
    });
  });
}

void _noop() {}
