library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/events.dart';
import 'package:reactor/state/connection_manager.dart';
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
    _sample('events-listeners', 512.0, max: 10000.0),
    _sample('event-time', 1.4, suffix: 'ms', max: 50.0),
    _sample('event-handles-per-tick', 24.0, max: 1000.0),
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
  group('EventsScreen', () {
    testServer(
      'renders Event Time section card heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const EventsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Event Time'),
          isTrue,
          reason: 'Event Time section heading must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders Listeners stat tile label',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const EventsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Listeners'),
          isTrue,
          reason: 'Listeners stat tile label must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders events-listeners value 512.0',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const EventsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('512.0'),
          isTrue,
          reason: 'events-listeners value 512.0 must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders Events or Event Time label in HTML',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const EventsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Event Time') || res.body.contains('Events'),
          isTrue,
          reason: 'Event Time (or Events) label must appear in rendered HTML',
        );
      },
    );
  });

  group('EventsScreen route wiring', () {
    test('kRouteServerEvents constant is correct', () {
      expect(kRouteServerEvents, equals('/server/:id/events'));
    });

    test('buildReactorRoutes includes server events route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasEvents = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerEvents,
      );
      expect(
        hasEvents,
        isTrue,
        reason: 'per-server Events screen requires /server/:id/events route',
      );
    });
  });
}
