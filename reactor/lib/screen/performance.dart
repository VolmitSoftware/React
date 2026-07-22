library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class PerformanceScreen extends StatelessWidget {
  const PerformanceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? tickTime = scope?.snapshot?.sampler('tick-time');
    final SamplerSample? p50 = scope?.snapshot?.sampler('tick-ms-p50');
    final SamplerSample? p95 = scope?.snapshot?.sampler('tick-ms-p95');
    final SamplerSample? p99 = scope?.snapshot?.sampler('tick-ms-p99');
    final SamplerSample? spikeRate = scope?.snapshot?.sampler(
      'tick-spike-rate',
    );
    final SamplerSample? worldMspt = scope?.snapshot?.sampler('top-world-mspt');
    final SamplerSample? chunkCost = scope?.snapshot?.sampler('top-chunk-cost');

    final List<(String, List<double>)> tickSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.commonTickTime),
        tickTime?.history ?? const <double>[],
      ),
      if (p50 != null) ('p50', p50.history),
      if (p95 != null) ('p95', p95.history),
      if (p99 != null) ('p99', p99.history),
    ];

    final List<(String, List<double>)> spikeSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.performanceSpikeRate),
        spikeRate?.history ?? const <double>[],
      ),
    ];

    return ReactorPage(
      title: reactorText(ReactorText.performanceTitle),
      subtitle: reactorText(ReactorText.performanceSubtitle),
      children: <Widget>[
        sectionCard(
          label: reactorText(ReactorText.performanceTickDuration),
          child: TimeseriesChart(series: tickSeries, height: 180),
        ),
        sectionCard(
          label: reactorText(ReactorText.performanceTickSpikeRate),
          child: TimeseriesChart(series: spikeSeries, height: 80),
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'grid',
              'grid-template-columns': 'repeat(auto-fill, minmax(200px, 1fr))',
              'gap': '1rem',
            },
          ),
          <Widget>[
            StatTile(
              label: reactorText(ReactorText.commonTopWorldMspt),
              sample: worldMspt,
            ),
            StatTile(
              label: reactorText(ReactorText.commonTopChunkCost),
              sample: chunkCost,
            ),
          ],
        ),
      ],
    );
  }
}
