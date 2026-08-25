library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/entities.dart';
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
    _sample('entities', 350.0, max: 5000.0),
    _sample('entity-ai-active-count', 120.0, max: 5000.0),
    _sample('entities-spawns', 4.0, max: 100.0),
    _sample('players', 42.0, max: 200.0),
    _sample('player-ping-p95', 55.0, suffix: 'ms', max: 500.0),
    _sample('ping-jitter', 8.0, suffix: 'ms', max: 500.0),
    _sample('entities-hostile', 63.0, max: 5000.0),
    _sample('block-entities-ticking', 17.0, max: 5000.0),
    _sample('player-joins-rate', 2.5, suffix: '/min'),
    _sample('players-unique-24h', 91.0),
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
  group('EntitiesScreen', () {
    testServer('renders Players stat tile label', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Players'),
        isTrue,
        reason: 'Players stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders Entities stat tile label', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Entities'),
        isTrue,
        reason: 'Entities stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders AI Active stat tile label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('AI Active'),
        isTrue,
        reason: 'AI Active stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders Player Ping section card heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Player Ping'),
        isTrue,
        reason: 'Player Ping section heading must appear in rendered HTML',
      );
    });

    testServer('renders players value 42.0', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('42.0'),
        isTrue,
        reason: 'Players value 42.0 must appear in rendered HTML',
      );
    });

    testServer('renders entity breakdown and player activity', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const EntitiesScreen()));
      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(res.body.contains('Entity Breakdown'), isTrue);
      expect(res.body.contains('Player Activity'), isTrue);
      expect(res.body.contains('63.0'), isTrue);
      expect(res.body.contains('91.0'), isTrue);
    });
  });

  group('EntitiesScreen route wiring', () {
    test('kRouteServerEntities constant is correct', () {
      expect(kRouteServerEntities, equals('/server/:id/entities'));
    });

    test('buildReactorRoutes includes server entities route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasEntities = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerEntities,
      );
      expect(
        hasEntities,
        isTrue,
        reason: 'per-server Entities tab requires /server/:id/entities route',
      );
    });
  });
}
