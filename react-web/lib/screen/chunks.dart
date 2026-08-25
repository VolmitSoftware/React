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

const List<String> _kChunkResidencyIds = <String>[
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
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.chunksTitle),
      subtitle: reactorText(ReactorText.chunksSubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? chunks = snapshot.sampler('chunks');
    final SamplerSample? chunksLoaded = snapshot.sampler('chunks-loaded');
    final SamplerSample? chunksGenerated = snapshot.sampler('chunks-generated');
    final SamplerSample? chunkLoadMs = snapshot.sampler('chunk-load-ms');
    final SamplerSample? chunkGenMs = snapshot.sampler('chunk-gen-ms');
    final SamplerSample? worldSaveEventInterval = snapshot.sampler(
      'world-save-event-interval',
    );
    final SamplerSample? pdcWriteBatcher = snapshot.sampler(
      'pdc-write-batcher',
    );
    final List<SamplerSample> residency = _kChunkResidencyIds
        .map(snapshot.sampler)
        .whereType<SamplerSample>()
        .toList(growable: false);

    final List<(String, List<double>)> timeSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.chunksLoadMs),
        chunkLoadMs?.history ?? const <double>[],
      ),
      (
        reactorText(ReactorText.chunksGenMs),
        chunkGenMs?.history ?? const <double>[],
      ),
    ];

    return ReactorPage(
      title: reactorText(ReactorText.chunksTitle),
      subtitle: reactorText(ReactorText.chunksSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.chunksLoadGenTime),
          children: <Widget>[
            TimeseriesChart(series: timeSeries, height: 160),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.commonChunks),
                sample: chunks,
              ),
              StatTile(
                label: reactorText(ReactorText.chunksLoadedPerSecond),
                sample: chunksLoaded,
              ),
              StatTile(
                label: reactorText(ReactorText.chunksGeneratedPerSecond),
                sample: chunksGenerated,
              ),
              StatTile(
                label: reactorText(ReactorText.chunksLoadTime),
                sample: chunkLoadMs,
              ),
              StatTile(
                label: reactorText(ReactorText.chunksGenTime),
                sample: chunkGenMs,
              ),
            ]),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.chunksPersistence),
          flush: true,
          child: statGrid(<Widget>[
            StatTile(
              label:
                  worldSaveEventInterval?.name ??
                  reactorText(ReactorText.chunksWorldSaveEventInterval),
              sample: worldSaveEventInterval,
            ),
            StatTile(
              label: reactorText(ReactorText.chunksPdcBatcher),
              sample: pdcWriteBatcher,
            ),
          ]),
        ),
        if (residency.isNotEmpty)
          SectionPanel(
            label: reactorText(ReactorText.chunksResidency),
            flush: true,
            child: statGrid(
              residency
                  .map((SamplerSample sample) {
                    return StatTile(label: sample.name, sample: sample);
                  })
                  .toList(growable: false),
            ),
          ),
      ],
    );
  }
}
