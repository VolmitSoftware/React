/// Performance > Ticks: the one tick time chart (with its percentiles), the
/// spike rate and the costliest world and chunk.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show ListenableBuilder;

import '../chart/range_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/sampler_catalog.dart';
import '../state/server_scope.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

class PerformanceScreen extends StatelessWidget {
  const PerformanceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final SamplerSample? tickTime = snapshot.sampler('tick-time');
    final SamplerSample? spikeRate = snapshot.sampler('tick-spike-rate');
    final String tickLabel = reactorText(ReactorText.commonTickTime);
    final String spikeLabel = reactorText(ReactorText.performanceSpikeRate);
    final SamplerCatalog? catalog = ServerSessionScope.of(context)?.catalog;
    if (catalog == null) {
      return _page(
        context,
        snapshot,
        tickTime,
        spikeRate,
        tickLabel,
        spikeLabel,
        null,
      );
    }
    return ListenableBuilder(
      listenable: catalog,
      builder: (BuildContext context) => _page(
        context,
        snapshot,
        tickTime,
        spikeRate,
        tickLabel,
        spikeLabel,
        catalog,
      ),
    );
  }

  Widget _page(
    BuildContext context,
    ServerSnapshot snapshot,
    SamplerSample? tickTime,
    SamplerSample? spikeRate,
    String tickLabel,
    String spikeLabel,
    SamplerCatalog? catalog,
  ) {
    // A server without tick time (Folia) gets no chart that waits for it.
    final bool noTicks =
        tickTime == null && (catalog?.notReported('tick-time') ?? false);
    final bool advanced = ModeScope.advanced(context);
    return PageStack(
      children: <Widget>[
        // Simple: TPS and tick time. Advanced: tick time and what drives
        // it (spikes, the slowest world, the costliest chunk).
        MetricGrid(
          children: <Widget>[
            if (!advanced)
              samplerTile(
                reactorText(ReactorText.overviewTps),
                snapshot.sampler('ticks-per-second'),
              ),
            samplerTile(tickLabel, tickTime, notReported: noTicks),
            if (advanced) ...<Widget>[
              samplerTile(spikeLabel, spikeRate),
              samplerTile(
                reactorText(ReactorText.commonTopWorldMspt),
                snapshot.sampler('top-world-mspt'),
              ),
              samplerTile(
                reactorText(ReactorText.commonTopChunkCost),
                snapshot.sampler('top-chunk-cost'),
              ),
            ],
          ],
        ),
        // The tick time over time; Advanced adds its percentiles.
        if (!noTicks)
          RangeChartCard(
            title: tickLabel,
            height: 200,
            unit: 'ms',
            series: <RangeSeries>[
              RangeSeries(tickLabel, 'tick-time', tickTime),
              if (advanced)
                for (final String percentile in const <String>[
                  'p50',
                  'p95',
                  'p99',
                ])
                  if (snapshot.sampler('tick-ms-$percentile')
                      case final SamplerSample sample)
                    RangeSeries(percentile, 'tick-ms-$percentile', sample),
            ],
          ),
      ],
    );
  }
}
