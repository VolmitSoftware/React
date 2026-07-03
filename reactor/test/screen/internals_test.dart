library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/internals.dart';
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
    _sample('react-async-tick-time', 1.2, suffix: 'ms'),
    _sample('react-sync-tick-time', 0.8, suffix: 'ms'),
    _sample('react-jobs-queue', 14.0),
    _sample('react-job-queue-time', 5.3, suffix: 'ms'),
    _sample('react-job-budget', 50.0, suffix: 'ms'),
    _sample('processor-system-load', 45.0, suffix: '%'),
    _sample('processor-process-load', 72.0, suffix: '%'),
    _sample('processor-outside-load', 30.0, suffix: '%'),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

ServerSnapshot _highCpuSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('react-async-tick-time', 1.0, suffix: 'ms'),
    _sample('react-sync-tick-time', 0.5, suffix: 'ms'),
    _sample('react-jobs-queue', 0.0),
    _sample('react-job-queue-time', 0.0, suffix: 'ms'),
    _sample('react-job-budget', 50.0, suffix: 'ms'),
    _sample('processor-system-load', 95.0, suffix: '%'),
    _sample('processor-process-load', 90.0, suffix: '%'),
    _sample('processor-outside-load', 5.0, suffix: '%'),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
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
  group('InternalsScreen', () {
    testServer(
      'renders Jobs section heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const InternalsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Jobs'),
          isTrue,
          reason: 'Jobs section heading must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders CPU Load section heading',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const InternalsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('CPU Load'),
          isTrue,
          reason: 'CPU Load section heading must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders Process Load gauge label',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const InternalsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Process Load'),
          isTrue,
          reason: 'Process Load gauge label must appear in rendered HTML',
        );
      },
    );

    testServer(
      'renders react-jobs-queue value 14.0',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const InternalsScreen()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('14.0'),
          isTrue,
          reason: 'react-jobs-queue value 14.0 must appear in rendered HTML',
        );
      },
    );

    testServer(
      'high processor-process-load (90) renders var(--destructive) gauge error',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(const InternalsScreen(), snapshot: _highCpuSnapshot()),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('var(--destructive)'),
          isTrue,
          reason:
              'processor-process-load=90 exceeds threshold 85 → GaugeStatus.error → var(--destructive) must appear',
        );
      },
    );
  });

  group('kRouteServerInternals', () {
    test('constant is "/server/:id/internals"', () {
      expect(kRouteServerInternals, equals('/server/:id/internals'));
    });

    test('buildReactorRoutes includes server internals route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasInternals = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerInternals,
      );
      expect(
        hasInternals,
        isTrue,
        reason: 'Internals sidebar item requires /server/:id/internals route',
      );
    });
  });
}
