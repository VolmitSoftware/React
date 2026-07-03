library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/action_descriptor.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/actions.dart';
import 'package:reactor/state/actions_controller.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/operate_scope.dart';
import 'package:reactor/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

final List<ActionDescriptor> _fakeActions = <ActionDescriptor>[
  const ActionDescriptor(
    id: 'gc',
    name: 'Force GC',
    description: 'Triggers a JVM garbage collection cycle.',
    destructive: false,
  ),
  const ActionDescriptor(
    id: 'purge-cache',
    name: 'Purge Cache',
    description: 'Clears all in-memory caches.',
    destructive: true,
  ),
];

Widget _wrapView(Widget child) => ArcaneThemeProvider(
      stylesheet: _sheet,
      child: child,
    );

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
  group('ActionsScreen route wiring', () {
    test('kRouteServerActions constant is correct', () {
      expect(kRouteServerActions, equals('/server/:id/actions'));
    });

    test('buildReactorRoutes includes server actions route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasActions = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerActions,
      );
      expect(
        hasActions,
        isTrue,
        reason:
            'per-server Actions screen requires /server/:id/actions route',
      );
    });
  });

  group('ActionsScreen sidebar', () {
    testServer(
      'ReactorShell with a server renders Actions sidebar item',
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
          res.body.contains('Actions'),
          isTrue,
          reason:
              'Actions sidebar item must appear in per-server sidebar group',
        );
      },
    );
  });

  group('ActionsScreen no-connection', () {
    testServer(
      'renders requires a live connection note when client is null',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapScreen(const ActionsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('live connection'),
          isTrue,
          reason:
              'connection note must appear when OperateScope has no client',
        );
      },
    );
  });

  group('ActionsConsoleView render', () {
    testServer(
      'renders all action names',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Force GC'),
          isTrue,
          reason: 'Force GC action name must appear',
        );
        expect(
          res.body.contains('Purge Cache'),
          isTrue,
          reason: 'Purge Cache action name must appear',
        );
      },
    );

    testServer(
      'renders Destructive chip for destructive action only',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Destructive'),
          isTrue,
          reason: 'Destructive badge must appear for destructive actions',
        );
      },
    );

    testServer(
      'renders Execute button per action card',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Execute'),
          isTrue,
          reason: 'Execute button must appear per action card',
        );
      },
    );

    testServer(
      'renders Recent Executions heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrapView(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Recent Executions'),
          isTrue,
          reason: 'Recent Executions heading must appear',
        );
      },
    );
  });
}
