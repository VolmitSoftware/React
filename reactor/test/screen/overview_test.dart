library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/localization/reactor_localizations.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/overview.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/server_scope.dart';

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
    _sample('ticks-per-second', 19.9, max: 20.0),
    _sample('incident-score', 12.0, max: 100.0),
    _sample('tick-time', 2.5, suffix: 'ms', max: 50.0),
    _sample('players', 42.0),
    _sample('entities', 350.0),
    _sample('chunks', 1200.0),
    _sample('memory-used', 4096.0, suffix: 'MB', max: 8192.0),
    _sample('gc-time-percent', 3.2, suffix: '%'),
  ];
  final Map<String, SamplerSample> byId = <String, SamplerSample>{
    for (final SamplerSample s in samples) s.id: s,
  };
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

ServerSnapshot _lowTpsSnapshot() {
  final List<SamplerSample> samples = <SamplerSample>[
    _sample('ticks-per-second', 1.0, max: 20.0),
    _sample('incident-score', 0.0, max: 100.0),
    _sample('tick-time', 0.5, suffix: 'ms', max: 50.0),
    _sample('players', 0.0),
    _sample('entities', 0.0),
    _sample('chunks', 0.0),
    _sample('memory-used', 0.0, suffix: 'MB', max: 8192.0),
    _sample('gc-time-percent', 0.0, suffix: '%'),
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
  group('OverviewScreen', () {
    testServer('renders TPS headline value 19.9', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const OverviewScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('19.9'),
        isTrue,
        reason: 'TPS value 19.9 must appear in rendered HTML',
      );
    });

    testServer('renders Players stat tile label', (ServerTester tester) async {
      tester.pumpComponent(_wrap(const OverviewScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.commonPlayers.english),
        isTrue,
        reason: 'Players stat tile label must appear in rendered HTML',
      );
    });

    testServer('renders Incident Score gauge label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const OverviewScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.commonIncidentScore.english),
        isTrue,
        reason: 'Incident Score gauge label must appear in rendered HTML',
      );
    });

    testServer('renders memory-used stat tile with full label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const OverviewScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.commonMemoryUsed.english),
        isTrue,
        reason: 'Memory Used tile label must appear in rendered HTML',
      );
    });

    testServer(
      'TPS gauge shows error status at TPS=1.0 (invertStatus correctness)',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(const OverviewScreen(), snapshot: _lowTpsSnapshot()),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('var(--destructive)'),
          isTrue,
          reason:
              'Low TPS (1.0) must trigger error status, rendering var(--destructive) in the badge dot',
        );
      },
    );
  });
}
