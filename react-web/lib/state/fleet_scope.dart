import 'package:jaspr/jaspr.dart';

import '../model/server_credential.dart';
import 'alert_store.dart';
import 'connection_manager.dart';
import 'fleet_manager.dart';
import 'server_tags_store.dart';

abstract interface class FleetController {
  FleetManager get fleetManager;

  AlertStore get alertStore;

  ServerTagsStore get tagsStore;

  ConnectionManager? managerFor(String id);

  String? labelFor(String id);

  void trackPaired(String id);

  void removeServer(String id);

  void clearFleet();

  void importFleet(List<ServerCredential> creds);
}

class FleetScope extends InheritedComponent {
  final FleetController controller;
  final int revision;

  const FleetScope({
    required this.controller,
    required this.revision,
    required super.child,
    super.key,
  });

  static FleetController? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<FleetScope>()?.controller;

  @override
  bool updateShouldNotify(FleetScope oldComponent) =>
      revision != oldComponent.revision;
}
