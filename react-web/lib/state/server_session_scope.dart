import 'package:jaspr/jaspr.dart';

import 'fleet_scope.dart';
import 'server_session.dart';

/// Hands the current server's [ServerSession] to everything below a server
/// page. Read it in `build` (`ServerSessionScope.of(context)`) and never keep
/// a session or controller in State: a different server is a different
/// session.
class ServerSessionScope extends InheritedComponent {
  final ServerSession session;

  const ServerSessionScope({
    required this.session,
    required super.child,
    super.key,
  });

  static ServerSession? of(BuildContext context) => context
      .dependOnInheritedComponentOfExactType<ServerSessionScope>()
      ?.session;

  @override
  bool updateShouldNotify(ServerSessionScope oldComponent) =>
      !identical(session, oldComponent.session);
}

/// Looks up the session for [serverId] in `FleetScope` and scopes [child] to
/// it, keyed by the session so that switching servers (or replacing an
/// entry) rebuilds the whole subtree with fresh State. Renders [missing]
/// when the id is not paired.
class ServerSessionHost extends StatelessComponent {
  final String serverId;
  final Component child;
  final Component Function(BuildContext context) missing;

  const ServerSessionHost({
    required this.serverId,
    required this.child,
    required this.missing,
    super.key,
  });

  @override
  Component build(BuildContext context) {
    final ServerSession? session = FleetScope.of(
      context,
    )?.fleetManager.sessionFor(serverId);
    if (session == null) return missing(context);
    return ServerSessionScope(
      key: ValueKey<String>(session.key),
      session: session,
      child: child,
    );
  }
}
