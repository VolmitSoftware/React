library;

import 'dart:collection';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/environment_info.dart';
import 'package:react_web/screen/environment.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/operate_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

EnvironmentInfo _fakeInfo() => EnvironmentInfo(
  sections: LinkedHashMap<String, Map<String, Object?>>.from(
    <String, Map<String, Object?>>{
      'cpu': <String, Object?>{'model': 'Apple M3', 'cores': 8},
      'memory': <String, Object?>{'usedMb': 18000},
      'jvm': <String, Object?>{'version': '25'},
      'server': <String, Object?>{'brand': 'Purpur'},
    },
  ),
);

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('EnvironmentScreen route wiring', () {
    test('kRouteServerEnvironment constant is correct', () {
      expect(kRouteServerEnvironment, equals('/server/:id/environment'));
    });

    test('buildReactorRoutes includes environment route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasEnv = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerEnvironment,
      );
      expect(
        hasEnv,
        isTrue,
        reason: 'per-server Environment tab requires route',
      );
    });
  });

  group('ReactorShell sidebar', () {
    testServer('renders Environment sidebar item for a connected server', (
      ServerTester tester,
    ) async {
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
        res.body.contains('Environment'),
        isTrue,
        reason: 'Environment sidebar item must appear for connected server',
      );
    });
  });

  group('EnvironmentView', () {
    testServer('renders Apple M3 value from cpu section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Apple M3'),
        isTrue,
        reason: 'cpu.model value must appear in rendered HTML',
      );
    });

    testServer('renders cores value 8 from cpu section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('8'),
        isTrue,
        reason: 'cpu.cores value must appear in rendered HTML',
      );
    });

    testServer('renders usedMb value 18000 from memory section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('18000'),
        isTrue,
        reason: 'memory.usedMb value must appear in rendered HTML',
      );
    });

    testServer('renders Purpur value from server section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Purpur'),
        isTrue,
        reason: 'server.brand value must appear in rendered HTML',
      );
    });

    testServer('renders CPU section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('CPU'),
        isTrue,
        reason: 'CPU section heading must appear in rendered HTML',
      );
    });

    testServer('renders Memory section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Memory'),
        isTrue,
        reason: 'Memory section heading must appear in rendered HTML',
      );
    });

    testServer('renders JVM section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('JVM'),
        isTrue,
        reason: 'JVM section heading must appear in rendered HTML',
      );
    });

    testServer('renders Server section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Server'),
        isTrue,
        reason: 'Server section heading must appear in rendered HTML',
      );
    });
  });

  group('EnvironmentScreen null client', () {
    testServer('renders live-connection note when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OperateScope(
            client: null,
            logSocketFactory: null,
            child: const EnvironmentScreen(),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Environment data requires a live connection.'),
        isTrue,
        reason:
            'live-connection note must appear when OperateScope client is null',
      );
    });
  });
}
