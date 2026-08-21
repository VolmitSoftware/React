library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class ChunksScreen extends StatelessWidget {
  const ChunksScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? chunks = scope?.snapshot?.sampler('chunks');
    final SamplerSample? chunksLoaded = scope?.snapshot?.sampler(
      'chunks-loaded',
    );
    final SamplerSample? chunksGenerated = scope?.snapshot?.sampler(
      'chunks-generated',
    );
    final SamplerSample? chunkLoadMs = scope?.snapshot?.sampler(
      'chunk-load-ms',
    );
    final SamplerSample? chunkGenMs = scope?.snapshot?.sampler('chunk-gen-ms');
    final SamplerSample? worldSaveDuration = scope?.snapshot?.sampler(
      'world-save-duration',
    );
    final SamplerSample? pdcWriteBatcher = scope?.snapshot?.sampler(
      'pdc-write-batcher',
    );

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
        sectionCard(
          label: reactorText(ReactorText.chunksLoadGenTime),
          child: TimeseriesChart(series: timeSeries, height: 160),
        ),
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
        sectionCard(
          label: reactorText(ReactorText.chunksPersistence),
          child: statGrid(<Widget>[
            StatTile(
              label: reactorText(ReactorText.chunksWorldSave),
              sample: worldSaveDuration,
            ),
            StatTile(
              label: reactorText(ReactorText.chunksPdcBatcher),
              sample: pdcWriteBatcher,
            ),
          ]),
        ),
      ],
    );
  }
}
