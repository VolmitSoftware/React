/// Activity > Entities: entity load, players and their ping, then the
/// per-kind breakdown and player traffic.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/card.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

const List<String> kEntityBreakdownIds = <String>[
  'entities-animals',
  'entities-hostile',
  'villagers',
  'ground-items',
  'projectiles',
  'block-entities',
  'block-entities-ticking',
  'physics-entities',
  'spawner-spawns',
];

const List<String> kPlayerActivityIds = <String>[
  'player-joins-rate',
  'player-quits-rate',
  'players-unique-24h',
];

class EntitiesScreen extends StatelessWidget {
  const EntitiesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final SamplerSample? entities = snapshot.sampler('entities');
    final SamplerSample? pingP95 = snapshot.sampler('player-ping-p95');
    final SamplerSample? jitter = snapshot.sampler('ping-jitter');
    final List<SamplerSample> breakdown = samplersOf(
      snapshot,
      kEntityBreakdownIds,
    );
    final List<SamplerSample> playerActivity = samplersOf(
      snapshot,
      kPlayerActivityIds,
    );
    final String entitiesLabel = reactorText(ReactorText.commonEntities);
    final String pingLabel = reactorText(ReactorText.commonPingP95);

    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            samplerTile(entitiesLabel, entities),
            samplerTile(
              reactorText(ReactorText.entitiesAiActive),
              snapshot.sampler('entity-ai-active-count'),
            ),
            samplerTile(
              reactorText(ReactorText.entitiesSpawnsPerSecond),
              snapshot.sampler('entities-spawns'),
            ),
            samplerTile(
              reactorText(ReactorText.commonPlayers),
              snapshot.sampler('players'),
            ),
            samplerTile(pingLabel, pingP95),
            samplerTile(reactorText(ReactorText.entitiesPingJitter), jitter),
          ],
        ),
        ChartCard(
          title: reactorText(ReactorText.entitiesCount),
          child: TimeseriesChart(
            series: <(String, List<double>)>[
              (entitiesLabel, entities?.history ?? const <double>[]),
            ],
          ),
        ),
        ChartCard(
          title: reactorText(ReactorText.entitiesPlayerPing),
          child: TimeseriesChart(
            height: 120,
            unit: 'ms',
            series: <(String, List<double>)>[
              (pingLabel, pingP95?.history ?? const <double>[]),
            ],
          ),
        ),
        if (breakdown.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.entitiesBreakdown),
            children: <Widget>[MetricGrid(children: namedTiles(breakdown))],
          ),
        if (playerActivity.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.entitiesPlayerActivity),
            children: <Widget>[
              MetricGrid(children: namedTiles(playerActivity)),
            ],
          ),
      ],
    );
  }
}
