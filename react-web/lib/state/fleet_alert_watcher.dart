library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../model/alert.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import 'alert_engine.dart';
import 'alert_store.dart';
import 'fleet_live_scope.dart';
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
  int _lastProcessedRevision = -1;
  int? _queuedRevision;
  _FleetAlertCheck? _queuedCheck;
  bool _flushScheduled = false;

  void _scheduleCriticalCheck(BuildContext context) {
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);
    if (liveScope == null || fleet == null) return;
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
    _queuedCheck = null;
    _queuedRevision = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _scheduleCriticalCheck(context);
    return component.child;
  }
}
