library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/logs.dart';
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

void main() {
  group('LogsScreen route wiring', () {
    test('kRouteServerLogs constant is correct', () {
      expect(kRouteServerLogs, equals('/server/:id/logs'));
    });

    test('buildReactorRoutes includes server logs route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasLogs = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerLogs,
      );
      expect(
        hasLogs,
        isTrue,
        reason: 'per-server Logs screen requires /server/:id/logs route',
      );
    });
  });

  group('LogsScreen sidebar', () {
    testServer('ReactorShell with a server renders Logs sidebar item', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            currentPath: '/server/srv1/logs',
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
        res.body.contains('Logs'),
        isTrue,
        reason: 'Logs sidebar item must appear in per-server sidebar group',
      );
    });
  });

  group('LogsScreen no-connection', () {
    testServer('renders live connection note when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapScreen(const LogsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('live connection'),
        isTrue,
        reason: 'connection note must appear when OperateScope has no client',
      );
    });
  });

  group('LogsView render', () {
    testServer('renders the authenticated server console', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>[],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(res.body.contains('Server Console'), isTrue);
      expect(res.body.contains('console:execute'), isTrue);
      expect(res.body.contains('Run Command'), isTrue);
    });

    testServer('renders log line content', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>['[INFO] hello'],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('hello'),
        isTrue,
        reason: 'log line content must appear',
      );
    });

    testServer('renders Pause control when not paused', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>[],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Pause'),
        isTrue,
        reason: 'Pause button must appear when not paused',
      );
    });

    testServer('renders Resume control when paused', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>[],
            paused: true,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Resume'),
        isTrue,
        reason: 'Resume button must appear when paused',
      );
    });

    testServer('renders Clear control', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>[],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Clear'),
        isTrue,
        reason: 'Clear button must appear in toolbar',
      );
    });

    testServer('renders Level select', (ServerTester tester) async {
      tester.pumpComponent(
        _wrapView(
          LogsView(
            lines: const <String>[],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Level'),
        isTrue,
        reason: 'Level select label must appear in toolbar',
      );
    });
  });
}
