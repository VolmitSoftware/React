library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/performance.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = 'ms',
  double max = 50.0,
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
    _sample('tick-time', 8.3),
    _sample('tick-ms-p50', 6.1),
    _sample('tick-ms-p95', 12.4),
    _sample('tick-ms-p99', 18.7),
    _sample('tick-spike-rate', 0.05, suffix: '/s', max: 1.0),
    _sample('top-world-mspt', 5.2),
    _sample('top-chunk-cost', 3.1),
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
  group('PerformanceScreen', () {
    testServer('renders tick-time section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const PerformanceScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Tick Duration'),
        isTrue,
        reason: 'Tick Duration section heading must appear in rendered HTML',
      );
    });

    testServer('renders top-world-mspt tile label and value', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const PerformanceScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Top World MSPT'),
        isTrue,
        reason: 'Top World MSPT tile label must appear in rendered HTML',
      );
      expect(
        res.body.contains('5.2'),
        isTrue,
        reason: 'top-world-mspt value 5.2 must appear as stat tile numeric',
      );
    });

    testServer('renders tick-spike-rate as sparkline section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const PerformanceScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Tick Spike Rate'),
        isTrue,
        reason: 'Tick Spike Rate section heading must appear in rendered HTML',
      );
      expect(
        res.body.contains('<polyline'),
        isTrue,
        reason: 'tick-spike-rate must render as a local SVG sparkline',
      );
    });

    testServer('renders top-chunk-cost tile label and value', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const PerformanceScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Top Chunk Cost'),
        isTrue,
        reason: 'Top Chunk Cost tile label must appear in rendered HTML',
      );
      expect(
        res.body.contains('3.1'),
        isTrue,
        reason: 'top-chunk-cost value 3.1 must appear as stat tile numeric',
      );
    });
  });
}
