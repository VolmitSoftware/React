library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../model/alert.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import 'alert_engine.dart';
import 'alert_store.dart';
import 'connection_manager.dart';
import 'fleet_live_scope.dart';
import 'fleet_live_model.dart';
import 'fleet_rollup.dart';
import 'fleet_scope.dart';

void _showCriticalAlert(FleetAlert alert) {
  ArcaneSonner.error(
    reactorText(ReactorText.alertCriticalNotification),
    description: reactorText(
      ReactorText.alertCriticalDescription,
      <String, Object?>{'server': alert.serverName, 'title': alert.title},
    ),
  );
}

class _FleetAlertCheck {
  final int revision;
  final List<({String id, String name, ServerSnapshot? snapshot})> servers;
  final AlertThresholds thresholds;
  final AlertStore store;

  const _FleetAlertCheck({
    required this.revision,
    required this.servers,
    required this.thresholds,
    required this.store,
  });
}

class FleetAlertWatcher extends StatefulWidget {
  final Widget child;
  final void Function(FleetAlert alert) notifyCritical;

  const FleetAlertWatcher({
    required this.child,
    this.notifyCritical = _showCriticalAlert,
    super.key,
  });

  @override
  State<FleetAlertWatcher> createState() => _FleetAlertWatcherState();
}

class _FleetAlertWatcherState extends State<FleetAlertWatcher> {
  FleetLiveModel? _model;
  StreamSubscription<String?>? _changeSub;
  AlertStore? _store;
  AlertThresholds? _thresholds;
  final Map<String, ServerSnapshot?> _checkedSnapshots =
      <String, ServerSnapshot?>{};
  final Map<String, ConnState> _checkedStates = <String, ConnState>{};
  int _lastProcessedRevision = -1;
  int? _queuedRevision;
  _FleetAlertCheck? _queuedCheck;
  bool _flushScheduled = false;

  void _scheduleCriticalCheck(BuildContext context) {
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);
    if (liveScope == null || fleet == null) return;
    final FleetLiveModel? model = liveScope.model;
    if (model != null) {
      final bool changed =
          !identical(_model, model) ||
          !identical(_thresholds, fleet.alertStore.thresholds);
      if (!identical(_model, model)) {
        _changeSub?.cancel();
        _model = model;
        _changeSub = model.changes.listen(_processChange);
      }
      _store = fleet.alertStore;
      _thresholds = fleet.alertStore.thresholds;
      if (changed) {
        _checkedSnapshots.clear();
        _checkedStates.clear();
        context.binding.addPostFrameCallback(() => _processChange(null));
      }
      return;
    }
    final int revision = liveScope.revision;
    if (revision == _lastProcessedRevision || revision == _queuedRevision) {
      return;
    }

    final List<({String id, String name, ServerSnapshot? snapshot})> servers =
        liveScope.servers
            .map(
              (FleetServerLive s) =>
                  (id: s.id, name: s.name, snapshot: currentFleetSnapshot(s)),
            )
            .toList();

    _queuedRevision = revision;
    _queuedCheck = _FleetAlertCheck(
      revision: revision,
      servers: servers,
      thresholds: fleet.alertStore.thresholds,
      store: fleet.alertStore,
    );
    if (_flushScheduled) return;
    _flushScheduled = true;
    context.binding.addPostFrameCallback(_flushCriticalCheck);
  }

  void _processChange(String? serverId) {
    final FleetLiveModel? model = _model;
    final AlertStore? store = _store;
    final AlertThresholds? thresholds = _thresholds;
    if (!mounted || model == null || store == null || thresholds == null) {
      return;
    }
    if (serverId == null) {
      final Set<String> liveIds = <String>{};
      for (final FleetServerLive server in model.servers) {
        liveIds.add(server.id);
        _processServer(server, store, thresholds);
      }
      for (final String removed in _checkedSnapshots.keys.toList()) {
        if (!liveIds.contains(removed)) {
          store.detectNewCriticalForServer(removed, const <FleetAlert>[]);
          _checkedSnapshots.remove(removed);
          _checkedStates.remove(removed);
        }
      }
    } else {
      final FleetServerLive? server = model.server(serverId);
      if (server != null) _processServer(server, store, thresholds);
    }
  }

  void _processServer(
    FleetServerLive server,
    AlertStore store,
    AlertThresholds thresholds,
  ) {
    final ServerSnapshot? snapshot = currentFleetSnapshot(server);
    if (_checkedSnapshots.containsKey(server.id) &&
        identical(_checkedSnapshots[server.id], snapshot) &&
        _checkedStates[server.id] == server.state) {
      return;
    }
    _checkedSnapshots[server.id] = snapshot;
    _checkedStates[server.id] = server.state;
    final List<FleetAlert> alerts = AlertEngine.computeForServer(
      serverId: server.id,
      serverName: server.name,
      snapshot: snapshot,
      thresholds: thresholds,
      now: DateTime.now(),
    );
    final Set<String> keys = store.detectNewCriticalForServer(
      server.id,
      alerts,
    );
    for (final FleetAlert alert in alerts) {
      if (keys.contains(alert.key)) component.notifyCritical(alert);
    }
  }

  void _flushCriticalCheck() {
    _flushScheduled = false;
    final _FleetAlertCheck? check = _queuedCheck;
    _queuedCheck = null;
    _queuedRevision = null;
    if (!mounted || check == null) return;
    if (check.revision == _lastProcessedRevision) return;
    _lastProcessedRevision = check.revision;

    final List<FleetAlert> alerts = AlertEngine.computeFleet(
      servers: check.servers,
      thresholds: check.thresholds,
      now: DateTime.now(),
    );

    final Set<String> newKeys = check.store.detectNewCritical(alerts);
    final Map<String, FleetAlert> byKey = <String, FleetAlert>{};
    for (final FleetAlert a in alerts) {
      byKey[a.key] = a;
    }
    final void Function(FleetAlert alert) notifyCritical =
        component.notifyCritical;
    for (final String key in newKeys) {
      final FleetAlert? alert = byKey[key];
      if (alert != null) {
        notifyCritical(alert);
      }
    }
  }

  @override
  void dispose() {
    _changeSub?.cancel();
    _model = null;
    _queuedCheck = null;
    _queuedRevision = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    _scheduleCriticalCheck(context);
    return component.child;
  }
}
