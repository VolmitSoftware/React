import 'package:jaspr/jaspr.dart';

import '../model/server_snapshot.dart';
import 'connection_manager.dart';

class ServerScope extends InheritedComponent {
  final ServerSnapshot? snapshot;
  final ConnState state;

  const ServerScope({
    required this.snapshot,
    required this.state,
    required super.child,
    super.key,
  });

  static ServerScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ServerScope>();

  @override
  bool updateShouldNotify(ServerScope oldComponent) =>
      state != oldComponent.state ||
      snapshot?.seq != oldComponent.snapshot?.seq;
}
