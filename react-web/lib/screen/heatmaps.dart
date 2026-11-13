/// Map: one card holding the chunk heatmap. The world and heatmap selects
/// and the view buttons sit in its header, the plane fills it, and the
/// picked cell's facts and actions (copy, teleport for admins) follow under
/// a separator. Advanced adds the centre and radius fields, the compass and
/// fit-border buttons and the spatial sampler tiles.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/heatmap.dart';
import '../model/player_navigation.dart';
import '../model/role_info.dart';
import '../model/world_settings.dart';
import '../service/clipboard.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../state/world_overrides_controller.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/browser.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import '../widget/heatmap_controls.dart';
import '../widget/heatmap_grid_view.dart';
import '../widget/server_snapshot_state.dart';
import 'heatmaps_spatial.dart';
import 'heatmaps_viewport.dart';

/// The view a map opens on: the world's origin, eight chunks out.
const int heatmapInitialRadius = 8;

/// The id of the picked cell's Copy button (Enter on the grid lands there).
const String heatmapCopyButtonId = 'heatmap-copy';

class HeatmapsScreen extends StatefulWidget {
  const HeatmapsScreen({super.key});

  @override
  State<HeatmapsScreen> createState() => _HeatmapsScreenState();
}

class _HeatmapsScreenState extends State<HeatmapsScreen> {
  final HeatmapRefreshController _refreshController =
      HeatmapRefreshController();

  IHeatmapClient? _heatmapClient;

  /// The server's worlds, kept current by the session (a world added or
  /// renamed on the server shows here without a reload).
  WorldOverridesController? _worlds;
  bool _worldsSeen = false;
  IPlayerClient? _playerClient;
  List<HeatmapSummary>? _summaries;
  Object? _summariesError;
  bool _summariesLoading = false;
  String? _selectedHeatmapId;
  String? _selectedWorldKey;

