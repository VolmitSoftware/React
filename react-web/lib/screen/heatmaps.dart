library;

import 'dart:async';
import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/heatmap.dart';
import '../model/player_navigation.dart';
import '../model/role_info.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import '../service/clipboard.dart';
import '../state/connection_manager.dart';
import '../state/control_scope.dart';
import '../state/heatmap_scope.dart';
import '../state/player_scope.dart';
import '../state/role_scope.dart';
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
const int heatmapMaximumRadius = 1875000;

final class HeatmapViewport {
  final String world;
  final int centerChunkX;
  final int centerChunkZ;
  final int radius;

  const HeatmapViewport({
    required this.world,
    required this.centerChunkX,
    required this.centerChunkZ,
    required this.radius,
  });

  HeatmapViewport copyWith({
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) => HeatmapViewport(
    world: world ?? this.world,
    centerChunkX: centerChunkX ?? this.centerChunkX,
    centerChunkZ: centerChunkZ ?? this.centerChunkZ,
    radius: radius ?? this.radius,
  );
}

HeatmapViewport panHeatmapViewport(
  HeatmapViewport viewport, {
  required int horizontal,
  required int vertical,
}) {
  final int step = math.max(1, viewport.radius);
  return viewport.copyWith(
    centerChunkX: viewport.centerChunkX + horizontal * step,
    centerChunkZ: viewport.centerChunkZ + vertical * step,
  );
}

HeatmapViewport zoomHeatmapViewport(
  HeatmapViewport viewport, {
  required bool zoomIn,
}) {
  final int radius = zoomIn
      ? math.max(heatmapMinimumRadius, (viewport.radius / 2).ceil())
      : math.min(heatmapMaximumRadius, viewport.radius * 2);
  return viewport.copyWith(radius: radius);
}

HeatmapViewport fitHeatmapWorldBorder(
  HeatmapViewport viewport,
  HeatmapWorldBorder border,
) {
  final int minimumChunkX = (border.minimumBlockX / 16).floor();
  final int maximumChunkX = (border.maximumBlockX / 16).ceil() - 1;
  final int minimumChunkZ = (border.minimumBlockZ / 16).floor();
  final int maximumChunkZ = (border.maximumBlockZ / 16).ceil() - 1;
  final int centerChunkX = ((minimumChunkX + maximumChunkX) / 2).floor();
  final int centerChunkZ = ((minimumChunkZ + maximumChunkZ) / 2).floor();
  final int radius = math.max(
    math.max(
      (centerChunkX - minimumChunkX).abs(),
      (maximumChunkX - centerChunkX).abs(),
    ),
    math.max(
      (centerChunkZ - minimumChunkZ).abs(),
      (maximumChunkZ - centerChunkZ).abs(),
    ),
  );
  return viewport.copyWith(
    centerChunkX: centerChunkX,
    centerChunkZ: centerChunkZ,
    radius: radius.clamp(heatmapMinimumRadius, heatmapMaximumRadius),
  );
}

Future<HeatmapGrid> loadHeatmapGrid(
  IHeatmapClient client,
  String id,
  HeatmapViewport viewport,
) => client.heatmap(
  id,
  world: viewport.world,
  centerChunkX: viewport.centerChunkX,
  centerChunkZ: viewport.centerChunkZ,
  radius: viewport.radius,
);

HeatmapTarget initialHeatmapTarget(HeatmapGrid grid) {
  final int column =
      ((grid.centerChunkX - grid.originChunkX) / grid.cellSizeChunks)
          .floor()
          .clamp(0, grid.columns - 1);
  final int row =
      ((grid.centerChunkZ - grid.originChunkZ) / grid.cellSizeChunks)
          .floor()
          .clamp(0, grid.rows - 1);
  return HeatmapTarget(
    world: grid.world,
    originChunkX: grid.originChunkX + column * grid.cellSizeChunks,
    originChunkZ: grid.originChunkZ + row * grid.cellSizeChunks,
    sizeChunks: grid.cellSizeChunks,
  );
}

bool heatmapTeleportEnabled({
  required ConnState state,
  required RoleInfo? role,
  required bool clientAvailable,
  required bool playerSelected,
  required bool pending,
}) =>
    state == ConnState.live &&
    role?.isAdmin == true &&
    clientAvailable &&
    playerSelected &&
    !pending;

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
      if (!_disposed && _operation != null && _pending) request();
    }
  }
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
  IPlayerClient? _playerClient;
  List<HeatmapSummary>? _summaries;
  Object? _summariesError;
  bool _summariesLoading = false;
  String? _selectedHeatmapId;
  List<WorldSettings>? _worlds;
  Object? _worldsError;
  bool _worldsLoading = false;
  String? _selectedWorldKey;
  String _centerChunkX = '0';
  String _centerChunkZ = '0';
  String _radius = '8';
  bool _coordinatesInvalid = false;
  bool _radiusInvalid = false;
  HeatmapViewport? _appliedViewport;
  HeatmapGrid? _grid;
  Object? _error;
  bool _loading = false;
  int _generation = 0;
  int _playerGeneration = 0;
  List<OnlinePlayerInfo>? _players;
  Object? _playersError;
  bool _playersLoading = false;
  String? _selectedPlayerId;
  HeatmapTarget? _selectedTarget;
  HeatmapTarget? _pendingTeleportTarget;
  bool _teleporting = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IHeatmapClient? heatmapClient = HeatmapScope.of(context)?.client;
    final IControlClient? controlClient = ControlScope.of(context)?.client;
    if (!identical(heatmapClient, _heatmapClient) ||
        !identical(controlClient, _controlClient)) {
      _replaceClients(heatmapClient, controlClient);
    }
    final bool admin = RoleScope.of(context)?.role?.isAdmin == true;
    final IPlayerClient? playerClient = admin
        ? PlayerScope.of(context)?.client
        : null;
    if (!identical(playerClient, _playerClient)) {
      _replacePlayerClient(playerClient);
    }
  }

  @override
  void dispose() {
    _generation++;
    _playerGeneration++;
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
    _summaries = null;
    _summariesError = null;
    _summariesLoading = heatmapClient != null;
    _selectedHeatmapId = null;
    _worlds = null;
    _worldsError = null;
    _worldsLoading = controlClient != null;
    _selectedWorldKey = null;
    _coordinatesInvalid = false;
    _radiusInvalid = false;
    _appliedViewport = null;
    _grid = null;
    _selectedTarget = null;
    _pendingTeleportTarget = null;
    _error = null;
    _loading = false;
    if (heatmapClient != null) {
      _refreshController.start(_refreshAppliedViewport);
      unawaited(_loadSummaries(heatmapClient, _generation));
    }
    if (controlClient != null) {
      unawaited(_loadWorlds(controlClient, _generation));
    }
  }

  void _replacePlayerClient(IPlayerClient? playerClient) {
    _playerGeneration++;
    _playerClient = playerClient;
    _players = null;
    _playersError = null;
    _playersLoading = playerClient != null;
    _selectedPlayerId = null;
    _pendingTeleportTarget = null;
    _teleporting = false;
    if (playerClient != null) {
      unawaited(_loadPlayers(playerClient, _playerGeneration));
    }
  }

  Future<void> _loadPlayers(IPlayerClient client, int generation) async {
    try {
      final List<OnlinePlayerInfo> loaded = await client.players();
      if (!mounted ||
          generation != _playerGeneration ||
          !identical(client, _playerClient)) {
        return;
      }
      final List<OnlinePlayerInfo> players = loaded
          .where(
            (OnlinePlayerInfo player) =>
                player.id.trim().isNotEmpty && player.name.trim().isNotEmpty,
          )
          .toList(growable: false);
      final String? current = _selectedPlayerId;
      final bool currentAvailable =
          current != null &&
          players.any((OnlinePlayerInfo player) => player.id == current);
      setState(() {
        _players = players;
        _playersError = null;
        _playersLoading = false;
        _selectedPlayerId = currentAvailable
            ? current
            : players.isEmpty
            ? null
            : players.first.id;
      });
    } on Object catch (error) {
      if (!mounted ||
          generation != _playerGeneration ||
          !identical(client, _playerClient)) {
        return;
      }
      setState(() {
        _players = null;
        _playersError = error;
        _playersLoading = false;
      });
    }
  }

  void _retryPlayers() {
    final IPlayerClient? client = _playerClient;
    if (client == null || _playersLoading) return;
    setState(() {
      _playersLoading = true;
      _playersError = null;
    });
    unawaited(_loadPlayers(client, _playerGeneration));
  }

  Future<void> _loadSummaries(IHeatmapClient client, int generation) async {
    try {
      final List<HeatmapSummary> loaded = await client.heatmaps();
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient)) {
        return;
      }
      final List<HeatmapSummary> summaries = loaded
          .where((HeatmapSummary summary) => summary.id.trim().isNotEmpty)
          .toList(growable: false);
      setState(() {
        _summaries = summaries;
        _summariesError = null;
        _summariesLoading = false;
        _selectedHeatmapId = summaries.isEmpty ? null : summaries.first.id;
      });
      _applyInitialViewport();
    } on Object catch (error) {
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient)) {
        return;
      }
      setState(() {
        _summariesError = error;
        _summaries = null;
        _summariesLoading = false;
      });
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
      _applyInitialViewport();
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

  void _applyInitialViewport() {
    if (_appliedViewport != null ||
        _selectedWorldKey == null ||
        _selectedHeatmapId == null) {
      return;
    }
    _applyViewport();
  }

  void _retrySummaries() {
    final IHeatmapClient? client = _heatmapClient;
    if (client == null || _summariesLoading) return;
    setState(() {
      _summariesLoading = true;
      _summariesError = null;
    });
    unawaited(_loadSummaries(client, _generation));
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
    final String? heatmapId = _selectedHeatmapId;
    final int? centerChunkX = int.tryParse(_centerChunkX.trim());
    final int? centerChunkZ = int.tryParse(_centerChunkZ.trim());
    final int? radius = int.tryParse(_radius.trim());
    final bool coordinatesInvalid =
        centerChunkX == null || centerChunkZ == null;
    final bool radiusInvalid =
        radius == null ||
        radius < heatmapMinimumRadius ||
        radius > heatmapMaximumRadius;
    if (world == null ||
        heatmapId == null ||
        coordinatesInvalid ||
        radiusInvalid) {
      setState(() {
        _coordinatesInvalid = coordinatesInvalid;
        _radiusInvalid = radiusInvalid;
      });
      return;
    }
    _setViewport(
      HeatmapViewport(
        world: world,
        centerChunkX: centerChunkX,
        centerChunkZ: centerChunkZ,
        radius: radius,
      ),
    );
  }

  void _setViewport(HeatmapViewport viewport) {
    setState(() {
      _selectedWorldKey = viewport.world;
      _centerChunkX = viewport.centerChunkX.toString();
      _centerChunkZ = viewport.centerChunkZ.toString();
      _radius = viewport.radius.toString();
      _coordinatesInvalid = false;
      _radiusInvalid = false;
      _appliedViewport = viewport;
      _error = null;
      _loading = true;
      _selectedTarget = null;
      _pendingTeleportTarget = null;
    });
    _refreshController.request();
  }

  void _pan(int horizontal, int vertical) {
    final HeatmapViewport? viewport = _appliedViewport;
    if (viewport == null) return;
    _setViewport(
      panHeatmapViewport(viewport, horizontal: horizontal, vertical: vertical),
    );
  }

  void _zoom(bool zoomIn) {
    final HeatmapViewport? viewport = _appliedViewport;
    if (viewport == null) return;
    _setViewport(zoomHeatmapViewport(viewport, zoomIn: zoomIn));
  }

  void _centerSpawn() {
    final HeatmapViewport? viewport = _appliedViewport;
    final HeatmapGrid? grid = _grid;
    if (viewport == null || grid == null) return;
    _setViewport(
      viewport.copyWith(
        centerChunkX: grid.spawnChunkX,
        centerChunkZ: grid.spawnChunkZ,
      ),
    );
  }

  void _fitBorder() {
    final HeatmapViewport? viewport = _appliedViewport;
    final HeatmapWorldBorder? border = _grid?.worldBorder;
    if (viewport == null || border == null) return;
    _setViewport(fitHeatmapWorldBorder(viewport, border));
  }

  Future<void> _refreshAppliedViewport() async {
    final IHeatmapClient? client = _heatmapClient;
    final String? heatmapId = _selectedHeatmapId;
    final HeatmapViewport? viewport = _appliedViewport;
    if (!mounted || client == null || heatmapId == null || viewport == null) {
      return;
    }
    final int generation = _generation;
    try {
      final HeatmapGrid grid = await loadHeatmapGrid(
        client,
        heatmapId,
        viewport,
      );
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient) ||
          heatmapId != _selectedHeatmapId ||
          !identical(viewport, _appliedViewport)) {
        return;
      }
      setState(() {
        _grid = grid;
        _selectedTarget ??= initialHeatmapTarget(grid);
        _error = null;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted ||
          generation != _generation ||
          !identical(client, _heatmapClient) ||
          heatmapId != _selectedHeatmapId ||
          !identical(viewport, _appliedViewport)) {
        return;
      }
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _copyTarget(HeatmapTarget target) async {
    final bool copied = await writeClipboardText(target.clipboardText);
    if (!mounted) return;
    if (copied) {
      ArcaneSonner.success(
        reactorText(ReactorText.heatmapPositionCopied),
        description: target.clipboardText,
      );
    } else {
      ArcaneSonner.error(
        reactorText(ReactorText.heatmapClipboardUnavailable),
        description: target.clipboardText,
      );
    }
  }

  void _requestTeleport(HeatmapTarget target) {
    setState(() => _pendingTeleportTarget = target);
  }

  Future<void> _confirmTeleport() async {
    final IPlayerClient? client = _playerClient;
    final String? playerId = _selectedPlayerId;
    final HeatmapTarget? target = _pendingTeleportTarget;
    if (client == null || playerId == null || target == null || _teleporting) {
      return;
    }
    setState(() {
      _pendingTeleportTarget = null;
      _teleporting = true;
    });
    try {
      final PlayerTeleportResult result = await client.teleportPlayer(
        playerId,
        worldKey: target.world,
        blockX: target.centerBlockX,
        blockZ: target.centerBlockZ,
      );
      if (!mounted) return;
      ArcaneSonner.success(
        reactorText(ReactorText.heatmapTeleportQueued),
        description:
            '${result.playerName} · ${result.worldKey} ${result.blockX} ${result.blockZ}',
      );
      unawaited(_loadPlayers(client, _playerGeneration));
    } on Object catch (error) {
      if (!mounted) return;
      ArcaneSonner.error(
        reactorText(ReactorText.heatmapTeleportFailed),
        description: localizedReactorError(error),
      );
    } finally {
      if (mounted) {
        setState(() => _teleporting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final List<Widget> tiles = <Widget>[];
    for (final (String id, ReactorText label) in _kSpatialSamplers) {
      final SamplerSample? sample = snapshot?.sampler(id);
      if (sample != null) {
        tiles.add(StatTile(label: reactorText(label), sample: sample));
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
    if (_controlClient == null) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsEndpointUnavailable),
        message: reactorText(ReactorText.heatmapsLiveRequired),
        status: ReactorStatus.critical,
      );
    }
    if (_worldsLoading || _summariesLoading) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.heatmapsLoading),
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
    final Object? summariesError = _summariesError;
    if (summariesError != null) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsRequestFailed),
        message: localizedReactorError(summariesError),
        status: ReactorStatus.critical,
        action: Button.secondary(
          label: reactorText(ReactorText.commonRetry),
          size: ButtonSize.small,
          onPressed: _retrySummaries,
        ),
      );
    }
    final List<WorldSettings>? worlds = _worlds;
    final List<HeatmapSummary>? summaries = _summaries;
    if (worlds == null || summaries == null) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.heatmapsLoading),
      );
    }
    if (worlds.isEmpty) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.worldOverridesNoWorlds),
        description: reactorText(ReactorText.worldOverridesNoWorldsDescription),
        icon: ArcaneIcon.globe(size: IconSize.sm),
      );
    }
    if (summaries.isEmpty) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.heatmapsNoChunkHeatmaps),
        description: reactorText(
          ReactorText.heatmapsNoChunkHeatmapsDescription,
        ),
        icon: ArcaneIcon.grid3x3(size: IconSize.sm),
      );
    }
    final String validationError = reactorText(ReactorText.errorBadRequest);
    return dom.div(classes: 'reactor-heatmap-viewport', <Widget>[
      dom.div(classes: 'reactor-heatmap-viewport-fields', <Widget>[
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
        ArcaneNativeSelect(
          size: ComponentSize.sm,
          label: reactorText(ReactorText.heatmapsChunkHeatmaps),
          options: <ArcaneSelectOption>[
            for (final HeatmapSummary summary in summaries)
              ArcaneSelectOption(label: summary.label, value: summary.id),
          ],
          value: _selectedHeatmapId,
          fullWidth: true,
          onChange: (String value) {
            setState(() {
              _selectedHeatmapId = value;
            });
            _applyViewport();
          },
        ),
        TextInput(
          label: reactorText(ReactorText.heatmapsCenterChunkX),
          type: TextInputType.number,
          value: _centerChunkX,
          error: _coordinatesInvalid ? validationError : null,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _centerChunkX = value;
            _coordinatesInvalid = false;
          }),
        ),
        TextInput(
          label: reactorText(ReactorText.heatmapsCenterChunkZ),
          type: TextInputType.number,
          value: _centerChunkZ,
          error: _coordinatesInvalid ? validationError : null,
          fullWidth: true,
          onChange: (String value) => setState(() {
            _centerChunkZ = value;
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
      ]),
      dom.div(
        classes: 'reactor-heatmap-navigation',
        attributes: <String, String>{
          'aria-label': reactorText(ReactorText.heatmapsChunkHeatmaps),
        },
        <Widget>[
          Button.secondary(
            label: 'N −Z',
            size: ButtonSize.small,
            onPressed: () => _pan(0, -1),
          ),
          Button.secondary(
            label: 'W −X',
            size: ButtonSize.small,
            onPressed: () => _pan(-1, 0),
          ),
          Button.secondary(
            label: 'E +X',
            size: ButtonSize.small,
            onPressed: () => _pan(1, 0),
          ),
          Button.secondary(
            label: 'S +Z',
            size: ButtonSize.small,
            onPressed: () => _pan(0, 1),
          ),
          Button.secondary(
            label: reactorText(ReactorText.heatmapsZoomIn),
            size: ButtonSize.small,
            disabled: _appliedViewport?.radius == heatmapMinimumRadius,
            onPressed: () => _zoom(true),
          ),
          Button.secondary(
            label: reactorText(ReactorText.heatmapsZoomOut),
            size: ButtonSize.small,
            disabled: _appliedViewport?.radius == heatmapMaximumRadius,
            onPressed: () => _zoom(false),
          ),
          Button.secondary(
            label: reactorText(ReactorText.heatmapsCenterSpawn),
            size: ButtonSize.small,
            disabled: _grid == null,
            onPressed: _centerSpawn,
          ),
          Button.secondary(
            label: reactorText(ReactorText.heatmapsFitBorder),
            size: ButtonSize.small,
            disabled: _grid?.worldBorder == null,
            onPressed: _fitBorder,
          ),
        ],
      ),
    ]);
  }

  Widget _chunkHeatmapState() {
    if (_heatmapClient == null) {
      return ReactorNotice(
        title: reactorText(ReactorText.heatmapsEndpointUnavailable),
        message: reactorText(ReactorText.heatmapsLiveRequired),
        status: ReactorStatus.critical,
      );
    }
    final Object? error = _error;
    final HeatmapGrid? grid = _grid;
    if (_loading && grid == null) {
      return ReactorLoadingState(
        label: reactorText(ReactorText.heatmapsLoading),
      );
    }
    if (error != null && grid == null) {
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
    if (grid == null) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.heatmapsChunkHeatmaps),
        description: reactorText(ReactorText.heatmapsSubtitle),
        icon: ArcaneIcon.grid3x3(size: IconSize.sm),
      );
    }
    return dom.div(
      classes: 'reactor-heatmap-result${_loading ? ' is-refreshing' : ''}',
      <Widget>[
        if (_loading)
          dom.div(
            classes: 'reactor-heatmap-refreshing',
            attributes: const <String, String>{'aria-live': 'polite'},
            <Widget>[
              dom.span(<Widget>[
                Component.text(reactorText(ReactorText.heatmapsRefreshing)),
              ]),
            ],
          ),
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
        HeatmapGridView(
          grid: grid,
          selection: _selectedTarget,
          onSelectionChanged: (HeatmapTarget target) => setState(() {
            _selectedTarget = target;
            _pendingTeleportTarget = null;
          }),
          onPan: _pan,
          onZoom: _zoom,
        ),
        _targetActions(grid),
      ],
    );
  }

  Widget _targetActions(HeatmapGrid grid) {
    final HeatmapTarget target = _selectedTarget ?? initialHeatmapTarget(grid);
    final ServerScope? server = ServerScope.of(context);
    final bool live = server?.state == ConnState.live;
    final bool admin = RoleScope.of(context)?.role?.isAdmin == true;
    final List<OnlinePlayerInfo> players =
        _players ?? const <OnlinePlayerInfo>[];
    final OnlinePlayerInfo? selectedPlayer = _selectedPlayer();
    final bool canTeleport = heatmapTeleportEnabled(
      state: server?.state ?? ConnState.offline,
      role: RoleScope.of(context)?.role,
      clientAvailable: _playerClient != null,
      playerSelected: selectedPlayer != null,
      pending: _teleporting,
    );
    return dom.div(classes: 'reactor-heatmap-target-actions', <Widget>[
      dom.div(classes: 'reactor-heatmap-target-coordinate', <Widget>[
        dom.strong(<Widget>[
          Component.text(
            reactorText(ReactorText.heatmapCellCenter, <String, Object?>{
              'x': target.centerBlockX,
              'z': target.centerBlockZ,
            }),
          ),
        ]),
        dom.code(<Widget>[Component.text(target.clipboardText)]),
      ]),
      Button.secondary(
        label: reactorText(ReactorText.heatmapCopyPosition),
        size: ButtonSize.small,
        onPressed: () => unawaited(_copyTarget(target)),
      ),
      if (!admin)
        dom.span(classes: 'reactor-heatmap-target-note', <Widget>[
          Component.text(reactorText(ReactorText.heatmapTeleportAdminRequired)),
        ]),
      if (admin && _playersLoading)
        dom.span(classes: 'reactor-heatmap-target-note', <Widget>[
          Component.text(reactorText(ReactorText.heatmapPlayersLoading)),
        ]),
      if (admin && _playersError != null)
        Button.secondary(
          label: reactorText(ReactorText.commonRetry),
          size: ButtonSize.small,
          onPressed: _retryPlayers,
        ),
      if (admin && !_playersLoading && _playersError == null && players.isEmpty)
        dom.span(classes: 'reactor-heatmap-target-note', <Widget>[
          Component.text(reactorText(ReactorText.heatmapNoOnlinePlayers)),
        ]),
      if (admin && players.isNotEmpty)
        dom.div(classes: 'reactor-heatmap-player-select', <Widget>[
          ArcaneNativeSelect(
            size: ComponentSize.sm,
            label: reactorText(ReactorText.heatmapPlayer),
            placeholder: reactorText(ReactorText.heatmapSelectOnlinePlayer),
            options: <ArcaneSelectOption>[
              for (final OnlinePlayerInfo player in players)
                ArcaneSelectOption(label: player.name, value: player.id),
            ],
            value: _selectedPlayerId,
            disabled: !live || _teleporting,
            onChange: (String value) => setState(() {
              _selectedPlayerId = value;
              _pendingTeleportTarget = null;
            }),
          ),
        ]),
      if (admin && players.isNotEmpty)
        Button.primary(
          label: reactorText(ReactorText.heatmapTeleport),
          size: ButtonSize.small,
          disabled: !canTeleport,
          onPressed: () => _requestTeleport(target),
        ),
      if (_pendingTeleportTarget != null && selectedPlayer != null)
        dom.div(classes: 'reactor-heatmap-teleport-confirm', <Widget>[
          ArcaneConfirmDialog(
            title: reactorText(
              ReactorText.heatmapTeleportConfirmTitle,
              <String, Object?>{'player': selectedPlayer.name},
            ),
            message: reactorText(
              ReactorText.heatmapTeleportConfirmMessage,
              <String, Object?>{
                'player': selectedPlayer.name,
                'world': _pendingTeleportTarget!.world,
                'x': _pendingTeleportTarget!.centerBlockX,
                'z': _pendingTeleportTarget!.centerBlockZ,
              },
            ),
            confirmText: reactorText(ReactorText.heatmapTeleport),
            onConfirm: () => unawaited(_confirmTeleport()),
            onCancel: () => setState(() => _pendingTeleportTarget = null),
          ),
        ]),
    ]);
  }

  OnlinePlayerInfo? _selectedPlayer() {
    final String? selectedId = _selectedPlayerId;
    if (selectedId == null) return null;
    for (final OnlinePlayerInfo player
        in _players ?? const <OnlinePlayerInfo>[]) {
      if (player.id == selectedId) return player;
    }
    return null;
  }
}
