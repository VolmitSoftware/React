/// The paired servers as the app sees them: their connections, the toasts on
/// a change, the stores, and the router built over them. [LiveServerScope]
/// hands one server's live snapshot to its pages.
library;

import 'dart:async' show StreamSubscription;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show kIsWeb;
import 'package:jaspr_router/jaspr_router.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import '../screen/pairing/pairing_ended.dart';
import '../service/monotonic_counter.dart';
import '../service/react_client.dart';
import '../service/relay_connection.dart';
import '../service/relay_react_client.dart';
import '../state/alert_store.dart';
import '../state/chart_history.dart';
import '../state/connection_manager.dart';
import '../state/fleet_alert_watcher.dart';
import '../state/fleet_live_model.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../state/memory_fleet_storage.dart';
import '../state/paint_scheduler.dart';
import '../state/server_scope.dart';
import '../state/server_tags_store.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import 'routes.dart';

/// A paired server in the shell: its id, label and connection.
class ServerEntry {
  final String id;
  final String name;
  final ConnState state;

  const ServerEntry({
    required this.id,
    required this.name,
    required this.state,
  });
}

class LiveServerScope extends StatefulWidget {
  final String serverId;
  final ConnectionManager manager;
  final IHistoryClient? historyClient;
  final ChartHistory? charts;
  final Widget child;

  const LiveServerScope({
    required this.serverId,
    required this.manager,
    this.historyClient,
    this.charts,
    required this.child,
    super.key,
  });

  @override
  State<LiveServerScope> createState() => _LiveServerScopeState();
}

class _LiveServerScopeState extends State<LiveServerScope> {
  late PaintScheduler _paint;
  ServerSnapshot? _snapshot;
  late ConnState _state;
  StreamSubscription<ServerSnapshot>? _snapshotSub;
  StreamSubscription<ConnState>? _stateSub;

  @override
  void initState() {
    super.initState();
    _paint = PaintScheduler(() {
      if (mounted) setState(() {});
    });
    _bind(component.manager);
  }

  @override
  void didUpdateWidget(LiveServerScope oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (oldComponent.manager != component.manager) {
      _unbind();
      _bind(component.manager);
    }
  }

  void _bind(ConnectionManager manager) {
    _state = manager.state;
    _snapshot = manager.latestSnapshot;
    _snapshotSub = manager.snapshots.listen((ServerSnapshot snap) {
      if (!mounted || !identical(manager, component.manager)) return;
      _snapshot = snap;
      _paint.schedule();
    });
    _stateSub = manager.stateChanges.listen((ConnState next) {
      if (!mounted || !identical(manager, component.manager)) return;
      _state = next;
      _paint.schedule();
    });
  }

  void _unbind() {
    _snapshotSub?.cancel();
    _stateSub?.cancel();
    _snapshotSub = null;
    _stateSub = null;
  }

  @override
  void dispose() {
    _paint.dispose();
    _unbind();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final bool degraded = _state == ConnState.degraded;
    final bool offline = _state == ConnState.offline;
    // An ended pairing never reconnects; only a new link helps.
    final bool ended = component.manager.access == ConnAccess.revoked;
    return ServerScope(
      snapshot: _snapshot,
      state: _state,
      pairingEnded: ended,
      historyClient: component.historyClient,
      charts: component.charts,
      child: dom.div(classes: 'ui-stack', <Widget>[
        if ((degraded || offline) && !ended)
          AppAlert(
            tone: offline ? AlertTone.error : AlertTone.warning,
            title: offline
                ? reactorText(ReactorText.startNotReachable)
                : reactorText(ReactorText.shellConnectionDegraded),
            message: switch ((offline, _snapshot == null)) {
              (false, true) => reactorText(
                ReactorText.shellConnectionNoSnapshotRecovering,
              ),
              (false, false) => reactorText(
                ReactorText.shellConnectionShowingSnapshotRecovering,
              ),
              (true, true) => reactorText(
                ReactorText.shellConnectionNoSnapshotRetrying,
              ),
              (true, false) => reactorText(
                ReactorText.shellConnectionShowingSnapshotRetrying,
              ),
            },
            live: true,
            action: ActionRow.footer(
              dense: true,
              secondary: <RowAction>[
                RowAction(
                  reactorText(ReactorText.shellReconnect),
                  component.manager.start,
                ),
              ],
            ),
          ),
        component.child,
        // The page keeps its title only; the notice says what happened.
        if (ended)
          PairingEndedNotice(
            serverId: component.serverId,
            since: _snapshot?.at,
          ),
      ]),
    );
  }
}

class _ActiveServer {
  final String id;
  final String name;
  final ConnectionManager manager;
  ConnState state;
  late StreamSubscription<ConnTransition> sub;

  _ActiveServer({
    required this.id,
    required this.name,
    required this.manager,
    required this.state,
  });
}

class ReactorFleetObserver extends StatefulWidget {
  final FleetManager? fleetManager;

  const ReactorFleetObserver({this.fleetManager, super.key});

  @override
  State<ReactorFleetObserver> createState() => ReactorFleetObserverState();
}

