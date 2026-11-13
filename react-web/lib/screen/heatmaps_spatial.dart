/// The map's spatial samplers (Advanced): one tile per map sampler the
/// server reports.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/section.dart';
import '../ui/kit/states.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

const List<(String, ReactorText)> _kSpatialSamplers = <(String, ReactorText)>[
  ('entity-pressure-heatmap', ReactorText.heatmapsEntityPressure),
  ('chunk-load-gen-cost-map', ReactorText.heatmapsChunkLoadGenCost),
  ('chunk-sampler-map', ReactorText.heatmapsChunkSampler),
  ('redstone-activity-heatmap', ReactorText.heatmapsRedstoneActivity),
  ('hopper-container-throughput-map', ReactorText.heatmapsHopperThroughput),
  ('tick-spike-origin-replay-map', ReactorText.heatmapsTickSpikeOrigin),
  ('plugin-event-impact-pie-map', ReactorText.heatmapsEventImpactPie),
  ('plugin-event-impact-list-map', ReactorText.heatmapsEventImpactList),
  ('iris-biome-chunk-share-pie-map', ReactorText.heatmapsIrisBiomeShare),
  ('iris-world-chunk-share-pie-map', ReactorText.heatmapsIrisWorldShare),
];

class SpatialMetrics extends StatelessWidget {
  const SpatialMetrics({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final List<Widget> tiles = <Widget>[
      for (final (String id, ReactorText label) in _kSpatialSamplers)
        if (snapshot?.sampler(id) case final SamplerSample sample)
          samplerTile(reactorText(label), sample),
    ];
    return PageSection(
      title: reactorText(ReactorText.heatmapsSpatialMetrics),
      children: <Widget>[
        if (tiles.isNotEmpty)
          MetricGrid(children: tiles)
        else if (snapshot == null)
          serverSnapshotState(scope: scope, icon: ArcaneIcon.map())
        else
          EmptyState(
            message: reactorText(ReactorText.heatmapsNoSpatialMetrics),
            icon: ArcaneIcon.map(),
          ),
      ],
    );
  }
}
