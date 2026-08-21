library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/incidents.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  double max = 100.0,
}) => SamplerSample(
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
    _sample('incident-score', 12.0, max: 100.0),
    _sample('scheduler-backlog', 3.0, suffix: 'tasks', max: 50.0),
    _sample('backlog-growth-rate', 0.5, suffix: '/s', max: 10.0),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

ServerSnapshot _criticalSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('incident-score', 85.0, max: 100.0),
    _sample('scheduler-backlog', 30.0, suffix: 'tasks', max: 50.0),
    _sample('backlog-growth-rate', 5.0, suffix: '/s', max: 10.0),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 2);
}

Widget _wrap(Widget child, {ServerSnapshot? snapshot}) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ServerScope(
    snapshot: snapshot ?? _fakeSnapshot(),
    state: ConnState.live,
    child: child,
  ),
);

void main() {
  group('IncidentsScreen', () {
    testServer('renders Incident Timeline section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const IncidentsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Incident Timeline'),
        isTrue,
        reason:
            'Incident Timeline section heading must appear in rendered HTML',
      );
    });

    testServer('renders Backlog section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const IncidentsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Backlog'),
        isTrue,
        reason: 'Backlog section heading must appear in rendered HTML',
      );
    });

    testServer('renders Incident Score gauge label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const IncidentsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Incident Score'),
        isTrue,
        reason: 'Incident Score gauge label must appear in rendered HTML',
      );
    });

    testServer(
      'incident-score=85 renders var(--destructive) for error gauge status',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(const IncidentsScreen(), snapshot: _criticalSnapshot()),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('var(--destructive)'),
          isTrue,
          reason:
              'incident-score=85 exceeds threshold 70.0, must render var(--destructive)',
        );
      },
    );
  });

  group('IncidentsScreen route', () {
    test('kRouteServerIncidents constant is correct', () {
      expect(kRouteServerIncidents, equals('/server/:id/incidents'));
    });

    test('buildReactorRoutes includes kRouteServerIncidents route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasIncidents = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerIncidents,
      );
      expect(
        hasIncidents,
        isTrue,
        reason:
            'per-server Incidents tab requires route at kRouteServerIncidents',
      );
    });
  });
}
