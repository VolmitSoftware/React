library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
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
    final SamplerSample? topWorldMspt = scope?.snapshot?.sampler(
      'top-world-mspt',
    );
    final SamplerSample? perWorldTickTime = scope?.snapshot?.sampler(
      'per-world-tick-time',
    );
    final SamplerSample? topChunkCost = scope?.snapshot?.sampler(
      'top-chunk-cost',
    );

    final List<(String, List<double>)> tickSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.commonTopWorldMspt),
        topWorldMspt?.history ?? const <double>[],
      ),
      (
        reactorText(ReactorText.worldsPerWorldTick),
        perWorldTickTime?.history ?? const <double>[],
      ),
    ];

    return ReactorPage(
      title: reactorText(ReactorText.worldsTitle),
      subtitle: reactorText(ReactorText.worldsSubtitle),
      children: <Widget>[
        sectionCard(
          label: reactorText(ReactorText.commonTopWorldMspt),
          child: TimeseriesChart(series: tickSeries, height: 160),
        ),
        statGrid(<Widget>[
          StatTile(
            label: reactorText(ReactorText.commonTopWorldMspt),
            sample: topWorldMspt,
          ),
          StatTile(
            label: reactorText(ReactorText.worldsPerWorldTickTime),
            sample: perWorldTickTime,
          ),
          StatTile(
            label: reactorText(ReactorText.commonTopChunkCost),
            sample: topChunkCost,
          ),
        ]),
        sectionCard(
          label: reactorText(ReactorText.worldsBreakdown),
          child: ArcaneEmptyState.noData(
            title: reactorText(ReactorText.worldsBudgetsMoved),
            description: reactorText(ReactorText.worldsBudgetsMovedDescription),
          ),
        ),
      ],
    );
  }
}
