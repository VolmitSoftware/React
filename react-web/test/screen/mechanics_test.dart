library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/mechanics.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  double min = 0.0,
  double max = 100.0,
}) => SamplerSample(
  id: id,
  name: id,
  value: value,
  display: value.toString(),
  suffix: suffix,
  min: min,
  max: max,
  history: <double>[value],
);

ServerSnapshot _fakeSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('redstone', 42.0, max: 10000.0),
    _sample('redstone-burst-rate', 3.5, suffix: '/s', max: 500.0),
    _sample('redstone-event-span', 1.2, suffix: 'ms', max: 50.0),
    _sample('hopper', 80.0, max: 10000.0),
    _sample('hopper-event-span', 0.8, suffix: 'ms', max: 50.0),
    _sample('hopper-chain-coalescing', 12.0, max: 1000.0),
    _sample('physics', 15.0, max: 5000.0),
    _sample('physics-event-span', 0.5, suffix: 'ms', max: 50.0),
    _sample('fluid', 25.0, max: 5000.0),
    _sample('fluid-event-span', 0.3, suffix: 'ms', max: 50.0),
    _sample('crop-fast-forward', 7.0, max: 1000.0),
    _sample('lazy-gravity-skipped', 55.0, max: 10000.0),
    _sample('spawner-light-cache-skipped', 33.0, max: 10000.0),
    _sample('explosion-packet-reduction', 2.0, max: 1000.0),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

Widget _wrap(Widget child) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ServerScope(
    snapshot: _fakeSnapshot(),
    state: ConnState.live,
    child: child,
  ),
);

void main() {
  group('MechanicsScreen', () {
    testServer('renders Redstone section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const MechanicsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Redstone'),
        isTrue,
        reason: 'Redstone section heading must appear in rendered HTML',
      );
    });

    testServer('renders Hoppers section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const MechanicsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Hoppers'),
        isTrue,
        reason: 'Hoppers section heading must appear in rendered HTML',
      );
    });

    testServer('renders Physics &amp; Fluids section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MechanicsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Physics') && res.body.contains('Fluids'),
        isTrue,
        reason: 'Physics & Fluids section heading must appear in rendered HTML',
      );
    });

    testServer('renders Optimizations section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MechanicsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Optimizations'),
        isTrue,
        reason: 'Optimizations section heading must appear in rendered HTML',
      );
    });

    testServer('renders at least one sampler value (redstone 42.0)', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MechanicsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('42.0'),
        isTrue,
        reason: 'redstone value 42.0 must appear in rendered HTML',
      );
    });
  });

  group('MechanicsScreen route wiring', () {
    test('kRouteServerMechanics constant is correct', () {
      expect(kRouteServerMechanics, equals('/server/:id/mechanics'));
    });

    test('buildReactorRoutes includes server mechanics route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasMechanics = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerMechanics,
      );
      expect(
        hasMechanics,
        isTrue,
        reason:
            'per-server Mechanics screen requires /server/:id/mechanics route',
      );
    });
  });
}
