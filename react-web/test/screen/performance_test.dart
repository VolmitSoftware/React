library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/performance.dart';
import 'package:react_web/state/chart_history.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'monitor_support.dart';

void main() {
  group('PerformanceScreen (Ticks)', () {
    testServer('Simple: TPS and tick time, then the tick chart alone', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const PerformanceScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('ticks-per-second', 19.9),
            sample('tick-time', 12.5, suffix: 'ms'),
            sample('tick-spike-rate', 0.4),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(occurrences(body, 'class="ui-metric"'), 2);
      expect(body, contains(ReactorText.overviewTps.english));
      expect(body, isNot(contains(ReactorText.performanceSpikeRate.english)));
      expect(occurrences(body, 'data-chart-card'), 1);
    });

    testServer('Advanced: tiles on the page, then the tick chart', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const PerformanceScreen(),
          mode: ReactorMode.advanced,
          snapshot: snapshotOf(<SamplerSample>[
            sample('tick-time', 12.5, suffix: 'ms'),
            sample('tick-ms-p50', 11),
            sample('tick-ms-p95', 20),
            sample('tick-ms-p99', 31),
            sample('tick-spike-rate', 0.4),
            sample('top-world-mspt', 8),
            sample('top-chunk-cost', 2),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'class="ui-metric"'), 4);
      // One chart: the spike rate is a tile.
      expect(occurrences(body, 'data-chart-card'), 1);
      expect(body, contains('<polyline'));
      for (final String label in <String>[
        ReactorText.commonTickTime.english,
        ReactorText.performanceSpikeRate.english,
        ReactorText.commonTopWorldMspt.english,
        ReactorText.commonTopChunkCost.english,
      ]) {
        expect(body, contains(label));
      }
      expect(body, contains('12.5'));
    });

    testServer('missing percentiles leave the tick chart with one series', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const PerformanceScreen(),
          snapshot: snapshotOf(<SamplerSample>[sample('tick-time', 3)]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains('p95')));
      expect(body, contains('--'));
    });

    testServer('the tick chart follows the stored range, percentiles too', (
      ServerTester tester,
    ) async {
      final ChartHistory charts = await chartsOn(
        ChartRange.sixHours,
        <String, double>{'tick-time': 12, 'tick-ms-p95': 30},
      );
      tester.pumpComponent(
        onServer(
          const PerformanceScreen(),
          mode: ReactorMode.advanced,
          charts: charts,
          snapshot: snapshotOf(<SamplerSample>[
            sample('tick-time', 12.5, suffix: 'ms'),
            sample('tick-ms-p50', 11),
            sample('tick-ms-p95', 20),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('aria-label="${ReactorText.chartRange.english}"'));
      expect(body, contains('aria-pressed="true" data-variant="outline">6h<'));
      // p50 has nothing stored: the stored chart draws tick time and p95.
      expect(body, contains('>p95<'));
      expect(body, isNot(contains('>p50<')));
    });

    testServer('loads while connecting', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const PerformanceScreen(), state: ConnState.connecting),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('ui-loading'));
    });
  });
}
