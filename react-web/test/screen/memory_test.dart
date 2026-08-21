library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/memory.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = 'MB',
  double max = 8192.0,
}) => SamplerSample(
  id: id,
  name: id,
  value: value,
  display: value.toString(),
  suffix: suffix,
  min: 0.0,
  max: max,
  history: <double>[value, value * 0.95, value * 1.05],
);

ServerSnapshot _fakeSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('memory-used', 3500.0),
    _sample('memory-free', 4692.0),
    _sample('memory-used-after-gc', 3100.0),
    _sample('memory-pressure', 42.0, suffix: '%', max: 100.0),
    _sample('gc-time-percent', 1.8, suffix: '%', max: 100.0),
    _sample('gc-pause-p95', 45.0, suffix: 'ms', max: 500.0),
    _sample('memory-garbage', 512.0),
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
  group('MemoryScreen', () {
    testServer('renders used-heap tile showing memory-used value 3500.0', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MemoryScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('3500.0'),
        isTrue,
        reason: 'memory-used value 3500.0 must appear in rendered HTML',
      );
    });

    testServer('renders gc-time-percent gauge label and value', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MemoryScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('GC Time'),
        isTrue,
        reason: 'GC Time gauge label must appear in rendered HTML',
      );
      expect(
        res.body.contains('1.8'),
        isTrue,
        reason: 'gc-time-percent value 1.8 must appear as gauge center numeric',
      );
    });

    testServer('renders Memory Pressure section heading', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MemoryScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Memory Pressure'),
        isTrue,
        reason: 'Memory Pressure section heading must appear in rendered HTML',
      );
    });

    testServer('renders gc-pause-p95 as line chart section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MemoryScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('GC Pause p95'),
        isTrue,
        reason: 'GC Pause p95 section heading must appear in rendered HTML',
      );
      // A line chart must not render the current value as a StatTile big
      // number. gc-pause-p95=45.0 → StatTile formats to "45.0"; a
      // TimeseriesChart does not render this text.
      expect(
        res.body.contains('45.0'),
        isFalse,
        reason:
            'gc-pause-p95 must render as line chart, not StatTile displaying "45.0"',
      );
    });

    testServer('renders Memory Garbage stat tile label and value', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const MemoryScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Memory Garbage'),
        isTrue,
        reason: 'Memory Garbage label must appear in rendered HTML',
      );
      expect(
        res.body.contains('512'),
        isTrue,
        reason: 'memory-garbage value 512 must appear in rendered HTML',
      );
    });
  });
}
