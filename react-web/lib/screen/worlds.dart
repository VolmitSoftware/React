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

class WorldsScreen extends StatelessWidget {
  const WorldsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) {
      return ReactorPage(
        title: reactorText(ReactorText.worldsTitle),
        subtitle: reactorText(ReactorText.worldsSubtitle),
        children: <Widget>[
          ReactorLoadingState(label: reactorText(ReactorText.metricsWaiting)),
        ],
      );
    }

    final SamplerSample? topWorldMspt = snapshot.sampler('top-world-mspt');
    final SamplerSample? perWorldTickTime = snapshot.sampler(
      'per-world-tick-time',
    );
    final SamplerSample? topChunkCost = snapshot.sampler('top-chunk-cost');

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
        SectionPanel(
          label: reactorText(ReactorText.commonTopWorldMspt),
          children: <Widget>[
            TimeseriesChart(series: tickSeries, height: 160),
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
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.worldsBreakdown),
          child: ReactorNotice(
            title: reactorText(ReactorText.worldsBudgetsMoved),
            message: reactorText(ReactorText.worldsBudgetsMovedDescription),
          ),
        ),
      ],
    );
  }
}