  HeatmapViewport? _appliedViewport;
  HeatmapGrid? _grid;
  Object? _error;
  int _generation = 0;
  int _playerGeneration = 0;
  List<OnlinePlayerInfo>? _players;
  Object? _playersError;
  bool _playersLoading = false;
  String? _selectedPlayerId;
  HeatmapTarget? _selectedTarget;
  bool _teleporting = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ServerSession? session = ServerSessionScope.of(context);
    final IHeatmapClient? heatmapClient = session?.clientAs<IHeatmapClient>();
    final WorldOverridesController? worlds =
        session != null && session.supports<IControlClient>()
        ? session.worlds
        : null;
    if (!identical(heatmapClient, _heatmapClient) ||
        !identical(worlds, _worlds)) {
      _replaceClients(heatmapClient, worlds);
    }
    final bool admin = RoleScope.of(context)?.role?.isAdmin == true;
    final IPlayerClient? playerClient = admin
        ? session?.clientAs<IPlayerClient>()
        : null;
    if (!identical(playerClient, _playerClient)) {
      _replacePlayerClient(playerClient);
    }
  }

  @override
  void dispose() {
    _worlds?.removeListener(_worldsChanged);
    _generation++;
    _playerGeneration++;
    _refreshController.dispose();
    super.dispose();
  }

  void _replaceClients(
    IHeatmapClient? heatmapClient,
    WorldOverridesController? worlds,
  ) {
    _generation++;
    _refreshController.stop();
    _heatmapClient = heatmapClient;
    _worlds?.removeListener(_worldsChanged);
    _worlds = worlds;
    _worldsSeen = false;
    _summaries = null;
    _summariesError = null;
    _summariesLoading = heatmapClient != null;
    _selectedHeatmapId = null;
    _selectedWorldKey = null;
    _appliedViewport = null;
    _grid = null;
    _selectedTarget = null;
    _error = null;
    if (heatmapClient != null) {
      _refreshController.start(_refreshAppliedViewport);
      unawaited(_loadSummaries(heatmapClient, _generation));
    }
    // The first listener loads the list; later revisions refetch it.
    worlds?.addListener(_worldsChanged);
  }

  List<WorldSettings> get _worldList => <WorldSettings>[
    for (final WorldSettings world
        in _worlds?.worlds ?? const <WorldSettings>[])
      if (world.key.trim().isNotEmpty) world,
  ];

  /// Keeps the picked world when it is still there, else takes the first,
  /// and moves an open view off a world the server no longer has.
  void _worldsChanged() {
    final WorldOverridesController? controller = _worlds;
    if (!mounted || controller == null) return;
    final List<WorldSettings> worlds = _worldList;
    bool has(String? key) =>
        key != null && worlds.any((WorldSettings w) => w.key == key);
    setState(() {
      if (!controller.loading) _worldsSeen = true;
      if (!has(_selectedWorldKey)) {
        _selectedWorldKey = worlds.isEmpty ? null : worlds.first.key;
      }
    });
    final HeatmapViewport? viewport = _appliedViewport;
    final String? world = _selectedWorldKey;
    if (viewport != null && world != null && !has(viewport.world)) {
      _setViewport(viewport.copyWith(world: world));
    }
    _applyInitialViewport();
  }

  void _replacePlayerClient(IPlayerClient? playerClient) {
    _playerGeneration++;
    _playerClient = playerClient;
    _players = null;
    _playersError = null;
    _playersLoading = playerClient != null;
    _selectedPlayerId = null;
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

  void _applyInitialViewport() {
    final String? world = _selectedWorldKey;
    if (_appliedViewport != null ||
        world == null ||
        _selectedHeatmapId == null) {
      return;
    }
    _setViewport(
      HeatmapViewport(
        world: world,
        centerChunkX: 0,
        centerChunkZ: 0,
        radius: heatmapInitialRadius,
      ),
    );
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

  void _setViewport(HeatmapViewport viewport) {
    setState(() {
      _selectedWorldKey = viewport.world;
      _appliedViewport = viewport;
      _error = null;
      _selectedTarget = null;
    });
    _refreshController.request();
  }

  void _pickWorld(String world) {
    final HeatmapViewport? viewport = _appliedViewport;
    if (viewport == null || viewport.world == world) return;
    _setViewport(viewport.copyWith(world: world));
  }

  void _pickHeatmap(String id) {
    final HeatmapViewport? viewport = _appliedViewport;
    if (id == _selectedHeatmapId) return;
    setState(() {
      _selectedHeatmapId = id;
      _grid = null;
    });
    if (viewport != null) _setViewport(viewport);
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

  Future<void> _teleport(HeatmapTarget target) async {
    final IPlayerClient? client = _playerClient;
    final OnlinePlayerInfo? player = _selectedPlayer();
    if (client == null || player == null || _teleporting) return;
    final bool confirmed = await askToConfirm(
      title: reactorText(
        ReactorText.heatmapTeleportConfirmTitle,
        <String, Object?>{'player': player.name},
      ),
      message: reactorText(
        ReactorText.heatmapTeleportConfirmMessage,
        <String, Object?>{
          'world': target.world,
          'x': target.centerBlockX,
          'z': target.centerBlockZ,
        },
      ),
      confirmLabel: reactorText(ReactorText.heatmapTeleport),
    );
    if (!confirmed || !mounted || !identical(client, _playerClient)) return;
    setState(() => _teleporting = true);
    try {
      final PlayerTeleportResult result = await client.teleportPlayer(
        player.id,
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
      if (mounted) setState(() => _teleporting = false);
    }
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

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return PageStack(
      children: <Widget>[
        _mapCard(context),
        const ModeGate(child: SpatialMetrics()),
      ],
    );
  }

  Widget _mapCard(BuildContext context) {
    final Widget? state = _setupState();
    if (state != null) return AppCard(children: <Widget>[state]);
    return AppCard(
      actions: _toolbar(_worldList, _summaries!),
      children: <Widget>[
        if (_appliedViewport case final HeatmapViewport viewport)
          ModeGate(
            child: HeatmapViewportFields(
              viewport: viewport,
              onCommit: _setViewport,
            ),
          ),
        ..._plane(context),
      ],
    );
  }

  /// What the card shows until it has worlds and heatmaps to pick from.
  Widget? _setupState() {
    final WorldOverridesController? worldsController = _worlds;
    if (_heatmapClient == null || worldsController == null) {
      final ServerScope? scope = ServerScope.of(context);
      if (scope == null || scope.state != ConnState.live) {
        return serverSnapshotState(scope: scope, icon: ArcaneIcon.map());
      }
      return EmptyState(
        message: reactorText(ReactorText.heatmapsLiveRequired),
        icon: ArcaneIcon.map(),
      );
    }
    final List<WorldSettings> worlds = _worldList;
    final Object? worldsError = worlds.isEmpty ? worldsController.error : null;
    if (worldsError == null && worlds.isEmpty && !_worldsSeen ||
        _summariesLoading) {
      return LoadingState(label: reactorText(ReactorText.heatmapsLoading));
    }
    final Object? failure = worldsError ?? _summariesError;
    if (failure != null) {
      return heatmapFailure(
        failure,
        worldsError != null ? worldsController.load : _retrySummaries,
      );
    }
    final List<HeatmapSummary>? summaries = _summaries;
    if (summaries == null) {
      return LoadingState(label: reactorText(ReactorText.heatmapsLoading));
    }
    if (worlds.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.heatmapsNoWorlds),
        icon: ArcaneIcon.globe(),
      );
    }
    if (summaries.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.heatmapsNoChunkHeatmaps),
        icon: ArcaneIcon.grid3x3(),
      );
    }
    return null;
  }

  ActionRow _toolbar(List<WorldSettings> worlds, List<HeatmapSummary> maps) {
    final HeatmapViewport? viewport = _appliedViewport;
    final bool advanced = ModeScope.advanced(context);
    final bool ready = viewport != null;
    RowAction button(
      ReactorText label,
      ArcaneGlyph icon,
      void Function()? onPressed,
    ) => RowAction(reactorText(label), onPressed, icon: icon, iconOnly: true);
    return ActionRow(
      dense: true,
      label: reactorText(ReactorText.pageMapTitle),
      leading: <Widget>[
        HeatmapPick(
          label: reactorText(ReactorText.heatmapsWorld),
          value: _selectedWorldKey,
          options: <ArcaneSelectOption>[
            for (final WorldSettings world in worlds)
              ArcaneSelectOption(label: world.name, value: world.key),
          ],
          onChanged: _pickWorld,
        ),
        HeatmapPick(
          label: reactorText(ReactorText.heatmapsChunkHeatmaps),
          value: _selectedHeatmapId,
          options: <ArcaneSelectOption>[
            for (final HeatmapSummary summary in maps)
              ArcaneSelectOption(label: summary.label, value: summary.id),
          ],
          onChanged: _pickHeatmap,
        ),
      ],
      secondary: <RowAction>[
        if (advanced) ...<RowAction>[
          button(
            ReactorText.heatmapsFitBorder,
            ArcaneIcon.scan(),
            _grid?.worldBorder == null ? null : _fitBorder,
          ),
          button(
            ReactorText.heatmapsWest,
            ArcaneIcon.arrowLeft(),
            ready ? () => _pan(-1, 0) : null,
          ),
          button(
            ReactorText.heatmapsNorth,
            ArcaneIcon.arrowUp(),
            ready ? () => _pan(0, -1) : null,
          ),
          button(
            ReactorText.heatmapsSouth,
            ArcaneIcon.arrowDown(),
            ready ? () => _pan(0, 1) : null,
          ),
          button(
            ReactorText.heatmapsEast,
            ArcaneIcon.arrowRight(),
            ready ? () => _pan(1, 0) : null,
          ),
        ],
        button(
          ReactorText.heatmapsCenterSpawn,
          ArcaneIcon.house(),
          _grid == null ? null : _centerSpawn,
        ),
        button(
          ReactorText.heatmapsZoomOut,
          ArcaneIcon.zoomOut(),
          !ready || viewport.radius >= heatmapMaximumRadius
              ? null
              : () => _zoom(false),
        ),
        button(
          ReactorText.heatmapsZoomIn,
          ArcaneIcon.zoomIn(),
          !ready || viewport.radius <= heatmapMinimumRadius
              ? null
              : () => _zoom(true),
        ),
      ],
    );
  }

  List<Widget> _plane(BuildContext context) {
    final HeatmapGrid? grid = _grid;
    final Object? error = _error;
    if (grid == null && error != null) {
      return <Widget>[heatmapFailure(error, _refreshController.request)];
    }
    if (grid == null) {
      return <Widget>[
        LoadingState(
          label: reactorText(ReactorText.heatmapsLoading),
          height: '20rem',
        ),
      ];
    }
    final HeatmapTarget target = _selectedTarget ?? initialHeatmapTarget(grid);
    return <Widget>[
      if (error != null)
        AppAlert(
          tone: AlertTone.warning,
          title: reactorText(ReactorText.heatmapsRequestFailed),
          message: localizedReactorError(error),
          action: ActionRow.footer(
            dense: true,
            secondary: <RowAction>[
              RowAction(
                reactorText(ReactorText.commonRetry),
                _refreshController.request,
              ),
            ],
          ),
        ),
      if (grid.cells.isEmpty)
        AppAlert(message: reactorText(ReactorText.heatmapNoScoredChunks)),
      HeatmapGridView(
        grid: grid,
        unit: ServerScope.of(context)?.snapshot?.byId[grid.id]?.suffix,
        selection: _selectedTarget,
        onSelectionChanged: (HeatmapTarget picked) =>
            setState(() => _selectedTarget = picked),
        onPan: _pan,
        onZoom: _zoom,
        onPick: () => focusById(heatmapCopyButtonId),
      ),
      const ArcaneSeparator(),
      HeatmapCellDetails(grid: grid, target: target),
      _targetActions(context, target),
    ];
  }

  ActionRow _targetActions(BuildContext context, HeatmapTarget target) {
    final ServerScope? server = ServerScope.of(context);
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool admin = role?.isAdmin == true;
    final bool live = server?.state == ConnState.live;
    final List<OnlinePlayerInfo> players =
        _players ?? const <OnlinePlayerInfo>[];
    final bool canTeleport = heatmapTeleportEnabled(
      state: server?.state ?? ConnState.offline,
      role: role,
      clientAvailable: _playerClient != null,
      playerSelected: _selectedPlayer() != null,
      pending: _teleporting,
    );
    final List<Widget> leading = <Widget>[
      if (admin && _playersLoading)
        _caption(reactorText(ReactorText.heatmapPlayersLoading))
      else if (admin && _playersError == null && players.isEmpty)
        _caption(reactorText(ReactorText.heatmapNoOnlinePlayers))
      else if (admin && players.isNotEmpty)
        HeatmapPick(
          label: reactorText(ReactorText.heatmapPlayer),
          placeholder: reactorText(ReactorText.heatmapSelectOnlinePlayer),
          value: _selectedPlayerId,
          disabled: !live || _teleporting,
          options: <ArcaneSelectOption>[
            for (final OnlinePlayerInfo player in players)
              ArcaneSelectOption(label: player.name, value: player.id),
          ],
          onChanged: (String value) =>
              setState(() => _selectedPlayerId = value),
        ),
    ];
    return ActionRow(
      dense: true,
      leading: leading,
      secondary: <RowAction>[
        if (admin && _playersError != null)
          RowAction(reactorText(ReactorText.commonRetry), _retryPlayers),
        RowAction(
          reactorText(ReactorText.heatmapCopyPosition),
          () => unawaited(_copyTarget(target)),
          icon: ArcaneIcon.copy(),
          id: heatmapCopyButtonId,
        ),
      ],
      primary: admin && players.isNotEmpty
          ? RowAction(
              reactorText(ReactorText.heatmapTeleport),
              canTeleport ? () => unawaited(_teleport(target)) : null,
              loading: _teleporting,
            )
          : null,
    );
  }

  Widget _caption(String text) =>
      dom.span(classes: 'ui-caption', <Widget>[Component.text(text)]);
}