class ReactorFleetObserverState extends State<ReactorFleetObserver>
    implements FleetController {
  final List<_ActiveServer> _servers = <_ActiveServer>[];
  late final FleetManager _fleet;
  late final AlertStore _alertStore;
  late final ServerTagsStore _tagsStore;
  int _revision = 0;

  /// Built once: a new Router per fleet change re-matched the address while
  /// a navigation was in flight and corrupted the page (H1).
  late final Router _router = Router(routes: buildReactorShellRoutes());

  @override
  FleetManager get fleetManager => _fleet;

  @override
  AlertStore get alertStore => _alertStore;

  @override
  ServerTagsStore get tagsStore => _tagsStore;

  @override
  void initState() {
    super.initState();
    if (component.fleetManager != null) {
      _fleet = component.fleetManager!;
    } else {
      final FleetStorage storage = InMemoryFleetStorage();
      _fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential cred) =>
            ReactClient(cred, counter: MonotonicCounter(storage)),
        relayClientFactory: (ServerCredential cred) {
          if (!kIsWeb) return null;
          final String? fp = cred.fingerprint;
          if (fp == null) return null;
          final String? ru = cred.relayUrl;
          if (ru == null || ru.isEmpty) return null;
          return RelayReactClient(
            createRelayConnection(ru, fp),
            cred,
            counter: MonotonicCounter(storage),
          );
        },
      );
    }
    _alertStore = AlertStore(_fleet.storage);
    _tagsStore = ServerTagsStore(_fleet.storage);
    for (final ServerCredential cred in _fleet.servers) {
      _beginTracking(cred.id, cred.label);
    }
  }

  @override
  ConnectionManager? managerFor(String id) => _fleet.managerFor(id);

  @override
  String? labelFor(String id) {
    final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
    return idx < 0 ? null : _servers[idx].name;
  }

  @override
  void trackPaired(String id) {
    final ServerCredential? cred = _credentialFor(id);
    if (cred == null) return;
    _beginTracking(id, cred.label);
  }

  ServerCredential? _credentialFor(String id) {
    for (final ServerCredential cred in _fleet.servers) {
      if (cred.id == id) return cred;
    }
    return null;
  }

  void _beginTracking(String id, String name) {
    final ConnectionManager? manager = _fleet.managerFor(id);
    if (manager == null) return;
    final int tracked = _servers.indexWhere((_ActiveServer s) => s.id == id);
    if (tracked >= 0) {
      if (identical(_servers[tracked].manager, manager)) return;
      // Replaced in place by a new code: the old connection is disposed.
      _servers.removeAt(tracked).sub.cancel();
    }
    final _ActiveServer entry = _ActiveServer(
      id: id,
      name: name,
      manager: manager,
      state: manager.state,
    );
    entry.sub = manager.transitions.listen(
      (ConnTransition next) => _onTransition(id, name, next),
    );
    setState(() {
      _servers.add(entry);
      _revision++;
    });
    manager.start();
  }

  @override
  void removeServer(String id) {
    final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
    if (idx >= 0) {
      _servers[idx].sub.cancel();
    }
    _fleet.remove(id);
    _tagsStore.setTags(id, const <String>[]);
    _alertStore.clearServer(id);
    setState(() {
      if (idx >= 0) {
        _servers.removeAt(idx);
      }
      _revision++;
    });
  }

  @override
  void clearFleet() {
    for (final _ActiveServer server in _servers) {
      server.sub.cancel();
    }
    _fleet.clearAll();
    setState(() {
      _servers.clear();
      _revision++;
    });
  }

  @override
  void importFleet(List<ServerCredential> creds) {
    for (final _ActiveServer server in _servers) {
      server.sub.cancel();
    }
    _fleet.importReplace(creds);
    final List<_ActiveServer> rebuilt = <_ActiveServer>[];
    for (final ServerCredential cred in _fleet.servers) {
      final ConnectionManager? manager = _fleet.managerFor(cred.id);
      if (manager == null) continue;
      final _ActiveServer entry = _ActiveServer(
        id: cred.id,
        name: cred.label,
        manager: manager,
        state: manager.state,
      );
      entry.sub = manager.transitions.listen(
        (ConnTransition next) => _onTransition(cred.id, cred.label, next),
      );
      rebuilt.add(entry);
    }
    setState(() {
      _servers
        ..clear()
        ..addAll(rebuilt);
      _revision++;
    });
    for (final _ActiveServer entry in rebuilt) {
      entry.manager.start();
    }
  }

  void _onTransition(String id, String name, ConnTransition transition) {
    final ConnState next = transition.to;
    // Only changes toast: a server found down or a pairing found ended when
    // the page loads already says so in its row and on its pages.
    if (transition.wasLive) _toast(id, name, transition);
    setState(() {
      final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
      if (idx >= 0) {
        _servers[idx].state = next;
      }
      _revision++;
    });
  }

  void _toast(String id, String name, ConnTransition transition) {
    final ConnState next = transition.to;
    if (next == ConnState.offline &&
        _fleet.managerFor(id)?.access == ConnAccess.revoked) {
      // Not a server going away: only a new link brings it back.
      ArcaneSonner.error(reactorText(ReactorText.pairEnded), description: name);
    } else if (next == ConnState.offline) {
      ArcaneSonner.error(
        reactorText(ReactorText.shellServerOffline),
        description: name,
      );
    } else if (next == ConnState.degraded) {
      ArcaneSonner.warning(
        reactorText(ReactorText.shellConnectionDegraded),
        description: name,
      );
    } else if (transition.reconnected) {
      ArcaneSonner.success(
        reactorText(ReactorText.shellServerReconnected),
        description: name,
      );
    }
  }

  @override
  void dispose() {
    for (final _ActiveServer s in _servers) {
      s.sub.cancel();
    }
    _fleet.dispose();
    super.dispose();
  }

  /// One source per entry with fingerprint, role, transport and access, so
  /// rollups count a server paired twice once.
  List<FleetLiveSource> _buildSources() => _fleet.liveSources();

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return FleetScope(
      controller: this,
      revision: _revision,
      child: FleetLiveObserver(
        sources: _buildSources(),
        child: FleetAlertWatcher(child: _router),
      ),
    );
  }
}
