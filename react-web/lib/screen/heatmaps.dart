library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/heatmap.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/control_scope.dart';
import '../state/heatmap_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/heatmap_grid_view.dart';
import '../widget/section_card.dart' show statGrid;
import '../widget/server_snapshot_state.dart';
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

const Duration heatmapRefreshInterval = Duration(seconds: 10);
const int heatmapMinimumRadius = 1;
const int heatmapMaximumRadius = 16;

final class HeatmapViewport {
  final String world;
  final int centerX;
  final int centerZ;
  final int radius;

  const HeatmapViewport({
    required this.world,
    required this.centerX,
    required this.centerZ,
    required this.radius,
  });
}

typedef HeatmapRefreshOperation = Future<void> Function();

final class HeatmapRefreshController {
  final Duration interval;

  Timer? _timer;
  HeatmapRefreshOperation? _operation;
  bool _pending = false;
  bool _running = false;
  bool _disposed = false;

  HeatmapRefreshController({this.interval = heatmapRefreshInterval}) {
    if (interval <= Duration.zero) {
      throw ArgumentError.value(interval, 'interval', 'Must be positive.');
    }
  }

  void start(HeatmapRefreshOperation operation) {
    if (_disposed) return;
    stop();
    _operation = operation;
    _timer = Timer.periodic(interval, (Timer _) => request());
  }

  void request() {
    if (_disposed || _operation == null) return;
    _pending = true;
    if (_running) return;
    _running = true;
    unawaited(_drain());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
    _operation = null;
    _pending = false;
  }

  void dispose() {
    if (_disposed) return;
    stop();
    _disposed = true;
  }

  Future<void> _drain() async {
    try {
      while (!_disposed && _operation != null && _pending) {
        _pending = false;
        final HeatmapRefreshOperation operation = _operation!;
        await operation();
      }
    } finally {
      _running = false;
      if (!_disposed && _operation != null && _pending) {
        request();
      }
    }
  }
}

Future<HeatmapGrid?> _safeFetch(
  IHeatmapClient client,
  String id,
  HeatmapViewport viewport,
) async {
  try {
    return await client.heatmap(
      id,
      world: viewport.world,
      centerX: viewport.centerX,
      centerZ: viewport.centerZ,
      radius: viewport.radius,
    );
  } on ReactUnavailable {
    return null;
  }
}

Future<List<HeatmapGrid>> loadHeatmapGrids(
  IHeatmapClient client,
  HeatmapViewport viewport,
) async {
  final List<HeatmapSummary> summaries = await client.heatmaps();
  final List<HeatmapGrid?> fetched = await Future.wait(<Future<HeatmapGrid?>>[
    for (final HeatmapSummary summary in summaries)
      _safeFetch(client, summary.id, viewport),
  ]);
  return fetched.whereType<HeatmapGrid>().toList(growable: false);
}

class HeatmapsScreen extends StatefulWidget {
  const HeatmapsScreen({super.key});

  @override
  State<HeatmapsScreen> createState() => _HeatmapsScreenState();
}

class _HeatmapsScreenState extends State<HeatmapsScreen> {
  final HeatmapRefreshController _refreshController =
      HeatmapRefreshController();

