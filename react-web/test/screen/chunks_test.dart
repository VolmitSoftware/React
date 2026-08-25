library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/chunks.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  double min = 0.0,
  double max = 100.0,
  String? name,
}) => SamplerSample(
  id: id,
  name: name ?? id,
  value: value,
  display: value.toString(),
  suffix: suffix,
  min: min,
  max: max,
  history: <double>[value],
);

ServerSnapshot _fakeSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('chunks', 1200.0, max: 10000.0),
    _sample('chunks-loaded', 5.0, suffix: '/s', max: 200.0),
    _sample('chunks-generated', 3.0, suffix: '/s', max: 200.0),
    _sample('chunk-load-ms', 3.2, suffix: 'ms', max: 50.0),
    _sample('chunk-gen-ms', 8.5, suffix: 'ms', max: 100.0),
    _sample(
      'world-save-event-interval',
      45.0,
      name: 'World Save Event Interval',
      suffix: 'ms',
      max: 5000.0,
    ),
    _sample('pdc-write-batcher', 12.0, suffix: 'ms', max: 1000.0),
    _sample('chunks-force-loaded', 6.0),
    _sample('chunk-tickets', 23.0),
    _sample('chunk-unloads', 4.0, suffix: '/s'),
    _sample('worlds', 3.0),
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
  group('ChunksScreen', () {
    testServer('renders Chunks stat tile label', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Chunks'),
        isTrue,
        reason: 'Chunks stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders Persistence section card heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Persistence'),
        isTrue,
        reason: 'Persistence section heading must appear in rendered HTML',
      );
    });

    testServer('renders World Save Event Interval stat tile label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('World Save Event Interval'),
        isTrue,
        reason: 'World Save stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders chunks value 1200.0', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('1200.0'),
        isTrue,
        reason: 'chunks value 1200.0 must appear in rendered HTML',
      );
    });

    testServer('renders Chunk Load/Gen Time section card heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Chunk Load/Gen Time'),
        isTrue,
        reason:
            'Chunk Load/Gen Time section heading must appear in rendered HTML',
      );
    });

    testServer('renders residency metrics', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const ChunksScreen()));
      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(res.body.contains('Residency'), isTrue);
      expect(res.body.contains('23.0'), isTrue);
    });
  });

  group('ChunksScreen route wiring', () {
    test('kRouteServerChunks constant is correct', () {
      expect(kRouteServerChunks, equals('/server/:id/chunks'));
    });

    test('buildReactorRoutes includes server chunks route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasChunks = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerChunks,
      );
      expect(
        hasChunks,
        isTrue,
        reason: 'per-server Chunks tab requires /server/:id/chunks route',
      );
    });
  });
}
