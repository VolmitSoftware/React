library;

import 'dart:async';

import '../model/action_descriptor.dart';
import '../model/config_tree.dart';
import '../model/control_item.dart';
import '../model/environment_info.dart';
import '../model/heatmap.dart';
import '../model/identity_info.dart';
import '../model/incident_status.dart';
import '../model/role_info.dart';
import '../model/server_capabilities.dart';
import '../model/server_snapshot.dart';
import '../model/world_settings.dart';
import 'react_client.dart';
import 'react_exceptions.dart';
import 'relay_identity.dart';

enum RelayPath { none, direct, relay }

class HappyEyeballsClient
    implements
        IReactClient,
        IPingClient,
        IHeatmapClient,
        IControlClient,
        IOperateClient,
        IConsoleClient,
        IRoleClient {
  final IReactClient? _direct;
  final IReactClient? _relay;
  final String? _pinnedFingerprint;
  RelayPath _active;

  HappyEyeballsClient({
    IReactClient? direct,
    IReactClient? relay,
    String? pinnedFingerprint,
  }) : _direct = direct,
       _relay = relay,
       _pinnedFingerprint = pinnedFingerprint,
       _active = RelayPath.none {
    if (direct == null && relay == null) {
      throw ArgumentError('At least one of direct or relay must be non-null');
    }
  }

  RelayPath get activePath => _active;

  void _markStale() {
    _active = RelayPath.none;
  }

  Future<IReactClient> _resolve() async {
    if (_active != RelayPath.none) {
      final IReactClient? cached = _active == RelayPath.direct
          ? _direct
          : _relay;
      if (cached != null) return cached;
    }

    final Completer<IReactClient> winner = Completer<IReactClient>();
    int outstanding = 0;
    Object? lastError;
    bool pinMismatchSeen = false;

    if (_direct != null) outstanding++;
    if (_relay != null) outstanding++;

    void checkDone() {
      if (outstanding > 0 || winner.isCompleted) return;
      if (pinMismatchSeen) {
        winner.completeError(
          const ReactAuthException(
            'server identity mismatch (possible relay interception)',
          ),
        );
        return;
      }
      winner.completeError(
        lastError ?? const ReactUnavailable('No path available'),
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
        if (RelayIdentity.pinMatches(
          pinnedFingerprint: _pinnedFingerprint,
          identityServerId: observedFingerprint,
        )) {
          await client.identity();
          outstanding--;
          if (!winner.isCompleted) {
            _active = path;
            winner.complete(client);
          }
        } else {
          outstanding--;
          pinMismatchSeen = true;
          lastError = const ReactAuthException(
            'server identity mismatch (possible relay interception)',
          );
          checkDone();
        }
      } on Object catch (error) {
        outstanding--;
        lastError = error;
        checkDone();
      }
    }

    if (_direct != null) {
      unawaited(probe(RelayPath.direct, _direct));
    }
    if (_relay != null) {
      unawaited(probe(RelayPath.relay, _relay));
    }

    return winner.future;
  }

  Future<T> _execute<T>(
    Future<T> Function(IReactClient client) operation,
  ) async {
    final IReactClient client = await _resolve();
    try {
      return await operation(client);
    } on ReactUnavailable {
      _markStale();
      rethrow;
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

  @override
  Future<ServerCapabilities> ping() => _execute((IReactClient client) {
    if (client is IPingClient) return (client as IPingClient).ping();
    throw const ReactUnavailable('Active transport does not support ping');
  });

  @override
  Future<IdentityInfo> identity() =>
      _execute((IReactClient client) => client.identity());

  @override
  Future<ServerSnapshot> metrics() =>
      _execute((IReactClient client) => client.metrics());

  @override
  Future<RoleInfo> whoami() =>
      _execute((IReactClient client) => _roles(client).whoami());

  @override
  Future<List<HeatmapSummary>> heatmaps() =>
      _execute((IReactClient client) => _heatmaps(client).heatmaps());

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerX,
    int? centerZ,
    int? radius,
  }) => _execute(
    (IReactClient client) => _heatmaps(client).heatmap(
      id,
      world: world,
      centerX: centerX,
      centerZ: centerZ,
      radius: radius,
    ),
  );

  @override
  Future<List<ControlItem>> features() =>
      _execute((IReactClient client) => _controls(client).features());

  @override
  Future<ControlItem> feature(String id) =>
      _execute((IReactClient client) => _controls(client).feature(id));

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
      _execute((IReactClient client) => _controls(client).tweaks());

  @override
  Future<ControlItem> tweak(String id) =>
      _execute((IReactClient client) => _controls(client).tweak(id));

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
      _execute((IReactClient client) => _controls(client).worlds());

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
      _execute((IReactClient client) => _operations(client).actions());

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
  Future<IncidentStatus> incidents() =>
      _execute((IReactClient client) => _operations(client).incidents());

  @override
  Future<EnvironmentInfo> environment() =>
      _execute((IReactClient client) => _operations(client).environment());

  @override
  Future<ConfigTree> config() =>
      _execute((IReactClient client) => _operations(client).config());

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) => _execute(
    (IReactClient client) => _operations(client).applyConfig(changes),
  );

  @override
  Future<ConfigTree> applyPreset(String name) =>
      _execute((IReactClient client) => _operations(client).applyPreset(name));

  @override
  Future<List<String>> logs({int limit = 200}) =>
      _execute((IReactClient client) => _operations(client).logs(limit: limit));

  @override
  Future<bool> executeConsole(String command) => _execute(
    (IReactClient client) => _console(client).executeConsole(command),
  );
}
