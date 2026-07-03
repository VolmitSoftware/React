library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
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
    final SamplerSample? chunksLoaded =
        scope?.snapshot?.sampler('chunks-loaded');
    final SamplerSample? chunksGenerated =
        scope?.snapshot?.sampler('chunks-generated');
    final SamplerSample? chunkLoadMs =
        scope?.snapshot?.sampler('chunk-load-ms');
    final SamplerSample? chunkGenMs =
        scope?.snapshot?.sampler('chunk-gen-ms');
    final SamplerSample? worldSaveDuration =
        scope?.snapshot?.sampler('world-save-duration');
    final SamplerSample? pdcWriteBatcher =
        scope?.snapshot?.sampler('pdc-write-batcher');

    final List<(String, List<double>)> timeSeries =
        <(String, List<double>)>[
      ('Load ms', chunkLoadMs?.history ?? const <double>[]),
      ('Gen ms', chunkGenMs?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Chunks',
      subtitle: 'Chunk loading and persistence',
      children: <Widget>[
        sectionCard(
          label: 'Chunk Load/Gen Time',
          child: TimeseriesChart(series: timeSeries, height: 160),
        ),
        statGrid(<Widget>[
          StatTile(label: 'Chunks', sample: chunks),
          StatTile(label: 'Loaded/s', sample: chunksLoaded),
          StatTile(label: 'Generated/s', sample: chunksGenerated),
          StatTile(label: 'Load Time', sample: chunkLoadMs),
          StatTile(label: 'Gen Time', sample: chunkGenMs),
        ]),
        sectionCard(
          label: 'Persistence',
          child: statGrid(<Widget>[
            StatTile(label: 'World Save', sample: worldSaveDuration),
            StatTile(label: 'PDC Batcher', sample: pdcWriteBatcher),
          ]),
        ),
      ],
    );
  }
}
