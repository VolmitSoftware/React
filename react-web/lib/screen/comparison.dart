/// Server group > Compare: one metric across the live servers, best first
/// ([MetricPolarity]): where lower is better (tick time, memory, incident
/// score, GC, ping) the lowest value ranks first and the highest is flagged.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/metric_polarity.dart';
import '../model/reading.dart';
import '../model/sampler_sample.dart';
import '../model/units.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/states.dart';
import 'form_parts.dart';

/// A metric the servers can be compared on, by its sampler id.
typedef ComparableMetric = ({String id, ReactorText label});

const List<ComparableMetric> comparableMetrics = <ComparableMetric>[
  (id: 'ticks-per-second', label: ReactorText.overviewTps),
  (id: 'tick-time', label: ReactorText.commonTickTime),
  (id: 'players', label: ReactorText.commonPlayers),
  (id: 'entities', label: ReactorText.commonEntities),
  (id: 'chunks', label: ReactorText.commonChunks),
  (id: 'memory-used', label: ReactorText.commonMemoryUsed),
  (id: 'incident-score', label: ReactorText.commonIncidentScore),
  (id: 'gc-time-percent', label: ReactorText.commonGcTime),
  (id: 'player-ping-p95', label: ReactorText.commonPingP95),
];

/// One server's reading of the picked metric.
typedef MetricReading = ({String id, String name, SamplerSample sample});

/// The readings of [metricId] on [servers], one per server: an entry paired
/// twice (two tokens) is read once.
List<MetricReading> readMetric(List<FleetServerLive> servers, String metricId) {
  final Set<String> seen = <String>{};
  return <MetricReading>[
    for (final FleetServerLive server in servers)
      if (currentFleetSnapshot(server)?.sampler(metricId)
          case final SamplerSample sample when seen.add(server.serverKey))
        (id: server.id, name: server.name, sample: sample),
  ];
}

/// [readings] of [metricId] best first: lowest first where lower is better.
/// Servers that read the same on screen ("20" and "20") are a tie and stay
/// in name order, so the list does not reshuffle on noise.
List<MetricReading> rankReadings(
  List<MetricReading> readings,
  String metricId,
) => List<MetricReading>.of(readings)
  ..sort((MetricReading a, MetricReading b) {
    if (readingOf(a.sample).text == readingOf(b.sample).text) {
      return a.name.compareTo(b.name);
    }
    final int byValue = MetricPolarity.compareBestFirst(
      metricId,
      a.sample.value,
      b.sample.value,
    );
    return byValue != 0 ? byValue : a.name.compareTo(b.name);
  });

/// The rank of each of [ranked] (from [rankReadings]): a tie shares the
/// rank, and the next one skips past it (1, 1, 3).
List<int> competitionRanks(List<MetricReading> ranked) {
  final List<int> ranks = <int>[];
  for (int index = 0; index < ranked.length; index++) {
    final bool tie =
        index > 0 &&
        readingOf(ranked[index].sample).text ==
            readingOf(ranked[index - 1].sample).text;
    ranks.add(tie ? ranks.last : index + 1);
  }
  return ranks;
}

class ComparisonScreen extends StatefulWidget {
  const ComparisonScreen({super.key});

  @override
  State<ComparisonScreen> createState() => _ComparisonScreenState();
}

class _ComparisonScreenState extends State<ComparisonScreen> {
  static const String _metricSelectId = 'compare-metric';

  String _metricId = comparableMetrics.first.id;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<FleetServerLive> servers =
        FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[];
    final ComparableMetric metric = comparableMetrics.firstWhere(
      (ComparableMetric each) => each.id == _metricId,
    );
    final List<MetricReading> readings = readMetric(servers, metric.id);
    final List<MetricReading> ranked = rankReadings(readings, metric.id);
    final List<int> ranks = competitionRanks(ranked);
    final MetricPolarity polarity = MetricPolarity.of(metric.id);

    final String metricName = reactorText(metric.label);
    // The unit goes in the header when every reading shares it; sizes on
    // servers of different scale ("812 MB", "3.0 GB") keep theirs.
    final Set<String> units = <String>{
      for (final MetricReading reading in ranked)
        readingOf(reading.sample).unit,
    };
    final String? unit = units.length == 1
        ? unitBeside(metricName, units.single)
        : null;
    final Widget picker = FilterSelect(
      id: _metricSelectId,
      label: reactorText(ReactorText.comparisonMetric),
      value: metric.id,
      options: <ArcaneSelectOption>[
        for (final ComparableMetric each in comparableMetrics)
          ArcaneSelectOption(label: reactorText(each.label), value: each.id),
      ],
      onChanged: (String id) => setState(() => _metricId = id),
    );

    // One card: the metric to compare in its head, the servers ranked
    // under it (the place as a number; the unit once, in the header).
    return AppCard(
      title: reactorText(ReactorText.comparisonLeaderboard),
      tip: switch (polarity) {
        MetricPolarity.lowerIsBetter => reactorText(
          ReactorText.comparisonLowerBetter,
        ),
        MetricPolarity.higherIsBetter => reactorText(
          ReactorText.comparisonHigherBetter,
        ),
        MetricPolarity.neutral => null,
      },
      actions: ActionRow(
        dense: true,
        label: reactorText(ReactorText.comparisonMetric),
        leading: <Widget>[picker],
      ),
      children: <Widget>[
        if (servers.isEmpty)
          EmptyState(message: reactorText(ReactorText.fleetNoServersPaired))
        else if (ranked.isEmpty)
          EmptyState(message: reactorText(ReactorText.comparisonNoData))
        else
          DataList<MetricReading>(
            label: reactorText(ReactorText.comparisonLeaderboard),
            items: ranked,
            href: (MetricReading reading) => serverPath(reading.id),
            columns: <ListColumn<MetricReading>>[
              ListColumn<MetricReading>.text(
                reactorText(ReactorText.commonServer),
                (MetricReading reading) => reading.name,
                width: 'minmax(0, 2fr)',
              ),
              if (ranked.length > 1 && polarity != MetricPolarity.neutral)
                ListColumn<MetricReading>(
                  reactorText(ReactorText.comparisonRank),
                  (MetricReading reading) =>
                      Component.text('${ranks[ranked.indexOf(reading)]}'),
                  numeric: true,
                  width: '4rem',
                ),
              ListColumn<MetricReading>(
                unit == null ? metricName : '$metricName ($unit)',
                (MetricReading reading) {
                  final Reading shown = readingOf(reading.sample);
                  return Component.text(
                    units.length == 1 ? shown.value : shown.text,
                  );
                },
                numeric: true,
                width: 'minmax(0, 1fr)',
              ),
            ],
          ),
      ],
    );
  }
}
