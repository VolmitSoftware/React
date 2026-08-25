import 'package:jaspr/jaspr.dart';

import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import 'connection_manager.dart';

class ServerScope extends InheritedComponent {
  final ServerSnapshot? snapshot;
  final ConnState state;
  final IHistoryClient? historyClient;

  const ServerScope({
    required this.snapshot,
    required this.state,
    this.historyClient,
    required super.child,
    super.key,
  });

  static ServerScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ServerScope>();

  @override
  bool updateShouldNotify(ServerScope oldComponent) =>
      state != oldComponent.state ||
      historyClient != oldComponent.historyClient ||
      snapshot?.seq != oldComponent.snapshot?.seq;
}
