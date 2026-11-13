library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:react_web/ui/kit/tabs.dart';

AppTabs _tabs() => const AppTabs(
  label: 'Performance',
  tabs: <AppTab>[
    AppTab('ticks', 'Ticks', Text('ticks body')),
    AppTab('memory', 'Memory', Text('memory body')),
    AppTab('internals', 'Internals', Text('internals body'), advanced: true),
  ],
);

Widget _wrap(Widget child, ReactorMode mode) => ArcaneThemeProvider(
  stylesheet: const ReactorStylesheet(),
  child: ModeScope(mode: mode, onChanged: (ReactorMode _) {}, child: child),
);

void main() {
  testServer('shows the first tab without ?tab=', (ServerTester tester) async {
    tester.pumpComponent(_wrap(_tabs(), ReactorMode.simple));
    final DocumentResponse response = await tester.request('/');
    expect(response.body, contains('ticks body'));
    expect(response.body, isNot(contains('memory body')));
    expect(response.body, contains('data-tabs="ticks,memory"'));
    expect(response.body, contains('data-tab="ticks"'));
    expect(response.body, contains('role="tabpanel"'));
  });

  testServer('Advanced adds the advanced tabs', (ServerTester tester) async {
    tester.pumpComponent(_wrap(_tabs(), ReactorMode.advanced));
    final DocumentResponse response = await tester.request('/');
    expect(response.body, contains('data-tabs="ticks,memory,internals"'));
  });

  testServer('?tab= picks the tab', (ServerTester tester) async {
    tester.pumpComponent(
      _wrap(
        Router(
          routes: <RouteBase>[
            Route(
              path: '/perf',
              builder: (BuildContext context, RouteState state) => _tabs(),
            ),
          ],
        ),
        ReactorMode.simple,
      ),
    );
    final DocumentResponse response = await tester.request('/perf?tab=memory');
    expect(response.body, contains('memory body'));
    expect(response.body, isNot(contains('ticks body')));
  });

  testServer('an unknown ?tab= falls back to the first tab', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      _wrap(
        Router(
          routes: <RouteBase>[
            Route(
              path: '/perf',
              builder: (BuildContext context, RouteState state) => _tabs(),
            ),
          ],
        ),
        ReactorMode.simple,
      ),
    );
    final DocumentResponse response = await tester.request(
      '/perf?tab=internals',
    );
    expect(response.body, contains('ticks body'));
  });
}
