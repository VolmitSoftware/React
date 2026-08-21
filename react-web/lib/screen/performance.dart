library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class PerformanceScreen extends StatelessWidget {
  const PerformanceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) {
      return ReactorPage(
        title: reactorText(ReactorText.performanceTitle),
        subtitle: reactorText(ReactorText.performanceSubtitle),
        children: <Widget>[
          ReactorLoadingState(label: reactorText(ReactorText.metricsWaiting)),
        ],
      );
    }

    final SamplerSample? tickTime = snapshot.sampler('tick-time');
    final SamplerSample? p50 = snapshot.sampler('tick-ms-p50');
    final SamplerSample? p95 = snapshot.sampler('tick-ms-p95');
    final SamplerSample? p99 = snapshot.sampler('tick-ms-p99');
    final SamplerSample? spikeRate = snapshot.sampler('tick-spike-rate');
    final SamplerSample? worldMspt = snapshot.sampler('top-world-mspt');
    final SamplerSample? chunkCost = snapshot.sampler('top-chunk-cost');

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
        SectionPanel(
          label: reactorText(ReactorText.performanceTickDuration),
          child: TimeseriesChart(series: tickSeries, height: 180),
        ),
        SectionPanel(
          label: reactorText(ReactorText.performanceTickSpikeRate),
          children: <Widget>[
            TimeseriesChart(series: spikeSeries, height: 96),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.performanceSpikeRate),
                sample: spikeRate,
              ),
              StatTile(
                label: reactorText(ReactorText.commonTopWorldMspt),
                sample: worldMspt,
              ),
              StatTile(
                label: reactorText(ReactorText.commonTopChunkCost),
                sample: chunkCost,
              ),
            ]),
          ],
        ),
      ],
    );
  }
}
