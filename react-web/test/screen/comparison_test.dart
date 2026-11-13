library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/screen/comparison.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';

import 'fleet_test_support.dart';

MetricReading _reading(String name, double value) =>
    (id: name, name: name, sample: testSample('tick-time', value));

List<String> _names(List<MetricReading> readings) => <String>[
  for (final MetricReading reading in readings) reading.name,
];

/// Positions of [names] in [body], in the order given.
List<int> _positions(String body, List<String> names) => <int>[
  for (final String name in names) body.indexOf('>$name<'),
];

void main() {
  group('rankReadings', () {
    final List<MetricReading> readings = <MetricReading>[
      _reading('Mid', 30),
      _reading('Low', 10),
      _reading('High', 50),
    ];

    test('puts the lowest first where lower is better', () {
      for (final String cost in <String>[
        'tick-time',
        'memory-used',
        'incident-score',
        'gc-time-percent',
        'player-ping-p95',
      ]) {
        expect(_names(rankReadings(readings, cost)), <String>[
          'Low',
          'Mid',
          'High',
        ], reason: cost);
      }
    });

    test('servers that read the same share a rank and keep name order', () {
      final List<MetricReading> tied = <MetricReading>[
        (
          id: 'c',
          name: 'Purpur',
          sample: testSample('ticks-per-second', 19.98, display: '20'),
        ),
        (
          id: 'a',
          name: 'Folia',
          sample: testSample('ticks-per-second', 19.99, display: '20'),
        ),
        (
          id: 'b',
          name: 'Paper',
          sample: testSample('ticks-per-second', 18.5, display: '18.5'),
        ),
      ];
      final List<MetricReading> ranked = rankReadings(tied, 'ticks-per-second');
      expect(_names(ranked), <String>['Folia', 'Purpur', 'Paper']);
      expect(competitionRanks(ranked), <int>[1, 1, 3]);
    });

    test('puts the highest first where higher is better', () {
      expect(_names(rankReadings(readings, 'ticks-per-second')), <String>[
        'High',
        'Mid',
        'Low',
      ]);
    });

    test('reads a server paired twice once', () {
      final List<MetricReading> read = readMetric(<FleetServerLive>[
        liveServer(
          'a1',
          'Alpha',
          fingerprint: 'f1',
          samplers: <String, double>{'tick-time': 10},
        ),
        liveServer(
          'a2',
          'Alpha',
          fingerprint: 'f1',
          samplers: <String, double>{'tick-time': 10},
        ),
        liveServer(
          'b',
          'Beta',
          fingerprint: 'f2',
          samplers: <String, double>{'tick-time': 20},
        ),
      ], 'tick-time');
      expect(_names(read), <String>['Alpha', 'Beta']);
    });
  });

  group('ComparisonScreen', () {
    testServer('ranks TPS best first and flags the worst server', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const ComparisonScreen(),
          servers: <FleetServerLive>[
            liveServer(
              'a',
              'Alpha',
              samplers: <String, double>{'ticks-per-second': 15},
            ),
            liveServer(
              'b',
              'Beta',
              samplers: <String, double>{'ticks-per-second': 20},
            ),
            liveServer(
              'c',
              'Gamma',
              samplers: <String, double>{'ticks-per-second': 9},
            ),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      final List<int> at = _positions(body, <String>['Beta', 'Alpha', 'Gamma']);
      expect(at.every((int index) => index >= 0), isTrue);
      expect(at[0] < at[1] && at[1] < at[2], isTrue);
      // The place as a number, the reading bare under its header, and no
      // line chart (the ranking says it).
      expect(body, contains(ReactorText.comparisonRank.english));
      expect(body, contains('>3<'));
      expect(body, contains('href="/server/b"'));
      expect(body, isNot(contains('role="img"')));
      expect(body, isNot(contains('15.0 TPS')));
    });

    testServer('offers every metric by name, not by sampler id', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const ComparisonScreen(),
          servers: <FleetServerLive>[
            liveServer(
              'a',
              'Alpha',
              samplers: <String, double>{'ticks-per-second': 20},
            ),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('id="compare-metric"'));
      expect(body, contains(ReactorText.commonTickTime.english));
      expect(body, contains(ReactorText.commonPingP95.english));
      expect(body, isNot(contains('>tick-time<')));
    });

    testServer('leaves out servers that are offline or connecting', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const ComparisonScreen(),
          servers: <FleetServerLive>[
            liveServer(
              'a',
              'Alpha',
              samplers: <String, double>{'ticks-per-second': 20},
            ),
            liveServer(
              'b',
              'Cached',
              state: ConnState.offline,
              samplers: <String, double>{'ticks-per-second': 19},
            ),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('>Alpha<'));
      expect(body, isNot(contains('>Cached<')));
      expect(
        body,
        isNot(contains(ReactorText.comparisonRank.english)),
        reason: 'one server has no rank',
      );
    });

    testServer('says when no server has the metric', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const ComparisonScreen(),
          servers: <FleetServerLive>[
            liveServer('a', 'Alpha', samplers: <String, double>{'players': 4}),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.comparisonNoData.english));
      expect(body, contains('ui-empty'));
    });

    testServer('says when nothing is paired', (ServerTester tester) async {
      tester.pumpComponent(fleetHarness(const ComparisonScreen()));
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.fleetNoServersPaired.english));
    });
  });
}
