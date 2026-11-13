/// The route table: one shell around every page, the server group's pages,
/// and each server's pages inside that server's scopes.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../screen/pages/actions_page.dart';
import '../screen/pages/activity_page.dart';
import '../screen/pages/alerts_page.dart';
import '../screen/pages/console_page.dart';
import '../screen/pages/fleet_page.dart';
import '../screen/pages/incidents_page.dart';
import '../screen/pages/map_page.dart';
import '../screen/pages/metrics_page.dart';
import '../screen/pages/overview_page.dart';
import '../screen/pages/pair_page.dart';
import '../screen/pages/performance_page.dart';
import '../screen/pages/plugins_page.dart';
import '../screen/pages/settings_page.dart';
import '../screen/pages/start_page.dart';
import '../screen/pages/tuning_page.dart';
import '../state/connection_manager.dart';
import '../state/fleet_scope.dart';
import '../state/role_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import 'app_shell.dart';
import 'fleet_observer.dart';
import 'paths.dart';

List<RouteBase> buildReactorRoutes() => <RouteBase>[
  Route(
    path: kRouteRoot,
    builder: (BuildContext context, RouteState state) => const StartPage(),
  ),
  Route(
    path: kRoutePair,
    builder: (BuildContext context, RouteState state) => const PairPage(),
  ),
  Route(
    path: kRouteFleet,
    builder: (BuildContext context, RouteState state) => const FleetPage(),
  ),
  Route(
    path: kRouteAlerts,
    builder: (BuildContext context, RouteState state) => const AlertsPage(),
  ),
  Route(
    path: kRouteSettings,
    builder: (BuildContext context, RouteState state) => const SettingsPage(),
  ),
  _server(kRouteServer, const OverviewPage()),
  _server(kRouteServerPerformance, const PerformancePage()),
  _server(kRouteServerActivity, const ActivityPage()),
  _server(kRouteServerMap, const MapPage()),
  _server(kRouteServerIncidents, const IncidentsPage()),
  _server(kRouteServerConsole, const ConsolePage()),
  _server(kRouteServerActions, const ActionsPage()),
  _server(kRouteServerTuning, const TuningPage()),
  _server(kRouteServerPlugins, const PluginsPage()),
  _server(kRouteServerMetrics, const MetricsPage()),
];

Route _server(String path, Widget page) => Route(
  path: path,
  builder: (BuildContext context, RouteState state) =>
      ServerScopes(id: state.params['id'], child: page),
);

List<RouteBase> buildReactorShellRoutes() => <RouteBase>[
  ShellRoute(
    builder: (BuildContext context, RouteState state, Widget child) =>
        AppShell(currentPath: state.location, body: child),
    routes: buildReactorRoutes(),
  ),
];

/// A server's scopes around its page: its session, role and the live
/// snapshot, keyed by the session so a server switch gives the page fresh
/// State. An unknown id shows an empty state with a way home.
class ServerScopes extends StatelessWidget {
  const ServerScopes({required this.id, required this.child, super.key});

  final String? id;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final String? serverId = id;
    final FleetController? fleet = FleetScope.of(context);
    final ServerSession? session = serverId == null
        ? null
        : fleet?.fleetManager.sessionFor(serverId);
    final ConnectionManager? manager = session?.connection;
    if (fleet == null ||
        serverId == null ||
        session == null ||
        manager == null) {
      return AppPage(
        title: reactorText(ReactorText.shellServerNotConnected),
        children: <Widget>[
          EmptyState(
            message: reactorText(ReactorText.shellServerNotConnected),
            description: reactorText(
              ReactorText.shellServerNotConnectedDescription,
            ),
            action: ActionRow.footer(
              primary: RowAction(
                reactorText(ReactorText.pageHomeTitle),
                null,
                href: kRouteRoot,
              ),
            ),
          ),
        ],
      );
    }
    return ServerSessionScope(
      key: ValueKey<String>(session.key),
      session: session,
      child: RoleObserver(
        client: session.roles,
        child: LiveServerScope(
          serverId: serverId,
          manager: manager,
          historyClient: fleet.fleetManager.historyClientFor(serverId),
          charts: session.charts,
          child: child,
        ),
      ),
    );
  }
}
