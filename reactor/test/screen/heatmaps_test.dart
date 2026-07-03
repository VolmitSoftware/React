library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/heatmaps.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/heatmap_scope.dart';
import 'package:reactor/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  double min = 0.0,
  double max = 100.0,
}) =>
    SamplerSample(
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
    _sample('entity-pressure-heatmap', 0.7, max: 1.0),
    _sample('chunk-load-gen-cost-map', 120.0, suffix: 'ms', max: 500.0),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

Widget _wrap(Widget child) => ArcaneThemeProvider(
      stylesheet: _sheet,
      child: HeatmapScope(
        client: null,
        child: ServerScope(
          snapshot: _fakeSnapshot(),
          state: ConnState.live,
          child: child,
        ),
      ),
    );

void main() {
  group('HeatmapsScreen', () {
    testServer(
      'renders Spatial Metrics section card heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const HeatmapsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Spatial Metrics'),
          isTrue,
          reason: 'Spatial Metrics section heading must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders Entity Pressure stat tile label',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const HeatmapsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Entity Pressure'),
          isTrue,
          reason: 'Entity Pressure tile label must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders entity-pressure-heatmap value 0.7',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const HeatmapsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('0.7'),
          isTrue,
          reason: 'entity-pressure-heatmap value 0.7 must appear in rendered HTML',
        );
      },
    );

    testServer(
      'does not render the old chunk-grid placeholder',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const HeatmapsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('pending chunk-grid export contract'),
          isFalse,
          reason: 'old BLOCKED placeholder text must not appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders requires a live connection note when client is null',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const HeatmapsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('require a live connection'),
          isTrue,
          reason: 'connection note must appear when HeatmapScope has no client',
        );
      },
    );
  });

  group('HeatmapsScreen route wiring', () {
    test('kRouteServerHeatmaps constant is correct', () {
      expect(kRouteServerHeatmaps, equals('/server/:id/heatmaps'));
    });

    test('buildReactorRoutes includes server heatmaps route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasHeatmaps = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerHeatmaps,
      );
      expect(
        hasHeatmaps,
        isTrue,
        reason: 'per-server Heatmaps tab requires /server/:id/heatmaps route',
      );
    });
  });
}
