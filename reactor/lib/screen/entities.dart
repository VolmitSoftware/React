library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
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
    final SamplerSample? entityAiActive =
        scope?.snapshot?.sampler('entity-ai-active-count');
    final SamplerSample? entitiesSpawns =
        scope?.snapshot?.sampler('entities-spawns');
    final SamplerSample? players = scope?.snapshot?.sampler('players');
    final SamplerSample? pingP95 =
        scope?.snapshot?.sampler('player-ping-p95');
    final SamplerSample? pingJitter =
        scope?.snapshot?.sampler('ping-jitter');

    final List<(String, List<double>)> entityCountSeries =
        <(String, List<double>)>[
      ('Entities', entities?.history ?? const <double>[]),
    ];

    final List<(String, List<double>)> pingSeriesBuilder =
        <(String, List<double>)>[
      ('Ping p95', pingP95?.history ?? const <double>[]),
      if (pingJitter != null) ('Jitter', pingJitter.history),
    ];

    return ReactorPage(
      title: 'Entities',
      subtitle: 'Entity counts and AI load',
      children: <Widget>[
        sectionCard(
          label: 'Entity Count',
          child: TimeseriesChart(series: entityCountSeries, height: 160),
        ),
        sectionCard(
          label: 'Player Ping',
          child: TimeseriesChart(series: pingSeriesBuilder, height: 120),
        ),
        statGrid(<Widget>[
          StatTile(label: 'Players', sample: players),
          StatTile(label: 'Entities', sample: entities),
          StatTile(label: 'AI Active', sample: entityAiActive),
          StatTile(label: 'Spawns/s', sample: entitiesSpawns),
          StatTile(label: 'Ping p95', sample: pingP95),
          StatTile(label: 'Ping Jitter', sample: pingJitter),
        ]),
      ],
    );
  }
}
