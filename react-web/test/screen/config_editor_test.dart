library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/screen/config_editor.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/operate_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

ConfigTree _fakeTree() => const ConfigTree(
  sections: <ConfigSection>[
    ConfigSection(
      name: 'Performance',
      nodes: <Knob>[
        Knob(
          key: 'tick-rate',
          label: 'Tick Rate',
          type: KnobType.intType,
          value: 20,
        ),
      ],
    ),
    ConfigSection(
      name: 'Entity',
      nodes: <Knob>[
        Knob(
          key: 'max-entities',
          label: 'Max Entities',
          type: KnobType.intType,
          value: 512,
        ),
      ],
    ),
  ],
);

void main() {
  group('ConfigEditorScreen route wiring', () {
    test('kRouteServerConfig constant is correct', () {
      expect(kRouteServerConfig, equals('/server/:id/config'));
    });

    test('buildReactorRoutes includes server config route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasConfig = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerConfig,
      );
      expect(
        hasConfig,
        isTrue,
        reason: 'Config Editor screen requires /server/:id/config route',
      );
    });
  });

  group('ConfigEditorScreen sidebar', () {
    testServer('ReactorShell with a server renders Config Editor sidebar item', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            currentPath: '/server/srv1/config',
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
        res.body.contains('Config Editor'),
        isTrue,
        reason:
            'Config Editor sidebar item must appear in per-server sidebar group',
      );
    });
  });

  group('ConfigEditorView render', () {
    testServer('renders both section names Performance and Entity', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ConfigEditorView(
            tree: _fakeTree(),
            pending: const <String, Object?>{},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Performance'),
        isTrue,
        reason: 'Performance section name must appear',
      );
      expect(
        res.body.contains('Entity'),
        isTrue,
        reason: 'Entity section name must appear',
      );
    });

    testServer('renders node labels Tick Rate and Max Entities', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ConfigEditorView(
            tree: _fakeTree(),
            pending: const <String, Object?>{},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Tick Rate'),
        isTrue,
        reason: 'Tick Rate node label must appear',
      );
      expect(
        res.body.contains('Max Entities'),
        isTrue,
        reason: 'Max Entities node label must appear',
      );
    });

    testServer('renders Off Light Balanced High preset buttons', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ConfigEditorView(
            tree: _fakeTree(),
            pending: const <String, Object?>{},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Off'),
        isTrue,
        reason: 'Off preset button must appear',
      );
      expect(
        res.body.contains('Light'),
        isTrue,
        reason: 'Light preset button must appear',
      );
      expect(
        res.body.contains('Balanced'),
        isTrue,
        reason: 'Balanced preset button must appear',
      );
      expect(
        res.body.contains('High'),
        isTrue,
        reason: 'High preset button must appear',
      );
    });

    testServer('renders Apply Changes button', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(
          ConfigEditorView(
            tree: _fakeTree(),
            pending: const <String, Object?>{},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Apply Changes'),
        isTrue,
        reason: 'Apply Changes button must appear',
      );
    });
  });

  group('ConfigEditorScreen null client', () {
    testServer('renders live-connection note when OperateScope client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OperateScope(
            client: null,
            logSocketFactory: null,
            child: const ConfigEditorScreen(),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('requires a live connection'),
        isTrue,
        reason:
            'live-connection note must appear when OperateScope client is null',
      );
    });
  });
}
