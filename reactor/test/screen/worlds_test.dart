library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/worlds.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = 'ms',
  double max = 50.0,
}) =>
    SamplerSample(
      id: id,
      name: id,
      value: value,
      display: value.toString(),
      suffix: suffix,
      min: 0.0,
      max: max,
      history: <double>[value, value * 0.9, value * 1.1],
    );

ServerSnapshot _fakeSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('top-world-mspt', 18.2),
    _sample('per-world-tick-time', 12.5),
    _sample('top-chunk-cost', 2.1),
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
  group('WorldsScreen', () {
    testServer(
      'renders Top World MSPT section heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const WorldsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Top World MSPT'),
          isTrue,
          reason: 'Top World MSPT section heading must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders Per-World Tick Time stat tile label',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const WorldsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Per-World Tick Time'),
          isTrue,
          reason:
              'Per-World Tick Time stat tile label must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders top-world-mspt value 18.2',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const WorldsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('18.2'),
          isTrue,
          reason:
              'top-world-mspt value 18.2 must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders deferred breakdown placeholder text',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const WorldsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Per-world tick budgets moved to World Overrides'),
          isTrue,
          reason:
              'deferred breakdown placeholder must appear in rendered HTML',
        );
      },
    );
  });

  group('WorldsScreen route wiring', () {
    test('kRouteServerWorlds constant is correct', () {
      expect(kRouteServerWorlds, equals('/server/:id/worlds'));
    });

    test('buildReactorRoutes includes server worlds route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasWorlds = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerWorlds,
      );
      expect(
        hasWorlds,
        isTrue,
        reason: 'per-server Worlds tab requires /server/:id/worlds route',
      );
    });
  });
}
