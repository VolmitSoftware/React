library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/heatmap.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/heatmap_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/heatmap_grid_view.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

const List<(String, String)> _kSpatialSamplers = <(String, String)>[
  ('entity-pressure-heatmap', 'Entity Pressure'),
  ('chunk-load-gen-cost-map', 'Chunk Load/Gen Cost'),
  ('chunk-sampler-map', 'Chunk Sampler'),
  ('redstone-activity-heatmap', 'Redstone Activity'),
  ('hopper-container-throughput-map', 'Hopper Throughput'),
  ('tick-spike-origin-replay-map', 'Tick-Spike Origin'),
  ('plugin-event-impact-pie-map', 'Event Impact (pie)'),
  ('plugin-event-impact-list-map', 'Event Impact (list)'),
  ('iris-biome-chunk-share-pie-map', 'Iris Biome Share'),
  ('iris-world-chunk-share-pie-map', 'Iris World Share'),
];

Future<HeatmapGrid?> _safeFetch(IHeatmapClient client, String id) async {
  try {
    return await client.heatmap(id);
  } on ReactUnavailable {
    return null;
  }
}

Future<List<HeatmapGrid>> loadHeatmapGrids(IHeatmapClient client) async {
  final List<HeatmapSummary> summaries = await client.heatmaps();
  final List<HeatmapGrid> grids = <HeatmapGrid>[];
  for (final HeatmapSummary s in summaries) {
    final HeatmapGrid? g = await _safeFetch(client, s.id);
    if (g != null) grids.add(g);
  }
  return grids;
}

class HeatmapsScreen extends StatefulWidget {
  const HeatmapsScreen({super.key});

  @override
  State<HeatmapsScreen> createState() => _HeatmapsScreenState();
}

class _HeatmapsScreenState extends State<HeatmapsScreen> {
  IHeatmapClient? _client;
  List<HeatmapGrid>? _grids;
  bool _loading = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IHeatmapClient? c = HeatmapScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _loading = true;
      loadHeatmapGrids(c).then((List<HeatmapGrid> g) {
        if (!mounted) return;
        setState(() {
          _grids = g;
          _loading = false;
        });
      }).catchError((Object e) {
        if (!mounted) return;
        setState(() {
          _grids = const <HeatmapGrid>[];
          _loading = false;
        });
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;

    final List<Widget> tiles = <Widget>[];
    for (final (String id, String label) in _kSpatialSamplers) {
      final SamplerSample? s = snapshot?.sampler(id);
      if (s != null) {
        tiles.add(StatTile(label: label, sample: s));
      }
    }

    return ReactorPage(
      title: 'Heatmaps',
      subtitle: 'Spatial load distribution',
      children: <Widget>[
        if (tiles.isNotEmpty)
          sectionCard(
            label: 'Spatial Metrics',
            child: statGrid(tiles),
          ),
        if (_loading)
          sectionCard(
            label: 'Chunk Heatmaps',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[Component.text('Loading heatmaps...')],
            ),
          ),
        if (!_loading && _grids != null && _grids!.isNotEmpty)
          sectionCard(
            label: 'Chunk Heatmaps',
            child: Collection(
              gap: 12,
              children: <Widget>[
                for (final HeatmapGrid g in _grids!)
                  HeatmapGridView(grid: g),
              ],
            ),
          ),
        if (!_loading && _client == null)
          sectionCard(
            label: 'Chunk Heatmaps',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[
                Component.text(
                  'Grid heatmaps require a live connection.',
                ),
              ],
            ),
          ),
      ],
    );
  }
}
