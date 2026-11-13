/// Activity > Chunks: loading and generation, the time listeners spend on
/// them, persistence and what keeps chunks resident.
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

const List<String> kChunkResidencyIds = <String>[
  'chunks-force-loaded',
  'chunk-tickets',
  'chunk-unloads',
  'worlds',
];

class ChunksScreen extends StatelessWidget {
  const ChunksScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final SamplerSample? loadMs = snapshot.sampler('chunk-load-listener-ms');
    final SamplerSample? genMs = snapshot.sampler('chunk-gen-listener-ms');
    final List<SamplerSample> residency = samplersOf(
      snapshot,
      kChunkResidencyIds,
    );

    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            samplerTile(
              reactorText(ReactorText.commonChunks),
              snapshot.sampler('chunks'),
            ),
            samplerTile(
              reactorText(ReactorText.chunksLoadedPerSecond),
              snapshot.sampler('chunks-loaded'),
            ),
            samplerTile(
              reactorText(ReactorText.chunksGeneratedPerSecond),
              snapshot.sampler('chunks-generated'),
            ),
          ],
        ),
        ChartCard(
          title: reactorText(ReactorText.chunksListenerTime),
          child: TimeseriesChart(
            unit: 'ms',
            series: <(String, List<double>)>[
              (
                reactorText(ReactorText.chunksLoadListenerTime),
                loadMs?.history ?? const <double>[],
              ),
              (
                reactorText(ReactorText.chunksGenListenerTime),
                genMs?.history ?? const <double>[],
              ),
            ],
          ),
        ),
        PageSection(
          title: reactorText(ReactorText.chunksPersistence),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                samplerTile(
                  reactorText(ReactorText.chunksWorldSaveEventInterval),
                  snapshot.sampler('world-save-event-interval'),
                ),
                samplerTile(
                  reactorText(ReactorText.chunksPdcBatcher),
                  snapshot.sampler('pdc-write-batcher'),
                ),
              ],
            ),
          ],
        ),
        if (residency.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.chunksResidency),
            children: <Widget>[MetricGrid(children: namedTiles(residency))],
          ),
      ],
    );
  }
}
