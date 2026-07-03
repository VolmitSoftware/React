library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class WorldsScreen extends StatelessWidget {
  const WorldsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? topWorldMspt =
        scope?.snapshot?.sampler('top-world-mspt');
    final SamplerSample? perWorldTickTime =
        scope?.snapshot?.sampler('per-world-tick-time');
    final SamplerSample? topChunkCost =
        scope?.snapshot?.sampler('top-chunk-cost');

    final List<(String, List<double>)> tickSeries =
        <(String, List<double>)>[
      ('Top World MSPT', topWorldMspt?.history ?? const <double>[]),
      ('Per-World Tick', perWorldTickTime?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Worlds',
      subtitle: 'Per-world performance',
      children: <Widget>[
        sectionCard(
          label: 'Top World MSPT',
          child: TimeseriesChart(series: tickSeries, height: 160),
        ),
        statGrid(<Widget>[
          StatTile(label: 'Top World MSPT', sample: topWorldMspt),
          StatTile(label: 'Per-World Tick Time', sample: perWorldTickTime),
          StatTile(label: 'Top Chunk Cost', sample: topChunkCost),
        ]),
        sectionCard(
          label: 'Per-World Breakdown',
          child: ArcaneEmptyState.noData(
            title: 'Per-world tick budgets moved to World Overrides',
            description:
                'Open the World Overrides screen to view per-world '
                'NORMAL/PRESSURE/PANIC state and edit tick budgets.',
          ),
        ),
      ],
    );
  }
}
