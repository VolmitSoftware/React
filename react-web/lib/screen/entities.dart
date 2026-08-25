library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/server_snapshot_state.dart';
import '../widget/stat_tile.dart';

const List<String> _kEntityBreakdownIds = <String>[
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

const List<String> _kPlayerActivityIds = <String>[
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
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.entitiesTitle),
      subtitle: reactorText(ReactorText.entitiesSubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? entities = snapshot.sampler('entities');
    final SamplerSample? entityAiActive = snapshot.sampler(
      'entity-ai-active-count',
    );
    final SamplerSample? entitiesSpawns = snapshot.sampler('entities-spawns');
    final SamplerSample? players = snapshot.sampler('players');
    final SamplerSample? pingP95 = snapshot.sampler('player-ping-p95');
    final SamplerSample? pingJitter = snapshot.sampler('ping-jitter');
    final List<SamplerSample> entityBreakdown = _samples(
      snapshot,
      _kEntityBreakdownIds,
    );
    final List<SamplerSample> playerActivity = _samples(
      snapshot,
      _kPlayerActivityIds,
    );

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
        SectionPanel(
          label: reactorText(ReactorText.entitiesCount),
          children: <Widget>[
            TimeseriesChart(series: entityCountSeries, height: 160),
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
            ]),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.entitiesPlayerPing),
          children: <Widget>[
            TimeseriesChart(series: pingSeriesBuilder, height: 120),
            statGrid(<Widget>[
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
        ),
        if (entityBreakdown.isNotEmpty)
          _sampleSection(
            reactorText(ReactorText.entitiesBreakdown),
            entityBreakdown,
          ),
        if (playerActivity.isNotEmpty)
          _sampleSection(
            reactorText(ReactorText.entitiesPlayerActivity),
            playerActivity,
          ),
      ],
    );
  }

  List<SamplerSample> _samples(ServerSnapshot snapshot, List<String> ids) {
    return ids
        .map(snapshot.sampler)
        .whereType<SamplerSample>()
        .toList(growable: false);
  }

  Widget _sampleSection(String label, List<SamplerSample> samples) {
    return SectionPanel(
      label: label,
      flush: true,
      child: statGrid(
        samples
            .map((SamplerSample sample) {
              return StatTile(label: sample.name, sample: sample);
            })
            .toList(growable: false),
      ),
    );
  }
}
