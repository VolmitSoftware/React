library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class EntitiesScreen extends StatelessWidget {
  const EntitiesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? entities = scope?.snapshot?.sampler('entities');
    final SamplerSample? entityAiActive = scope?.snapshot?.sampler(
      'entity-ai-active-count',
    );
    final SamplerSample? entitiesSpawns = scope?.snapshot?.sampler(
      'entities-spawns',
    );
    final SamplerSample? players = scope?.snapshot?.sampler('players');
    final SamplerSample? pingP95 = scope?.snapshot?.sampler('player-ping-p95');
    final SamplerSample? pingJitter = scope?.snapshot?.sampler('ping-jitter');

    final List<(String, List<double>)> entityCountSeries =
        <(String, List<double>)>[
          (
            reactorText(ReactorText.commonEntities),
            entities?.history ?? const <double>[],
          ),
        ];

    final List<(String, List<double>)> pingSeriesBuilder =
        <(String, List<double>)>[
          (
            reactorText(ReactorText.commonPingP95),
            pingP95?.history ?? const <double>[],
          ),
          if (pingJitter != null)
            (reactorText(ReactorText.entitiesJitter), pingJitter.history),
        ];

    return ReactorPage(
      title: reactorText(ReactorText.entitiesTitle),
      subtitle: reactorText(ReactorText.entitiesSubtitle),
      children: <Widget>[
        sectionCard(
          label: reactorText(ReactorText.entitiesCount),
          child: TimeseriesChart(series: entityCountSeries, height: 160),
        ),
        sectionCard(
          label: reactorText(ReactorText.entitiesPlayerPing),
          child: TimeseriesChart(series: pingSeriesBuilder, height: 120),
        ),
        statGrid(<Widget>[
          StatTile(
            label: reactorText(ReactorText.commonPlayers),
            sample: players,
          ),
          StatTile(
            label: reactorText(ReactorText.commonEntities),
            sample: entities,
          ),
          StatTile(
            label: reactorText(ReactorText.entitiesAiActive),
            sample: entityAiActive,
          ),
          StatTile(
            label: reactorText(ReactorText.entitiesSpawnsPerSecond),
            sample: entitiesSpawns,
          ),
          StatTile(
            label: reactorText(ReactorText.commonPingP95),
            sample: pingP95,
          ),
          StatTile(
            label: reactorText(ReactorText.entitiesPingJitter),
            sample: pingJitter,
          ),
        ]),
      ],
    );
  }
}