  IHeatmapClient? _heatmapClient;
  IControlClient? _controlClient;
  List<WorldSettings>? _worlds;
  Object? _worldsError;
  bool _worldsLoading = false;
  String? _selectedWorldKey;
  String _centerX = '0';
  String _centerZ = '0';
  String _radius = '8';
  bool _coordinatesInvalid = false;
  bool _radiusInvalid = false;
  HeatmapViewport? _appliedViewport;
  List<HeatmapGrid>? _grids;
  Object? _error;
  bool _loading = false;
  int _generation = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IHeatmapClient? heatmapClient = HeatmapScope.of(context)?.client;
    final IControlClient? controlClient = ControlScope.of(context)?.client;
    if (identical(heatmapClient, _heatmapClient) &&
        identical(controlClient, _controlClient)) {
      return;
    }
    _replaceClients(heatmapClient, controlClient);
  }

  @override
  void dispose() {
    _generation++;
    _refreshController.dispose();
    super.dispose();
  }

  void _replaceClients(
    IHeatmapClient? heatmapClient,
    IControlClient? controlClient,
  ) {
    _generation++;
    _refreshController.stop();
    _heatmapClient = heatmapClient;
    _controlClient = controlClient;
    _worlds = null;
    _worldsError = null;
    _worldsLoading = controlClient != null;
    _selectedWorldKey = null;
    _coordinatesInvalid = false;
    _radiusInvalid = false;
    _appliedViewport = null;
    _grids = null;
    _error = null;
    _loading = false;
    if (heatmapClient != null) {
      _refreshController.start(_refreshAppliedViewport);
    }
    if (controlClient != null) {
      unawaited(_loadWorlds(controlClient, _generation));
    }
  }

  Future<void> _loadWorlds(IControlClient client, int generation) async {
    try {
      final List<WorldSettings> loaded = await client.worlds();
      if (!mounted ||
          generation != _generation ||
          !identical(client, _controlClient)) {
        return;
      }
      final List<WorldSettings> worlds = loaded
          .where((WorldSettings world) => world.key.trim().isNotEmpty)
          .toList(growable: false);
      setState(() {
        _worlds = worlds;
        _worldsError = null;
        _worldsLoading = false;
        _selectedWorldKey = worlds.isEmpty ? null : worlds.first.key;
      });
      if (worlds.isNotEmpty && _heatmapClient != null) {
        _applyViewport();
      }
    } on Object catch (error) {
      if (!mounted ||
          generation != _generation ||
          !identical(client, _controlClient)) {
        return;
      }
      setState(() {
        _worldsError = error;
        _worlds = null;
        _worldsLoading = false;
      });
    }
  }

  void _retryWorlds() {
    final IControlClient? client = _controlClient;
    if (client == null || _worldsLoading) return;
    setState(() {
      _worldsLoading = true;
      _worldsError = null;
    });
    unawaited(_loadWorlds(client, _generation));
  }

  void _applyViewport() {
    final String? world = _selectedWorldKey;
    final int? centerX = int.tryParse(_centerX.trim());
    final int? centerZ = int.tryParse(_centerZ.trim());
    final int? radius = int.tryParse(_radius.trim());
    final bool coordinatesInvalid = centerX == null || centerZ == null;
    final bool radiusInvalid =
        radius == null ||
        radius < heatmapMinimumRadius ||
        radius > heatmapMaximumRadius;
    if (world == null || coordinatesInvalid || radiusInvalid) {
      setState(() {
        _coordinatesInvalid = coordinatesInvalid;
        _radiusInvalid = radiusInvalid;
      });
      return;
    }
    setState(() {
      _coordinatesInvalid = false;
      _radiusInvalid = false;
      _appliedViewport = HeatmapViewport(
        world: world,
        centerX: centerX,
        centerZ: centerZ,
        radius: radius,
      );
      _error = null;
      _loading = _grids == null;
    });
    _refreshController.request();
  }

  Future<void> _refreshAppliedViewport() async {
    final IHeatmapClient? client = _heatmapClient;
    final HeatmapViewport? viewport = _appliedViewport;
    if (!mounted || client == null || viewport == null) return;
    final int generation = _generation;
    try {
      final List<HeatmapGrid> grids = await loadHeatmapGrids(client, viewport);
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient) ||
          !identical(viewport, _appliedViewport)) {
        return;
      }
      setState(() {
        _grids = grids;
        _error = null;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient) ||
          !identical(viewport, _appliedViewport)) {
        return;
      }
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
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
                ? serverSnapshotState(
                    scope: scope,
                    icon: ArcaneIcon.map(size: IconSize.sm),
                  )
                : ReactorEmptyState(
                    title: reactorText(ReactorText.heatmapsNoSpatialMetrics),
                    description: reactorText(
                      ReactorText.heatmapsNoSpatialMetricsDescription,
                    ),
                    icon: ArcaneIcon.map(size: IconSize.sm),
                  ),
          ),
        SectionPanel(
          label: reactorText(ReactorText.heatmapsChunkHeatmaps),
          flush: true,
          child: _chunkHeatmapsPanel(),
        ),
      ],
    );
  }

  Widget _chunkHeatmapsPanel() {
    if (_heatmapClient == null) return _chunkHeatmapState();
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'display': 'flex', 'flex-direction': 'column'},
      ),
      <Widget>[_viewportState(), _chunkHeatmapState()],
    );
  }

  Widget _viewportState() {
    final IControlClient? controlClient = _controlClient;
    if (controlClient == null) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsEndpointUnavailable),
        message: reactorText(ReactorText.heatmapsLiveRequired),
        status: ReactorStatus.critical,
      );
    }
    if (_worldsLoading) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.commonLoadingWorlds),
      );
    }
    final Object? worldsError = _worldsError;
    if (worldsError != null) {
      return ReactorNotice(
        title: reactorText(ReactorText.commonUpdateFailed),
        message: localizedReactorError(worldsError),
        status: ReactorStatus.critical,
        action: Button.secondary(
          label: reactorText(ReactorText.commonRetry),
          size: ButtonSize.small,
          onPressed: _retryWorlds,
        ),
      );
    }
    final List<WorldSettings>? worlds = _worlds;
    if (worlds == null) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.commonLoadingWorlds),
      );
    }
    if (worlds.isEmpty) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.worldOverridesNoWorlds),
        description: reactorText(ReactorText.worldOverridesNoWorldsDescription),
        icon: ArcaneIcon.globe(size: IconSize.sm),
      );
    }
    final String validationError = reactorText(ReactorText.errorBadRequest);
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'grid-template-columns':
              'repeat(auto-fit, minmax(min(100%, 160px), 1fr))',
          'align-items': 'end',
          'gap': '0.65rem',
          'padding': '0.8rem',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
        ArcaneNativeSelect(
          size: ComponentSize.sm,
          label: reactorText(ReactorText.heatmapsWorld),
          options: <ArcaneSelectOption>[
            for (final WorldSettings world in worlds)
              ArcaneSelectOption(
                label: world.name == world.key
                    ? world.name
                    : '${world.name} · ${world.key}',
                value: world.key,
              ),
          ],
          value: _selectedWorldKey,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _selectedWorldKey = value;
          }),
        ),
        TextInput(
          label: reactorText(ReactorText.heatmapsCenterChunkX),
          type: TextInputType.number,
          value: _centerX,
          error: _coordinatesInvalid ? validationError : null,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _centerX = value;
            _coordinatesInvalid = false;
          }),
        ),
        TextInput(
          label: reactorText(ReactorText.heatmapsCenterChunkZ),
          type: TextInputType.number,
          value: _centerZ,
          error: _coordinatesInvalid ? validationError : null,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _centerZ = value;
            _coordinatesInvalid = false;
          }),
        ),
        TextInput(
          label: reactorText(ReactorText.heatmapsRadiusChunks),
          type: TextInputType.number,
          value: _radius,
          error: _radiusInvalid ? validationError : null,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _radius = value;
            _radiusInvalid = false;
          }),
        ),
        Button.primary(
          label: reactorText(ReactorText.configEditorApplyChanges),
          size: ButtonSize.small,
          onPressed: _applyViewport,
        ),
      ],
    );
  }

  Widget _chunkHeatmapState() {
    final IHeatmapClient? client = _heatmapClient;
    if (client == null) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsEndpointUnavailable),
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
    final List<HeatmapGrid>? grids = _grids;
    if (error != null && (grids == null || grids.isEmpty)) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsRequestFailed),
        message: localizedReactorError(error),
        status: ReactorStatus.critical,
        action: Button.secondary(
          label: reactorText(ReactorText.commonRetry),
          size: ButtonSize.small,
          onPressed: _refreshController.request,
        ),
      );
    }
    if (grids == null) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.heatmapsChunkHeatmaps),
        description: reactorText(ReactorText.heatmapsSubtitle),
        icon: ArcaneIcon.grid3x3(size: IconSize.sm),
      );
    }
    if (grids.isEmpty) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.heatmapsNoChunkHeatmaps),
        description: reactorText(
          ReactorText.heatmapsNoChunkHeatmapsDescription,
        ),
        icon: ArcaneIcon.grid3x3(size: IconSize.sm),
      );
    }
    final List<Widget> children = <Widget>[
      if (error != null)
        ReactorNotice(
          title: reactorText(ReactorText.heatmapsRequestFailed),
          message: localizedReactorError(error),
          status: ReactorStatus.warning,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
            size: ButtonSize.small,
            onPressed: _refreshController.request,
          ),
        ),
      for (final HeatmapGrid grid in grids) HeatmapGridView(grid: grid),
    ];
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'display': 'flex', 'flex-direction': 'column'},
      ),
      children,
    );
  }
}
