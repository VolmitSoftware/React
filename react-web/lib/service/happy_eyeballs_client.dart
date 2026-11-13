library;

import 'dart:async';

import '../model/action_descriptor.dart';
import '../model/config_tree.dart';
import '../model/control_item.dart';
import '../model/environment_info.dart';
import '../model/heatmap.dart';
import '../model/identity_info.dart';
import '../model/log_line.dart';
import '../model/incident_status.dart';
import '../model/metric_history.dart';
import '../model/player_navigation.dart';
import '../model/range_history.dart';
import '../model/plugin_api_pack.dart';
import '../model/role_info.dart';
import '../model/server_capabilities.dart';
import '../model/server_snapshot.dart';
import '../model/server_summary.dart';
import '../model/world_settings.dart';
import 'react_client.dart';
import 'react_exceptions.dart';
import 'relay_connection_interface.dart';
import 'relay_identity.dart';

enum RelayPath { none, direct, relay }

class HappyEyeballsClient
    implements
        IReactClient,
        IPingClient,
        IHeatmapClient,
        IPlayerClient,
        IControlClient,
        IOperateClient,
        IActionTicketClient,
        ILogLineClient,
        IConsoleClient,
        IRoleClient,
        ISummaryClient,
        IHistoryClient,
        IPluginApiPackClient,
        IServerPresence {
  final IReactClient? _direct;
  final IReactClient? _relay;
  final String? _pinnedFingerprint;

  /// How long a relay win waits for the direct path before settling, so a
  /// reachable direct path is preferred even when the relay answers first.
  final Duration directGrace;
  RelayPath _active;
  bool _closed = false;
  Future<IReactClient>? _resolving;

  HappyEyeballsClient({
    IReactClient? direct,
    IReactClient? relay,
    this._pinnedFingerprint,
    this.directGrace = const Duration(milliseconds: 250),
  }) : _direct = direct,
       _relay = relay,
       _active = RelayPath.none {
    if (direct == null && relay == null) {
      throw ArgumentError('At least one of direct or relay must be non-null');
    }
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _active = RelayPath.none;
    await Future.wait<void>(<Future<void>>[
      if (_direct != null) _direct.close(),
      if (_relay != null && !identical(_relay, _direct)) _relay.close(),
    ]);
  }

  RelayPath get activePath => _active;

  /// Forgets the chosen path so the next request races both again (direct
  /// preferred), for example to move from the relay back to a direct path
  /// that became reachable.
  void reprobe() => _markStale();

  void _markStale() {
    _active = RelayPath.none;
  }

  Future<IReactClient> _resolve() async {
    if (_closed) throw const ReactUnavailable('Client closed');
    final Future<IReactClient>? resolving = _resolving;
    if (resolving != null) return resolving;
    final Future<IReactClient> pending = _resolvePath();
    _resolving = pending;
    try {
      return await pending;
    } finally {
      if (identical(_resolving, pending)) _resolving = null;
    }
  }

  Future<IReactClient> _resolvePath() async {
    if (_active != RelayPath.none) {
      final IReactClient? cached = _active == RelayPath.direct
          ? _direct
          : _relay;
      if (cached != null) return cached;
    }

    final Completer<IReactClient> winner = Completer<IReactClient>();
    int outstanding = (_direct != null ? 1 : 0) + (_relay != null ? 1 : 0);
    Object? lastError;
    bool pinMismatchSeen = false;
    bool directPending = _direct != null;
    IReactClient? relayReady;
    Timer? grace;

    void settle(RelayPath path, IReactClient client) {
      if (winner.isCompleted) return;
      grace?.cancel();
      _active = path;
      winner.complete(client);
    }

    void checkDone() {
      if (winner.isCompleted) return;
      final IReactClient? relay = relayReady;
      if (relay != null && !directPending) {
        settle(RelayPath.relay, relay);
        return;
      }
      if (outstanding > 0) return;
      winner.completeError(
        pinMismatchSeen
            ? const ReactWrongServer(
                'Server identity mismatch (possible relay interception)',
              )
            : lastError ?? const ReactUnavailable('No path available'),
      );
    }

    Future<void> probe(RelayPath path, IReactClient client) async {
      try {
        final String observedFingerprint;
        if (client is IPingClient) {
          final ServerCapabilities capabilities = await (client as IPingClient)
              .ping();
          observedFingerprint = capabilities.serverFingerprint;
        } else {
          final IdentityInfo identity = await client.identity();
          observedFingerprint = identity.serverId;
        }
        if (!RelayIdentity.pinMatches(
          pinnedFingerprint: _pinnedFingerprint,
          identityServerId: observedFingerprint,
        )) {
          pinMismatchSeen = true;
          throw const ReactWrongServer(
            'Server identity mismatch (possible relay interception)',
          );
        }
        await client.identity();
        if (_closed) throw const ReactUnavailable('Client closed');
        outstanding--;
        if (path == RelayPath.direct) {
          directPending = false;
          settle(RelayPath.direct, client);
          return;
        }
        relayReady = client;
        if (!directPending) {
          checkDone();
          return;
        }
        grace ??= Timer(directGrace, () {
          directPending = false;
          checkDone();
        });
      } on Object catch (error) {
        outstanding--;
        lastError = error;
        if (path == RelayPath.direct) directPending = false;
        checkDone();
      }
    }

    final IReactClient? direct = _direct;
    final IReactClient? relay = _relay;
    if (direct != null) unawaited(probe(RelayPath.direct, direct));
    if (relay != null) unawaited(probe(RelayPath.relay, relay));

    return winner.future;
  }

  Future<T> _execute<T>(
    Future<T> Function(IReactClient client) operation,
  ) async {
    final IReactClient client = await _resolve();
    try {
      if (_closed) throw const ReactUnavailable('Client closed');
      final T result = await operation(client);
      if (_closed) throw const ReactUnavailable('Client closed');
      return result;
    } on ReactUnavailable {
      _markStale();
      rethrow;
    }
  }

  /// A read: when the path in use stops answering, it is asked once more on
  /// whichever path answers now, so a dropped direct path moves to the relay
  /// within the same call and the page never sees the gap. Writes are not
  /// sent twice.
  Future<T> _read<T>(Future<T> Function(IReactClient client) operation) async {
    final bool settled = _active != RelayPath.none;
    try {
      return await _execute(operation);
    } on ReactUnavailable {
      if (!settled || _closed || _direct == null || _relay == null) rethrow;
      return _execute(operation);
    }
  }

  IHeatmapClient _heatmaps(IReactClient client) {
    if (client is IHeatmapClient) return client as IHeatmapClient;
    throw const ReactUnavailable('Active transport does not support heatmaps');
  }

  IControlClient _controls(IReactClient client) {
    if (client is IControlClient) return client as IControlClient;
    throw const ReactUnavailable('Active transport does not support controls');
  }

  IPlayerClient _players(IReactClient client) {
    if (client is IPlayerClient) return client as IPlayerClient;
    throw const ReactUnavailable(
      'Active transport does not support player navigation',
    );
  }

  IOperateClient _operations(IReactClient client) {
    if (client is IOperateClient) return client as IOperateClient;
    throw const ReactUnavailable(
      'Active transport does not support operations',
    );
  }

  IRoleClient _roles(IReactClient client) {
    if (client is IRoleClient) return client as IRoleClient;
    throw const ReactUnavailable('Active transport does not support roles');
  }

  IConsoleClient _console(IReactClient client) {
    if (client is IConsoleClient) return client as IConsoleClient;
    throw const ReactUnavailable(
      'Active transport does not support console execution',
    );
  }

  IHistoryClient _history(IReactClient client) {
    if (client is IHistoryClient) return client as IHistoryClient;
    throw const ReactUnavailable(
      'Active transport does not support metric history',
    );
  }

  IPluginApiPackClient _pluginApiPacks(IReactClient client) {
    if (client is IPluginApiPackClient) return client as IPluginApiPackClient;
    throw const ReactUnavailable(
      'Active transport does not support Plugin API packs',
    );
  }

  @override
  Future<ServerCapabilities> ping() => _read((IReactClient client) {
    if (client is IPingClient) return (client as IPingClient).ping();
    throw const ReactUnavailable('Active transport does not support ping');
  });

  @override
  Future<IdentityInfo> identity() =>
      _read((IReactClient client) => client.identity());

  @override
  Future<ServerSnapshot> metrics() =>
      _read((IReactClient client) => client.metrics());

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() =>
      _read((IReactClient client) => _history(client).historyCatalog());

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) => _read(
    (IReactClient client) => _history(client).historyPage(
      ids: ids,
      from: from,
      to: to,
      maxPoints: maxPoints,
      pageSize: pageSize,
      cursor: cursor,
    ),
  );

  @override
  Future<RangeHistory> metricRange(ChartRange range, {List<String>? ids}) =>
      _read(
        (IReactClient client) => _history(client).metricRange(range, ids: ids),
      );

  @override
  Future<RoleInfo> whoami() =>
      _read((IReactClient client) => _roles(client).whoami());

  @override
  Future<ServerSummary> summary() => _read((IReactClient client) {
    if (client is ISummaryClient) return (client as ISummaryClient).summary();
    throw const ReactUnavailable('Active transport has no summary');
  });

  @override
  Future<List<HeatmapSummary>> heatmaps() =>
      _read((IReactClient client) => _heatmaps(client).heatmaps());

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) => _read(
    (IReactClient client) => _heatmaps(client).heatmap(
      id,
      world: world,
      centerChunkX: centerChunkX,
      centerChunkZ: centerChunkZ,
      radius: radius,
    ),
  );

  @override
  Future<List<OnlinePlayerInfo>> players() =>
      _read((IReactClient client) => _players(client).players());

  @override
  Future<PlayerTeleportResult> teleportPlayer(
    String playerId, {
    required String worldKey,
    required int blockX,
    required int blockZ,
  }) => _execute(
    (IReactClient client) => _players(client).teleportPlayer(
      playerId,
      worldKey: worldKey,
      blockX: blockX,
      blockZ: blockZ,
    ),
  );

  @override
  Future<List<ControlItem>> features() =>
      _read((IReactClient client) => _controls(client).features());

  @override
  Future<ControlItem> feature(String id) =>
      _read((IReactClient client) => _controls(client).feature(id));

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) => _execute(
    (IReactClient client) => _controls(client).setFeatureEnabled(id, enabled),
  );

  @override
  Future<ControlItem> setFeatureConfig(String id, Map<String, Object?> knobs) =>
      _execute(
        (IReactClient client) => _controls(client).setFeatureConfig(id, knobs),
      );

  @override
  Future<List<ControlItem>> tweaks() =>
      _read((IReactClient client) => _controls(client).tweaks());

  @override
  Future<ControlItem> tweak(String id) =>
      _read((IReactClient client) => _controls(client).tweak(id));

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) => _execute(
    (IReactClient client) => _controls(client).setTweakEnabled(id, enabled),
  );

  @override
  Future<ControlItem> setTweakConfig(String id, Map<String, Object?> knobs) =>
      _execute(
        (IReactClient client) => _controls(client).setTweakConfig(id, knobs),
      );

  @override
  Future<List<WorldSettings>> worlds() =>
      _read((IReactClient client) => _controls(client).worlds());

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) => _execute(
    (IReactClient client) => _controls(client).setWorld(
      name,
      budgetMs: budgetMs,
      panicMs: panicMs,
      releaseMs: releaseMs,
    ),
  );

  @override
  Future<List<ActionDescriptor>> actions() =>
      _read((IReactClient client) => _operations(client).actions());

  @override
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  }) => _execute(
    (IReactClient client) =>
        _operations(client).executeAction(id, params: params, confirm: confirm),
  );

  @override
  Future<ActionTicketStatus> actionTicket(String ticketId) =>
      _read((IReactClient client) {
        if (client is IActionTicketClient) {
          return (client as IActionTicketClient).actionTicket(ticketId);
        }
        throw const ReactNotFound('Action tickets are not supported');
      });

  @override
  Future<IncidentStatus> incidents() =>
      _read((IReactClient client) => _operations(client).incidents());

  @override
  Future<EnvironmentInfo> environment() =>
      _read((IReactClient client) => _operations(client).environment());

  @override
  Future<ConfigTree> config() =>
      _read((IReactClient client) => _operations(client).config());

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) => _execute(
    (IReactClient client) => _operations(client).applyConfig(changes),
  );

  @override
  Future<ConfigTree> applyPreset(String name) =>
      _execute((IReactClient client) => _operations(client).applyPreset(name));

  @override
  Future<List<String>> logs({int limit = 200}) =>
      _read((IReactClient client) => _operations(client).logs(limit: limit));

  @override
  Future<List<LogLine>> logLines({int limit = 200, int? after}) => _read((
    IReactClient client,
  ) {
    if (client is ILogLineClient) {
      return (client as ILogLineClient).logLines(limit: limit, after: after);
    }
    return _operations(client)
        .logs(limit: limit)
        .then(
          (List<String> lines) =>
              lines.map((String line) => LogLine(line)).toList(),
        );
  });

  @override
  Stream<bool> get serverOnline {
    final IReactClient? relay = _relay;
    if (relay is IServerPresence) {
      return (relay as IServerPresence).serverOnline;
    }
    return const Stream<bool>.empty();
  }

  @override
  Future<bool> executeConsole(String command) => _execute(
    (IReactClient client) => _console(client).executeConsole(command),
  );

  @override
  Future<PluginApiCatalog> pluginApiPacks() => _read(
    (IReactClient client) => _pluginApiPacks(client).pluginApiPacks(),
  );

  @override
  Future<PluginApiValidationResult> validatePluginApiPack(String content) =>
      _execute(
        (IReactClient client) =>
            _pluginApiPacks(client).validatePluginApiPack(content),
      );

  @override
  Future<PluginApiPack> installPluginApiPack(String id, String content) =>
      _execute(
        (IReactClient client) =>
            _pluginApiPacks(client).installPluginApiPack(id, content),
      );

  @override
  Future<PluginApiCatalog> removePluginApiPack(String id) => _execute(
    (IReactClient client) => _pluginApiPacks(client).removePluginApiPack(id),
  );
}
