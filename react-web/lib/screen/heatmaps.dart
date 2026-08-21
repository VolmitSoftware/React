library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../model/heatmap.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/heatmap_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/heatmap_grid_view.dart';
import '../widget/section_card.dart' show statGrid;
import '../widget/stat_tile.dart';

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
  Object? _error;
  bool _loading = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IHeatmapClient? client = HeatmapScope.of(context)?.client;
    if (client != null && client != _client) {
      _client = client;
      _load(client);
    }
  }

  Future<void> _load(IHeatmapClient client) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final List<HeatmapGrid> grids = await loadHeatmapGrids(client);
      if (!mounted || client != _client) return;
      setState(() {
        _grids = grids;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || client != _client) return;
      setState(() {
        _error = error;
        _grids = null;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final List<Widget> tiles = <Widget>[];
    for (final (String id, ReactorText label) in _kSpatialSamplers) {
      final SamplerSample? s = snapshot?.sampler(id);
      if (s != null) {
        tiles.add(StatTile(label: reactorText(label), sample: s));
      }
    }

    return ReactorPage(
      title: reactorText(ReactorText.heatmapsTitle),
      subtitle: reactorText(ReactorText.heatmapsSubtitle),
      children: <Widget>[
        if (tiles.isNotEmpty)
          SectionPanel(
            label: reactorText(ReactorText.heatmapsSpatialMetrics),
            flush: true,
            child: statGrid(tiles),
          ),
        if (tiles.isEmpty)
          SectionPanel(
            label: reactorText(ReactorText.heatmapsSpatialMetrics),
            child: snapshot == null
                ? ReactorLoadingState(
                    label: reactorText(ReactorText.metricsWaiting),
                  )
                : ReactorEmptyState(
                    title: 'No spatial metrics',
                    description:
                        'This snapshot contains no spatial sampler output.',
                    icon: ArcaneIcon.map(size: IconSize.sm),
                  ),
          ),
        SectionPanel(
          label: reactorText(ReactorText.heatmapsChunkHeatmaps),
          flush: true,
          child: _chunkHeatmapState(),
        ),
      ],
    );
  }

  Widget _chunkHeatmapState() {
    final IHeatmapClient? client = _client;
    if (client == null) {
      return ReactorNotice(
        title: 'Heatmap endpoint unavailable',
        message: reactorText(ReactorText.heatmapsLiveRequired),
        status: ReactorStatus.critical,
      );
    }
    if (_loading) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.heatmapsLoading),
      );
    }
    final Object? error = _error;
    if (error != null) {
      return ReactorNotice(
        title: 'Heatmap request failed',
        message: error.toString(),
        status: ReactorStatus.critical,
        action: Button.secondary(
          label: 'Retry',
          size: ButtonSize.small,
          onPressed: () => _load(client),
        ),
      );
    }
    final List<HeatmapGrid>? grids = _grids;
    if (grids == null) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.heatmapsLoading),
      );
    }
    if (grids.isEmpty) {
      return ReactorEmptyState(
        title: 'No chunk heatmaps',
        description: 'React did not publish any chunk heatmap grids.',
        icon: ArcaneIcon.grid3x3(size: IconSize.sm),
      );
    }
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'display': 'flex', 'flex-direction': 'column'},
      ),
      <Widget>[
        for (final HeatmapGrid grid in grids) HeatmapGridView(grid: grid),
      ],
    );
  }
}
