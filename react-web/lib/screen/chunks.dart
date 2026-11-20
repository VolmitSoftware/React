/// Activity > Chunks: loading and generation, the time listeners spend on
/// them, persistence, what keeps chunks resident and, on a modded server,
/// which mods and chunk steps world generation spends its time on.
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

/// Per-owner forced chunk counts: one sampler per plugin or mod.
const List<String> kChunkLoaderPrefixes = <String>['chunk-loader-'];

const List<String> kWorldgenStepIds = <String>[
  'worldgen-step-structure-starts',
  'worldgen-step-biomes',
  'worldgen-step-noise',
  'worldgen-step-surface',
  'worldgen-step-carvers',
  'worldgen-step-features',
];

const String _kWorldgenModPrefix = 'worldgen-mod-';
const int _kWorldgenTopMods = 8;

/// The mods spending the most world generation time, busiest first.
List<SamplerSample> topWorldgenMods(ServerSnapshot snapshot) {
  final List<SamplerSample> mods = <SamplerSample>[
    for (final SamplerSample sample in snapshot.byId.values)
      if (sample.id.startsWith(_kWorldgenModPrefix)) sample,
  ]..sort((SamplerSample a, SamplerSample b) => b.value.compareTo(a.value));
  return mods.take(_kWorldgenTopMods).toList();
}

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
    final List<SamplerSample> loaders = samplersWithPrefix(
      snapshot,
      kChunkLoaderPrefixes,
    );
    final List<SamplerSample> worldgenMods = topWorldgenMods(snapshot);
    final List<SamplerSample> worldgenSteps = samplersOf(
      snapshot,
      kWorldgenStepIds,
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
        if (loaders.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.chunksLoaders),
            children: <Widget>[MetricGrid(children: namedTiles(loaders))],
          ),
        if (worldgenMods.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.chunksWorldgenByMod),
            children: <Widget>[MetricGrid(children: namedTiles(worldgenMods))],
          ),
        if (worldgenSteps.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.chunksWorldgenSteps),
            children: <Widget>[MetricGrid(children: namedTiles(worldgenSteps))],
          ),
      ],
    );
  }
}
