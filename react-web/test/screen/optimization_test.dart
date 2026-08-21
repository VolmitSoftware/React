library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/optimization.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/control_scope.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

final List<ControlItem> _fakeItems = <ControlItem>[
  const ControlItem(
    id: 'mob-stacking',
    name: 'Mob Stacking',
    category: 'Entity Population',
    enabled: true,
    description: 'Stacks nearby mobs to reduce entity count.',
  ),
  const ControlItem(
    id: 'fast-leaf-decay',
    name: 'Fast Leaf Decay',
    category: 'Block & World-Tick',
    enabled: false,
    description: 'Accelerates leaf decay propagation.',
  ),
];

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

Widget _wrapScreen(Widget child) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ControlScope(
    client: null,
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
  group('OptimizationScreen route wiring', () {
    test('kRouteServerOptimization constant is correct', () {
      expect(kRouteServerOptimization, equals('/server/:id/optimization'));
    });

    test('buildReactorRoutes includes server optimization route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasOptimization = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerOptimization,
      );
      expect(
        hasOptimization,
        isTrue,
        reason:
            'per-server Optimization screen requires /server/:id/optimization route',
      );
    });
  });

  group('OptimizationScreen sidebar', () {
    testServer('ReactorShell with a server renders Optimization sidebar item', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            currentPath: '/server/srv1/optimization',
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
        res.body.contains('Optimization'),
        isTrue,
        reason:
            'Optimization sidebar item must appear in per-server sidebar group',
      );
    });
  });

  group('OptimizationGridView render', () {
    testServer(
      'renders category headings Entity Population and Block & World-Tick',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrapView(
            OptimizationGridView(
              items: _fakeItems,
              total: _fakeItems.length,
              enabledCount: 1,
              onToggle: null,
              onConfigure: null,
              onSetAll: null,
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Entity Population'),
          isTrue,
          reason: 'Entity Population category heading must appear',
        );
        expect(
          res.body.contains('Block'),
          isTrue,
          reason: 'Block & World-Tick category heading must appear',
        );
      },
    );

    testServer('renders feature names Mob Stacking and Fast Leaf Decay', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OptimizationGridView(
            items: _fakeItems,
            total: _fakeItems.length,
            enabledCount: 1,
            onToggle: null,
            onConfigure: null,
            onSetAll: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Mob Stacking'),
        isTrue,
        reason: 'Mob Stacking feature name must appear',
      );
      expect(
        res.body.contains('Fast Leaf Decay'),
        isTrue,
        reason: 'Fast Leaf Decay feature name must appear',
      );
    });

    testServer('renders enabled summary 1 / 2 enabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OptimizationGridView(
            items: _fakeItems,
            total: _fakeItems.length,
            enabledCount: 1,
            onToggle: null,
            onConfigure: null,
            onSetAll: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('1 / 2 enabled'),
        isTrue,
        reason: 'Summary text 1 / 2 enabled must appear',
      );
    });

    testServer('renders Enable all and Disable all buttons', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OptimizationGridView(
            items: _fakeItems,
            total: _fakeItems.length,
            enabledCount: 1,
            onToggle: null,
            onConfigure: null,
            onSetAll: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Enable all'),
        isTrue,
        reason: 'Enable all button must appear',
      );
      expect(
        res.body.contains('Disable all'),
        isTrue,
        reason: 'Disable all button must appear',
      );
    });

    testServer('renders Configure action for each feature card', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OptimizationGridView(
            items: _fakeItems,
            total: _fakeItems.length,
            enabledCount: 1,
            onToggle: null,
            onConfigure: null,
            onSetAll: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Configure'),
        isTrue,
        reason: 'Configure button must appear for feature cards',
      );
    });
  });

  group('OptimizationScreen no-connection', () {
    testServer('renders requires a live connection note when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapScreen(const OptimizationScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('requires a live connection'),
        isTrue,
        reason: 'connection note must appear when ControlScope has no client',
      );
    });
  });
}
