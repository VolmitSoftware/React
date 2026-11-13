import 'dart:async';

import '../model/server_credential.dart';
import '../service/cached_role_client.dart';
import '../service/happy_eyeballs_client.dart';
import '../service/react_client.dart';
import '../service/react_log_socket.dart';
import 'actions_controller.dart';
import 'chart_history.dart';
import 'config_controller.dart';
import 'connection_manager.dart';
import 'control_list_controller.dart';
import 'fleet_manager.dart';
import 'log_controller.dart';
import 'plugin_api_packs_controller.dart';
import 'revision_feed.dart';
import 'sampler_catalog.dart';
import 'world_overrides_controller.dart';

/// Everything the app holds for one paired entry: its client, live
/// connection, revision feed and data controllers. Owned by `FleetManager`,
/// created once per entry and disposed when the entry is removed or
/// replaced, so a controller can only ever talk to the server it was made
/// for. Screens read it from `ServerSessionScope` in `build`.
class ServerSession {
  ServerCredential _credential;
  final IReactClient client;
  final ConnectionManager connection;
  final RevisionFeed revisions;
  final ILogSocket Function()? logSocketFactory;

  /// Where the chart range pick is kept between visits.
  final FleetStorage? storage;

  /// Distinguishes this session from an earlier one with the same id (an
  /// entry replaced by a new pairing). Use it to key the server page.
  final String key;

  ControlListController? _features;
  ControlListController? _tweaks;
  WorldOverridesController? _worlds;
  ConfigController? _config;
  ActionsController? _actions;
  PluginApiPacksController? _packs;
  LogController? _logs;
  SamplerCatalog? _catalog;
  ChartHistory? _charts;
  CachedRoleClient? _roles;
  bool _disposed = false;

  static int _epoch = 0;

  ServerSession({
    required ServerCredential credential,
    required this.client,
    required this.connection,
    this.logSocketFactory,
    this.storage,
  }) : _credential = credential,
       key = '${credential.id}#${++_epoch}',
       revisions = RevisionFeed(connection.snapshots);

  String get serverId => _credential.id;
  ServerCredential get credential => _credential;
  String get label => _credential.label;
  String? get fingerprint => _credential.fingerprint;
  String get tokenId => _credential.tokenId;
  String? get role => _credential.role;
  ConnAccess get access => connection.access;
  bool get disposed => _disposed;

  /// How the entry reaches its server: as paired, and for dual entries the
  /// path in use right now.
  ServerTransport get transport => _credential.transport;

  RelayPath get activePath {
    final IReactClient c = client;
    if (c is HappyEyeballsClient) return c.activePath;
    return _credential.hasDirect ? RelayPath.direct : RelayPath.relay;
  }

  void updateCredential(ServerCredential next) {
    assert(next.id == _credential.id);
    _credential = next;
  }

  T _capability<T>() {
    final IReactClient c = client;
    if (c is T) return c as T;
    throw StateError('${_credential.id} does not support $T');
  }

  bool supports<T>() => client is T;

  /// The client as one capability (heatmaps, players, history, incidents,
  /// console), or null when this transport does not offer it. Bound to this
  /// server like everything else on the session.
  T? clientAs<T>() {
    final IReactClient c = client;
    return c is T ? c as T : null;
  }

  ControlListController get features => _features ??= ControlListController(
    _capability<IControlClient>(),
    isTweaks: false,
    serverId: serverId,
    revisions: revisions,
  );

  ControlListController get tweaks => _tweaks ??= ControlListController(
    _capability<IControlClient>(),
    isTweaks: true,
    serverId: serverId,
    revisions: revisions,
  );

  WorldOverridesController get worlds => _worlds ??= WorldOverridesController(
    _capability<IControlClient>(),
    serverId: serverId,
    revisions: revisions,
  );

  ConfigController get config => _config ??= ConfigController(
    _capability<IConfigClient>(),
    serverId: serverId,
    revisions: revisions,
  );

  ActionsController get actions => _actions ??= ActionsController(
    _capability<IActionClient>(),
    serverId: serverId,
    revisions: revisions,
  );

  PluginApiPacksController get packs => _packs ??= PluginApiPacksController(
    _capability<IPluginApiPackClient>(),
    serverId: serverId,
    revisions: revisions,
  );

  /// Opens its socket when the first listener attaches and closes it 30 s
  /// after the last one leaves.
  LogController get logs => _logs ??= LogController(
    _capability<ILogClient>(),
    socketFactory: logSocketFactory,
    autoSuspend: true,
  );

  /// `/whoami` for this server, asked once for every page; null when the
  /// transport has no role route.
  CachedRoleClient? get roles {
    final IRoleClient? client = clientAs<IRoleClient>();
    if (client == null) return null;
    return _roles ??= CachedRoleClient(client);
  }

  /// The samplers this server cannot report; loads when a page listens.
  SamplerCatalog get catalog =>
      _catalog ??= SamplerCatalog(clientAs<IHistoryClient>());

  /// The chart range of Overview and Performance and its stored ranges;
  /// null when the transport has no history routes.
  ChartHistory? get charts {
    final IHistoryClient? history = clientAs<IHistoryClient>();
    if (history == null) return null;
    return _charts ??= ChartHistory(
      history,
      connection.snapshots,
      storage: storage,
    );
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _catalog?.dispose();
    _charts?.dispose();
    _features?.dispose();
    _tweaks?.dispose();
    _worlds?.dispose();
    _config?.dispose();
    _actions?.dispose();
    _packs?.dispose();
    _logs?.dispose();
    revisions.dispose();
    connection.dispose();
    unawaited(client.close());
  }
}
