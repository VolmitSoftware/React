import 'package:jaspr/jaspr.dart';

import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import 'chart_history.dart';
import 'connection_manager.dart';

class ServerScope extends InheritedComponent {
  final ServerSnapshot? snapshot;
  final ConnState state;
  final IHistoryClient? historyClient;

  /// The chart range and stored ranges of the server; null without history.
  final ChartHistory? charts;

  /// The server no longer accepts this entry's pairing.
  final bool pairingEnded;

  const ServerScope({
    required this.snapshot,
    required this.state,
    this.historyClient,
    this.charts,
    this.pairingEnded = false,
    required super.child,
    super.key,
  });

  static ServerScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ServerScope>();

  @override
  bool updateShouldNotify(ServerScope oldComponent) =>
      state != oldComponent.state ||
      pairingEnded != oldComponent.pairingEnded ||
      historyClient != oldComponent.historyClient ||
      charts != oldComponent.charts ||
      snapshot?.seq != oldComponent.snapshot?.seq;
}
