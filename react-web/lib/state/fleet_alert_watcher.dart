library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../model/alert.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import 'alert_engine.dart';
import 'fleet_live_scope.dart';
import 'fleet_rollup.dart';
import 'fleet_scope.dart';

class FleetAlertWatcher extends StatefulWidget {
  final Widget child;

  const FleetAlertWatcher({required this.child, super.key});

  @override
  State<FleetAlertWatcher> createState() => _FleetAlertWatcherState();
}

class _FleetAlertWatcherState extends State<FleetAlertWatcher> {
  int _lastRevision = -1;

  void _checkForNewCriticals(BuildContext context) {
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);
    if (liveScope == null || fleet == null) return;
    if (liveScope.revision == _lastRevision) return;
    _lastRevision = liveScope.revision;

    final List<({String id, String name, ServerSnapshot? snapshot})> servers =
        liveScope.servers
            .map(
              (FleetServerLive s) =>
                  (id: s.id, name: s.name, snapshot: s.snapshot),
            )
            .toList();

    final AlertThresholds thresholds = fleet.alertStore.thresholds;
    final List<FleetAlert> alerts = AlertEngine.computeFleet(
      servers: servers,
      thresholds: thresholds,
      now: DateTime.now(),
    );

    final Set<String> newKeys = fleet.alertStore.detectNewCritical(alerts);
    final Map<String, FleetAlert> byKey = <String, FleetAlert>{};
    for (final FleetAlert a in alerts) {
      byKey[a.key] = a;
    }
    for (final String key in newKeys) {
      final FleetAlert? alert = byKey[key];
      if (alert != null) {
        ArcaneSonner.error(
          reactorText(ReactorText.alertCriticalNotification),
          description: reactorText(
            ReactorText.alertCriticalDescription,
            <String, Object?>{'server': alert.serverName, 'title': alert.title},
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    _checkForNewCriticals(context);
    return component.child;
  }
}
