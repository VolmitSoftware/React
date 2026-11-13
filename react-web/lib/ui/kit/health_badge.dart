/// The one state vocabulary: Healthy, Warning, Critical, Offline, each in
/// one tone, from the shared verdict ([assessHealth]). Tiles, tables and
/// the Overview verdict all draw their state with these.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_localizations.dart';
import '../../model/health.dart';
import '../../state/fleet_rollup.dart';
import 'badge.dart';

BadgeTone levelTone(HealthLevel level) => switch (level) {
  HealthLevel.healthy => BadgeTone.ok,
  HealthLevel.warning => BadgeTone.warning,
  HealthLevel.critical => BadgeTone.error,
  HealthLevel.unknown => BadgeTone.neutral,
};

/// The state in words where it needs saying: Warning or Critical.
String? levelWord(HealthLevel level) => switch (level) {
  HealthLevel.warning => reactorText(ReactorText.commonWarning),
  HealthLevel.critical => reactorText(ReactorText.statusCritical),
  _ => null,
};

BadgeTone healthTone(FleetHealth health) => switch (health) {
  FleetHealth.healthy => BadgeTone.ok,
  FleetHealth.warning => BadgeTone.warning,
  FleetHealth.critical || FleetHealth.offline => BadgeTone.error,
  FleetHealth.pending => BadgeTone.neutral,
};

String healthWord(FleetHealth health) => switch (health) {
  FleetHealth.healthy => reactorText(ReactorText.commonHealthy),
  FleetHealth.warning => reactorText(ReactorText.commonWarning),
  FleetHealth.critical => reactorText(ReactorText.statusCritical),
  FleetHealth.offline => reactorText(ReactorText.startNotReachable),
  FleetHealth.pending => reactorText(ReactorText.fleetAwaitingTelemetry),
};

/// A server's state as a badge.
class HealthBadge extends StatelessWidget {
  const HealthBadge(this.health, {super.key});

  final FleetHealth health;

  @override
  Widget build(BuildContext context) =>
      StatusBadge(healthWord(health), tone: healthTone(health));
}
