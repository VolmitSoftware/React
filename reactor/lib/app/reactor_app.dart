library;

import 'dart:async' show StreamSubscription;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback, kIsWeb;
import 'package:jaspr_router/jaspr_router.dart';

import '../model/server_credential.dart';
import '../localization/reactor_localizations.dart';
import '../model/server_snapshot.dart';
import '../screen/actions.dart';
import '../screen/add_server.dart';
import '../screen/alerts_inbox.dart';
import '../screen/fleet_dashboard.dart';
import '../screen/config_editor.dart';
import '../screen/environment.dart';
import '../screen/chunks.dart';
import '../screen/entities.dart';
import '../screen/events.dart';
import '../screen/incident_center.dart';
import '../screen/logs.dart';
import '../screen/incidents.dart';
import '../screen/governors.dart';
import '../screen/heatmaps.dart';
import '../screen/integrations.dart';
import '../screen/optimization.dart';
import '../screen/tweaks.dart';
import '../screen/internals.dart';
import '../screen/world_overrides.dart';
import '../screen/worlds.dart';
import '../screen/mechanics.dart';
import '../screen/comparison.dart';
import '../screen/memory.dart';
import '../screen/settings.dart';
import '../screen/overview.dart';
import '../screen/performance.dart';
import '../service/monotonic_counter.dart';
import '../service/react_client.dart';
import '../service/relay_connection.dart';
import '../service/relay_react_client.dart';
import '../state/alert_store.dart';
import '../state/connection_manager.dart';
import '../state/fleet_alert_watcher.dart';
import '../state/fleet_live_model.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../state/memory_fleet_storage.dart';
import '../state/server_tags_store.dart';
import '../state/control_scope.dart';
import '../state/heatmap_scope.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/status_dot.dart';

const String kUplotCssUrl =
    'https://cdn.jsdelivr.net/npm/uplot@1.6.31/dist/uPlot.min.css';
const String kUplotJsUrl =
    'https://cdn.jsdelivr.net/npm/uplot@1.6.31/dist/uPlot.iife.min.js';

class ServerEntry {
  final String id;
  final String name;
  final ConnState state;

  const ServerEntry({
    required this.id,
    required this.name,
    required this.state,
  });
}

const String kRouteRoot = '/';
const String kRouteSettings = '/settings';
const String kRouteAddServer = '/add-server';
const String kRouteAlerts = '/alerts';
const String kRouteComparison = '/comparison';
const String kRouteServerOverview = '/server/:id/overview';
const String kRouteServerPerformance = '/server/:id/performance';
const String kRouteServerMemory = '/server/:id/memory';
const String kRouteServerEntities = '/server/:id/entities';
const String kRouteServerChunks = '/server/:id/chunks';
const String kRouteServerMechanics = '/server/:id/mechanics';
const String kRouteServerEvents = '/server/:id/events';
const String kRouteServerInternals = '/server/:id/internals';
const String kRouteServerIncidents = '/server/:id/incidents';
const String kRouteServerWorlds = '/server/:id/worlds';
const String kRouteServerIntegrations = '/server/:id/integrations';
const String kRouteServerHeatmaps = '/server/:id/heatmaps';
const String kRouteServerOptimization = '/server/:id/optimization';
const String kRouteServerTweaks = '/server/:id/tweaks';
const String kRouteServerGovernors = '/server/:id/governors';
const String kRouteServerWorldOverrides = '/server/:id/world-overrides';
const String kRouteServerActions = '/server/:id/actions';
const String kRouteServerIncidentCenter = '/server/:id/incident-center';
const String kRouteServerEnvironment = '/server/:id/environment';
const String kRouteServerConfig = '/server/:id/config';
const String kRouteServerLogs = '/server/:id/logs';

List<RouteBase> buildReactorRoutes() => <RouteBase>[
  Route(
    path: kRouteRoot,
    builder: (BuildContext ctx, RouteState state) => _buildFleetRoot(ctx),
  ),
  Route(
    path: kRouteSettings,
    builder: (BuildContext ctx, RouteState state) => const SettingsScreen(),
  ),
  Route(
    path: kRouteAddServer,
    builder: (BuildContext ctx, RouteState state) => _buildAddServer(ctx),
  ),
  Route(
    path: kRouteAlerts,
    builder: (BuildContext ctx, RouteState state) => const AlertsInboxScreen(),
  ),
  Route(
    path: kRouteComparison,
    builder: (BuildContext ctx, RouteState state) => const ComparisonScreen(),
  ),
  Route(
    path: kRouteServerOverview,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const OverviewScreen()),
  ),
  Route(
    path: kRouteServerPerformance,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const PerformanceScreen()),
  ),
  Route(
    path: kRouteServerMemory,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const MemoryScreen()),
  ),
  Route(
    path: kRouteServerEntities,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const EntitiesScreen()),
  ),
  Route(
    path: kRouteServerChunks,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const ChunksScreen()),
  ),
  Route(
    path: kRouteServerMechanics,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const MechanicsScreen()),
  ),
  Route(
    path: kRouteServerEvents,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const EventsScreen()),
  ),
  Route(
    path: kRouteServerInternals,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const InternalsScreen()),
  ),
  Route(
    path: kRouteServerIncidents,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const IncidentsScreen()),
  ),
  Route(
    path: kRouteServerWorlds,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const WorldsScreen()),
  ),
  Route(
    path: kRouteServerIntegrations,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const IntegrationsScreen()),
  ),
  Route(
    path: kRouteServerHeatmaps,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const HeatmapsScreen()),
  ),
  Route(
    path: kRouteServerOptimization,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const OptimizationScreen()),
  ),
  Route(
    path: kRouteServerTweaks,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const TweaksScreen()),
  ),
  Route(
    path: kRouteServerGovernors,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const GovernorsScreen()),
  ),
  Route(
    path: kRouteServerWorldOverrides,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const WorldOverridesScreen()),
  ),
  Route(
    path: kRouteServerActions,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const ActionsScreen()),
  ),
  Route(
    path: kRouteServerIncidentCenter,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const IncidentCenterScreen()),
  ),
  Route(
    path: kRouteServerEnvironment,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const EnvironmentScreen()),
  ),
  Route(
    path: kRouteServerConfig,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const ConfigEditorScreen()),
  ),
  Route(
    path: kRouteServerLogs,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const LogsScreen()),
  ),
];

List<RouteBase> buildReactorShellRoutes({
  List<ServerEntry> servers = const <ServerEntry>[],
  VoidCallback? onReconnect,
}) => <RouteBase>[
  ShellRoute(
    builder: (BuildContext context, RouteState state, Component child) =>
        ReactorShell(
          servers: servers,
          onReconnect: onReconnect,
          currentPath: state.location,
          body: child,
        ),
    routes: buildReactorRoutes(),
  ),
];

Widget _buildFleetRoot(BuildContext ctx) {
  final FleetLiveScope? scope = FleetLiveScope.of(ctx);
  if (scope == null || scope.servers.isEmpty) {
    return const _FirstRunFleetView();
  }
  return const FleetDashboardScreen();
}

Widget _buildAddServer(BuildContext context) {
  final FleetController? fleet = FleetScope.of(context);
  if (fleet == null) {
    return ArcaneEmptyState.noData(
      title: reactorText(ReactorText.shellFleetUnavailable),
      description: reactorText(ReactorText.shellFleetUnavailableDescription),
    );
  }
  return AddServerScreen(fleetManager: fleet.fleetManager);
}

Widget _buildServerPage(BuildContext context, RouteState state, Widget screen) {
  final String? id = state.params['id'];
  final FleetController? fleet = FleetScope.of(context);
  final ConnectionManager? manager = id == null ? null : fleet?.managerFor(id);
  if (manager == null) {
    return ArcaneEmptyState.noData(
      title: reactorText(ReactorText.shellServerNotConnected),
      description: reactorText(ReactorText.shellServerNotConnectedDescription),
    );
  }
  return RoleObserver(
    client: fleet!.fleetManager.roleClientFor(id!),
    child: OperateScope(
      client: fleet.fleetManager.operateClientFor(id),
      logSocketFactory: fleet.fleetManager.logSocketFactoryFor(id),
      child: ControlScope(
        client: fleet.fleetManager.controlClientFor(id),
        child: HeatmapScope(
          client: fleet.fleetManager.heatmapClientFor(id),
          child: LiveServerScope(manager: manager, child: screen),
        ),
      ),
    ),
  );
}

class ReactorApp extends StatelessWidget {
  final FleetManager? fleetManager;

  const ReactorApp({this.fleetManager, super.key});

  @override
  Widget build(BuildContext context) {
    return ArcaneApp(
      brightness: Brightness.dark,
      stylesheet: const ShadcnStylesheet(theme: ShadcnTheme.midnight),
      head: <Widget>[
        const dom.link(href: kUplotCssUrl, rel: 'stylesheet'),
        const dom.script(src: kUplotJsUrl),
        Component.element(
          tag: 'style',
          children: const <Component>[Component.text(kReactorNavCss)],
        ),
      ],
      home: Component.fragment(<Widget>[
        ReactorFleetObserver(fleetManager: fleetManager),
        const ArcaneSonner(),
      ]),
      title: reactorText(ReactorText.appTitle),
      description: reactorText(ReactorText.appDescription),
    );
  }
}

class LiveServerScope extends StatefulWidget {
  final ConnectionManager manager;
  final Widget child;

  const LiveServerScope({
    required this.manager,
    required this.child,
    super.key,
  });

  @override
  State<LiveServerScope> createState() => _LiveServerScopeState();
}

class _LiveServerScopeState extends State<LiveServerScope> {
  ServerSnapshot? _snapshot;
  late ConnState _state;
  StreamSubscription<ServerSnapshot>? _snapshotSub;
  StreamSubscription<ConnState>? _stateSub;

  @override
  void initState() {
    super.initState();
    _bind(component.manager);
  }

  @override
  void didUpdateComponent(LiveServerScope oldComponent) {
    super.didUpdateComponent(oldComponent);
    if (oldComponent.manager != component.manager) {
      _unbind();
      _bind(component.manager);
    }
  }

  void _bind(ConnectionManager manager) {
    _state = manager.state;
    _snapshot = null;
    _snapshotSub = manager.snapshots.listen((ServerSnapshot snap) {
      if (!mounted) return;
      setState(() => _snapshot = snap);
    });
    _stateSub = manager.stateChanges.listen((ConnState next) {
      if (!mounted) return;
      setState(() => _state = next);
    });
  }

  void _unbind() {
    _snapshotSub?.cancel();
    _stateSub?.cancel();
    _snapshotSub = null;
    _stateSub = null;
  }

  @override
  void dispose() {
    _unbind();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ServerScope(
      snapshot: _snapshot,
      state: _state,
      child: component.child,
    );
  }
}

const String kReactorNavCss = '''
#arcane-root.shadcn-midnight {
  --background: #02040a;
  --foreground: #edf5ff;
  --card: #07101c;
  --card-foreground: #edf5ff;
  --card-hover: #0b1830;
  --popover: #07101c;
  --popover-foreground: #edf5ff;
  --primary: #1f66ff;
  --primary-foreground: #f8fbff;
  --secondary: #0a1428;
  --secondary-foreground: #d8e7ff;
  --muted: #08111f;
  --muted-foreground: #8ba0c4;
  --accent: #102a61;
  --accent-foreground: #f8fbff;
  --border: #163462;
  --input: #1a3c72;
  --ring: #4f8dff;
  --info: #2f7dff;
  --success: #27d17f;
  --warning: #f3b34f;
  --destructive: #ff4b6b;
  --reactor-black: #02040a;
  --reactor-panel: #07101c;
  --reactor-panel-strong: #09172b;
  --reactor-line: color-mix(in srgb, var(--primary) 22%, var(--border));
  --reactor-label: #95a9cf;
  --reactor-blue-glow: color-mix(in srgb, var(--primary) 32%, transparent);
  --radius: 0.5rem;
}

#arcane-root.shadcn-midnight,
#arcane-root.shadcn-midnight .arcane-scaffold,
#arcane-root.shadcn-midnight .arcane-scaffold-main,
#arcane-root.shadcn-midnight .arcane-scaffold-body {
  background:
    linear-gradient(135deg, rgba(31, 102, 255, 0.11), transparent 24rem),
    linear-gradient(180deg, #02040a 0%, #030711 42%, #02040a 100%) !important;
  min-height: 100vh;
}

#arcane-root.shadcn-midnight .arcane-scaffold-main {
  position: relative;
}

#arcane-root.shadcn-midnight .arcane-scaffold-main::before {
  content: "";
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(79, 141, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79, 141, 255, 0.035) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.9), transparent 78%);
}

#arcane-root.shadcn-midnight .arcane-scaffold-sidebar {
  border-right: 1px solid var(--reactor-line) !important;
  background:
    linear-gradient(180deg, rgba(31, 102, 255, 0.12), transparent 18rem),
    linear-gradient(180deg, #050b16 0%, #03060d 100%) !important;
  box-shadow: 14px 0 44px rgba(0, 0, 0, 0.34);
  min-height: 100vh;
}

#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {
  position: sticky !important;
  inset: 0 auto auto 0 !important;
  top: 0 !important;
  align-self: stretch !important;
  width: 19rem !important;
  height: 100vh !important;
  min-height: 100vh !important;
}

#arcane-root.shadcn-midnight .arcane-sidebar,
#arcane-root.shadcn-midnight .shadcn-sidebar {
  border: 0 !important;
  border-radius: 0 !important;
  background:
    linear-gradient(180deg, rgba(31, 102, 255, 0.1), transparent 18rem),
    linear-gradient(180deg, rgba(7, 16, 28, 0.96), rgba(2, 4, 10, 0.88)) !important;
  box-shadow:
    inset -1px 0 0 var(--reactor-line),
    inset 1px 0 0 rgba(255, 255, 255, 0.04);
  overflow: hidden;
  position: relative;
}

#arcane-root.shadcn-midnight .arcane-sidebar::before,
#arcane-root.shadcn-midnight .shadcn-sidebar::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(79, 141, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79, 141, 255, 0.035) 1px, transparent 1px);
  background-size: 42px 42px;
  opacity: 0.45;
  mask-image: linear-gradient(to bottom, #000 0%, transparent 74%);
}

#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .arcane-sidebar.arcane-sidebar {
  height: 100vh !important;
  min-height: 100vh !important;
  display: flex !important;
  flex-direction: column !important;
}

#arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .sidebar-nav {
  flex: 1 1 auto !important;
}

#arcane-root.shadcn-midnight .sidebar-header {
  position: relative;
  z-index: 1;
  padding: 1rem 0.95rem 1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  background:
    linear-gradient(135deg, rgba(31, 102, 255, 0.16), transparent 58%),
    rgba(2, 4, 10, 0.36);
}

#arcane-root.shadcn-midnight .sidebar-nav {
  position: relative;
  z-index: 1;
  padding: 1rem 0.85rem !important;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--primary) 70%, transparent) transparent;
}

#arcane-root.shadcn-midnight .arcane-sidebar-footer {
  position: relative;
  z-index: 1;
  padding: 0.95rem !important;
  border-top: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  background:
    linear-gradient(0deg, rgba(31, 102, 255, 0.11), transparent),
    rgba(2, 4, 10, 0.4);
}

#arcane-root.shadcn-midnight .arcane-card {
  border-color: color-mix(in srgb, var(--primary) 18%, var(--border)) !important;
  background:
    linear-gradient(180deg, rgba(31, 102, 255, 0.075), transparent 42%),
    var(--card) !important;
  box-shadow:
    0 18px 48px rgba(0, 0, 0, 0.34),
    inset 0 1px 0 rgba(255, 255, 255, 0.045) !important;
}

#arcane-root.shadcn-midnight .arcane-card:hover {
  border-color: color-mix(in srgb, var(--primary) 38%, var(--border)) !important;
}

#arcane-root.shadcn-midnight .arcane-button {
  border-radius: 0.375rem !important;
  letter-spacing: 0 !important;
}

#arcane-root.shadcn-midnight .arcane-button:not([disabled]) {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary) 12%, transparent);
}

#arcane-root.shadcn-midnight .arcane-button:hover:not([disabled]) {
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--primary) 32%, transparent),
    0 10px 24px rgba(31, 102, 255, 0.18);
}

.reactor-shell-content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.reactor-page-header {
  background:
    linear-gradient(90deg, rgba(31, 102, 255, 0.12), transparent 56%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent);
}

.reactor-panel,
.reactor-metric-card {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.018), transparent),
    var(--reactor-panel);
}

.reactor-brand {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
}
.reactor-brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 0.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, #2b74ff, #06101f 72%);
  border: 1px solid color-mix(in srgb, var(--primary) 64%, transparent);
  color: var(--foreground);
  box-shadow:
    0 0 0 6px rgba(31, 102, 255, 0.08),
    0 0 34px var(--reactor-blue-glow);
}
.reactor-brand-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.reactor-brand-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
}
.reactor-brand-title {
  color: var(--foreground);
  font-size: 1.08rem;
  font-weight: 800;
  line-height: 1;
}
.reactor-brand-subtitle {
  color: var(--reactor-label);
  font-size: 0.7rem;
  font-weight: 650;
  line-height: 1;
  text-transform: uppercase;
}
.reactor-brand-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.26rem 0.45rem;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--primary) 28%, transparent);
  color: #c8dcff;
  background: color-mix(in srgb, var(--primary) 14%, transparent);
  font-size: 0.68rem;
  font-weight: 750;
  line-height: 1;
}
.reactor-brand-meta {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.55rem;
  padding-top: 0.9rem;
}
.reactor-brand-stat {
  min-width: 0;
  padding: 0.65rem;
  border-radius: 0.5rem;
  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);
  background: rgba(2, 4, 10, 0.45);
}
.reactor-brand-stat-label {
  display: block;
  color: var(--muted-foreground);
  font-size: 0.66rem;
  font-weight: 700;
  line-height: 1;
  text-transform: uppercase;
}
.reactor-brand-stat-value {
  display: block;
  margin-top: 0.35rem;
  color: var(--foreground);
  font-size: 0.86rem;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.reactor-nav { display: flex; flex-direction: column; gap: 0.22rem; }
.reactor-nav-label {
  display: flex; align-items: center; height: 28px; padding: 0 0.35rem;
  font-size: 0.68rem; font-weight: 800; letter-spacing: 0;
  text-transform: uppercase; color: var(--reactor-label);
}
.reactor-nav-item {
  display: flex; align-items: center; gap: 0.7rem; width: 100%;
  min-height: 42px;
  padding: 0.42rem 0.48rem; border-radius: 0.5rem; background: transparent;
  border: 1px solid transparent; color: var(--muted-foreground); font-size: 0.86rem;
  font-weight: 500; line-height: 1.2; cursor: pointer; text-align: left;
  font-family: inherit; position: relative;
  transition: background-color 0.12s ease, color 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;
}
.reactor-nav-item:hover {
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  border-color: color-mix(in srgb, var(--primary) 24%, transparent);
  color: var(--foreground);
}
.reactor-nav-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 0.42rem;
  color: currentColor;
  background: rgba(79, 141, 255, 0.07);
  border: 1px solid color-mix(in srgb, var(--primary) 10%, transparent);
}
.reactor-nav-item.active {
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--primary) 34%, transparent), color-mix(in srgb, var(--primary) 8%, transparent));
  border-color: color-mix(in srgb, var(--primary) 48%, transparent);
  color: var(--foreground); font-weight: 700;
  box-shadow:
    inset 3px 0 0 #78a8ff,
    0 0 22px color-mix(in srgb, var(--primary) 15%, transparent);
}
.reactor-nav-item.active .reactor-nav-ico {
  color: var(--foreground);
  background: color-mix(in srgb, var(--primary) 28%, transparent);
  border-color: color-mix(in srgb, var(--primary) 42%, transparent);
  box-shadow: 0 0 18px rgba(31, 102, 255, 0.22);
}
.reactor-nav-section { display: flex; flex-direction: column; gap: 1px; }
.reactor-nav-section-header {
  display: flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0.6rem;
  margin-top: 0.4rem; font-size: 0.6875rem; font-weight: 600;
  letter-spacing: 0; text-transform: uppercase;
  color: var(--reactor-label);
}
.reactor-nav-section-name {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0;
}

.reactor-server-list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin-top: 0.45rem;
}

.reactor-server-list-label {
  display: flex;
  align-items: center;
  height: 28px;
  padding: 0 0.35rem;
  color: var(--reactor-label);
  font-size: 0.68rem;
  font-weight: 800;
  line-height: 1;
  text-transform: uppercase;
}

.reactor-server-list-scroll {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  max-height: 15rem;
  overflow-y: auto;
  padding-right: 0.2rem;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--primary) 62%, transparent) transparent;
}

.reactor-server-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  min-height: 38px;
  padding: 0.42rem 0.55rem;
  border-radius: 0.48rem;
  border: 1px solid color-mix(in srgb, var(--primary) 10%, transparent);
  color: var(--muted-foreground);
  background: rgba(2, 4, 10, 0.28);
  font-family: inherit;
  cursor: pointer;
  text-align: left;
}

.reactor-server-row:hover {
  border-color: color-mix(in srgb, var(--primary) 28%, transparent);
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  color: var(--foreground);
}

.reactor-server-row.active {
  color: var(--foreground);
  border-color: color-mix(in srgb, var(--primary) 46%, transparent);
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--primary) 24%, transparent), rgba(2, 4, 10, 0.36));
  box-shadow:
    inset 3px 0 0 #78a8ff,
    0 0 18px rgba(31, 102, 255, 0.14);
}

.reactor-server-row-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.82rem;
  font-weight: 720;
}

.reactor-server-row-state {
  color: var(--reactor-label);
  font-size: 0.66rem;
  font-weight: 800;
  line-height: 1;
  text-transform: uppercase;
}

.reactor-sidebar-status {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.8rem;
  border-radius: 0.6rem;
  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  background:
    linear-gradient(145deg, rgba(31, 102, 255, 0.14), transparent 62%),
    rgba(2, 4, 10, 0.58);
}
.reactor-sidebar-status-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}
.reactor-sidebar-status-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.reactor-sidebar-status-title {
  color: var(--foreground);
  font-size: 0.82rem;
  font-weight: 800;
  line-height: 1.15;
}
.reactor-sidebar-status-subtitle {
  color: var(--muted-foreground);
  font-size: 0.7rem;
  line-height: 1.2;
}
.reactor-sidebar-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 34px;
  width: 100%;
  border-radius: 0.45rem;
  border: 1px solid color-mix(in srgb, var(--primary) 48%, transparent);
  color: var(--foreground);
  background:
    linear-gradient(180deg, #2f7dff, #1554df);
  font-family: inherit;
  font-size: 0.8rem;
  font-weight: 750;
  cursor: pointer;
  box-shadow: 0 12px 24px rgba(31, 102, 255, 0.18);
}
.reactor-sidebar-action:hover {
  background:
    linear-gradient(180deg, #4b8dff, #1f66ff);
}

.reactor-first-run {
  min-height: calc(100vh - 3.5rem);
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);
  gap: clamp(1rem, 2.2vw, 1.6rem);
  align-items: stretch;
  width: 100%;
}

.reactor-first-run-hero,
.reactor-first-run-side {
  border: 1px solid color-mix(in srgb, var(--primary) 22%, var(--border));
  border-radius: 0.625rem;
  background:
    linear-gradient(145deg, rgba(31, 102, 255, 0.16), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.035), transparent),
    rgba(5, 11, 22, 0.92);
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.42),
    inset 0 1px 0 rgba(255, 255, 255, 0.055);
}

.reactor-first-run-hero {
  position: relative;
  overflow: hidden;
  padding: clamp(1.25rem, 4vw, 3.75rem);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 620px;
}

.reactor-first-run-hero::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 16%, rgba(31, 102, 255, 0.34), transparent 24rem),
    linear-gradient(90deg, rgba(79, 141, 255, 0.08) 1px, transparent 1px),
    linear-gradient(rgba(79, 141, 255, 0.06) 1px, transparent 1px);
  background-size: auto, 44px 44px, 44px 44px;
  opacity: 0.8;
}

.reactor-first-run-main,
.reactor-first-run-proof {
  position: relative;
  z-index: 1;
}

.reactor-empty-mark {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.625rem;
  color: var(--foreground);
  background:
    linear-gradient(135deg, var(--primary), #061226 78%);
  border: 1px solid color-mix(in srgb, var(--primary) 58%, transparent);
  box-shadow:
    0 0 0 8px rgba(31, 102, 255, 0.08),
    0 0 44px rgba(31, 102, 255, 0.34);
}

.reactor-empty-kicker {
  margin-top: 1.3rem;
  color: var(--reactor-label);
  font-size: 0.75rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0;
}

.reactor-empty-title {
  max-width: 780px;
  margin-top: 0.85rem;
  color: var(--foreground);
  font-size: clamp(2.25rem, 5vw, 5.6rem);
  font-weight: 800;
  line-height: 0.94;
  letter-spacing: 0;
}

.reactor-empty-copy {
  max-width: 660px;
  margin-top: 1.1rem;
  color: #aec2e8;
  font-size: clamp(0.98rem, 1.4vw, 1.18rem);
  line-height: 1.55;
}

.reactor-empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  margin-top: 1.45rem;
}

.reactor-first-run-proof {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 2rem;
}

.reactor-proof-cell {
  padding: 0.95rem;
  border-radius: 0.5rem;
  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  background: rgba(2, 4, 10, 0.48);
}

.reactor-proof-value {
  display: block;
  color: var(--foreground);
  font-size: 1.45rem;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.reactor-proof-label {
  display: block;
  margin-top: 0.4rem;
  color: var(--muted-foreground);
  font-size: 0.78rem;
  line-height: 1.25;
}

.reactor-first-run-side {
  padding: clamp(1rem, 2vw, 1.35rem);
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
  min-height: 620px;
}

.reactor-side-panel {
  border: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);
  border-radius: 0.5rem;
  background: rgba(2, 4, 10, 0.5);
  overflow: hidden;
}

.reactor-side-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 0.95rem;
  border-bottom: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);
  background: linear-gradient(90deg, rgba(31, 102, 255, 0.13), transparent);
}

.reactor-side-title {
  color: var(--foreground);
  font-size: 0.85rem;
  font-weight: 800;
}

.reactor-side-body {
  padding: 0.9rem 0.95rem;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.reactor-signal-row {
  display: grid;
  grid-template-columns: 94px 1fr auto;
  gap: 0.65rem;
  align-items: center;
  color: var(--muted-foreground);
  font-size: 0.78rem;
}

.reactor-signal-bar {
  height: 7px;
  border-radius: 999px;
  background: rgba(79, 141, 255, 0.12);
  overflow: hidden;
}

.reactor-signal-fill {
  display: block;
  width: var(--signal);
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1f66ff, #78a8ff);
  box-shadow: 0 0 18px rgba(31, 102, 255, 0.34);
}

.reactor-command-line {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 38px;
  color: #c9d9f6;
  font-size: 0.78rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.reactor-command-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--primary);
  box-shadow: 0 0 18px rgba(31, 102, 255, 0.72);
  flex: 0 0 auto;
}

.reactor-step-list {
  display: grid;
  gap: 0.65rem;
}

.reactor-step {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 0.65rem;
  align-items: center;
  padding: 0.75rem;
  border-radius: 0.5rem;
  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);
  background: rgba(7, 16, 28, 0.66);
}

.reactor-step-index {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.4rem;
  color: var(--foreground);
  font-size: 0.78rem;
  font-weight: 800;
  background: color-mix(in srgb, var(--primary) 24%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 32%, transparent);
}

.reactor-step-title {
  color: var(--foreground);
  font-size: 0.84rem;
  font-weight: 750;
  line-height: 1.2;
}

.reactor-step-copy {
  margin-top: 0.25rem;
  color: var(--muted-foreground);
  font-size: 0.74rem;
  line-height: 1.3;
}

.reactor-add-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 1rem;
  align-items: start;
}

.reactor-add-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.reactor-add-console {
  border: 1px solid color-mix(in srgb, var(--primary) 22%, transparent);
  border-radius: 0.5rem;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(31, 102, 255, 0.12), transparent 48%),
    rgba(2, 4, 10, 0.56);
}

.reactor-add-console-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 0.95rem;
  border-bottom: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);
  background: rgba(4, 10, 20, 0.72);
}

.reactor-add-console-title {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--foreground);
  font-size: 0.86rem;
  font-weight: 800;
}

.reactor-add-console-body {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 0.95rem;
}

.reactor-add-message {
  border-radius: 0.45rem;
  border: 1px solid color-mix(in srgb, var(--info) 28%, transparent);
  background: color-mix(in srgb, var(--info) 11%, transparent);
  color: #d8e7ff;
  font-size: 0.82rem;
  line-height: 1.4;
  padding: 0.7rem 0.8rem;
}

.reactor-add-message.warning {
  border-color: color-mix(in srgb, var(--warning) 42%, transparent);
  background: color-mix(in srgb, var(--warning) 13%, transparent);
  color: #ffe9bd;
}

.reactor-add-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.reactor-add-detail,
.reactor-add-step {
  border: 1px solid color-mix(in srgb, var(--primary) 14%, transparent);
  border-radius: 0.45rem;
  background: rgba(7, 16, 28, 0.58);
  padding: 0.78rem;
}

.reactor-add-detail-label,
.reactor-add-step-label {
  display: block;
  color: var(--reactor-label);
  font-size: 0.68rem;
  font-weight: 800;
  line-height: 1;
  text-transform: uppercase;
}

.reactor-add-detail-value {
  display: block;
  margin-top: 0.42rem;
  color: var(--foreground);
  font-size: 0.92rem;
  font-weight: 760;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reactor-add-side {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.reactor-add-step-list {
  display: grid;
  gap: 0.65rem;
}

.reactor-add-step-copy {
  margin-top: 0.35rem;
  color: var(--muted-foreground);
  font-size: 0.78rem;
  line-height: 1.35;
}

@media (max-width: 900px) {
  #arcane-root.shadcn-midnight .arcane-scaffold-body {
    display: flex !important;
    flex-direction: column !important;
  }

  #arcane-root.shadcn-midnight .arcane-scaffold-sidebar {
    width: 100% !important;
    min-height: auto !important;
    height: auto !important;
    border-right: none !important;
    border-bottom: 1px solid var(--reactor-line) !important;
    position: relative !important;
    top: 0 !important;
    inset: auto !important;
    align-self: auto !important;
    padding: 0.75rem !important;
    box-sizing: border-box;
  }

  #arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar.arcane-scaffold-sidebar {
    position: relative !important;
    inset: auto !important;
    top: 0 !important;
    align-self: auto !important;
    width: 100% !important;
    height: auto !important;
    min-height: 0 !important;
  }

  #arcane-root.shadcn-midnight .arcane-sidebar,
  #arcane-root.shadcn-midnight .shadcn-sidebar {
    width: 100% !important;
    min-width: 0 !important;
    border-right: none !important;
    max-height: 18rem !important;
    height: auto !important;
    min-height: 0 !important;
    overflow: hidden !important;
  }

  #arcane-root.shadcn-midnight.arcane-theme-shadcn .arcane-scaffold-sidebar .arcane-sidebar.arcane-sidebar {
    height: auto !important;
    min-height: 0 !important;
    max-height: 18rem !important;
  }

  #arcane-root.shadcn-midnight .sidebar-header {
    padding: 0.2rem 0.35rem 0.55rem !important;
    border-bottom: 0;
  }

  #arcane-root.shadcn-midnight .sidebar-nav {
    max-height: 9.75rem !important;
    overflow-x: auto !important;
    overflow-y: hidden !important;
    padding: 0.45rem !important;
    scrollbar-width: thin;
    display: grid !important;
    gap: 0.45rem !important;
  }

  #arcane-root.shadcn-midnight .arcane-sidebar-footer {
    padding: 0.45rem 0.7rem !important;
    border-top: 1px solid color-mix(in srgb, var(--primary) 16%, transparent);
  }

  .reactor-brand {
    display: flex;
    align-items: center;
  }

  .reactor-brand-meta,
  .reactor-brand-chip {
    display: none;
  }

  .reactor-nav {
    flex-direction: row;
    align-items: center;
    gap: 0.4rem;
    width: max-content;
    min-width: 100%;
  }

  .reactor-nav-section {
    flex-direction: row;
    align-items: center;
    gap: 0.4rem;
    width: max-content;
    min-width: 100%;
  }

  .reactor-nav-section-header {
    flex: 0 0 auto;
    margin-top: 0;
    min-height: 36px;
    padding: 0.5rem 0.6rem;
    border-radius: 0.5rem;
    border: 1px solid color-mix(in srgb, var(--success) 35%, transparent);
    background: rgba(39, 209, 127, 0.08);
  }

  .reactor-server-list {
    width: max-content;
    min-width: 100%;
    margin-top: 0;
  }

  .reactor-server-list-label {
    display: none;
  }

  .reactor-server-list-scroll {
    flex-direction: row;
    max-height: none;
    overflow-x: auto;
    overflow-y: hidden;
    padding-right: 0;
    padding-bottom: 0.15rem;
  }

  .reactor-server-row {
    flex: 0 0 auto;
    width: auto;
    min-width: 9.5rem;
  }

  .reactor-nav-label {
    display: none;
  }

  .reactor-nav-item {
    flex: 0 0 auto;
    width: auto;
    white-space: nowrap;
    padding: 0.55rem 0.7rem;
    min-height: 36px;
  }

  .reactor-nav-ico {
    width: 22px;
    height: 22px;
  }

  .reactor-sidebar-status {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding: 0.48rem 0.6rem;
    border-radius: 0.5rem;
  }

  .reactor-sidebar-status-top {
    width: 100%;
    align-items: center;
  }

  .reactor-sidebar-status-subtitle {
    display: none;
  }

  .reactor-sidebar-action {
    display: none;
  }

  .reactor-shell-content {
    padding: 1rem !important;
    min-height: auto;
  }

  .reactor-page-header {
    align-items: stretch !important;
    padding-left: 0.75rem !important;
  }

  .reactor-grid {
    grid-template-columns: 1fr !important;
  }

  .reactor-add-layout {
    grid-template-columns: 1fr;
  }

  .reactor-add-actions {
    justify-content: stretch;
    width: 100%;
  }

  .reactor-add-actions .arcane-button {
    flex: 1 1 auto;
  }

  .reactor-first-run {
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .reactor-first-run-hero,
  .reactor-first-run-side {
    min-height: auto;
  }

  .reactor-first-run-proof {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .reactor-nav-item {
    padding: 0.55rem 0.5rem;
    font-size: 0.8rem;
  }

  .reactor-nav-item span:last-child {
    max-width: 8.5rem;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .reactor-page {
    gap: 16px !important;
  }

  .reactor-page-header {
    gap: 0.75rem !important;
  }

  .reactor-first-run-hero,
  .reactor-first-run-side {
    padding: 1rem;
  }

  .reactor-add-detail-grid {
    grid-template-columns: 1fr;
  }

  .reactor-empty-title {
    font-size: 2.2rem;
    line-height: 1;
  }

  .reactor-empty-actions {
    flex-direction: column;
  }

  .reactor-empty-actions .arcane-button {
    width: 100%;
  }

  .reactor-signal-row {
    grid-template-columns: 78px 1fr;
  }

  .reactor-signal-row span:last-child {
    grid-column: 2;
  }
}
''';

class _NavEntry {
  final ReactorText label;
  final String route;
  final Widget Function({IconSize size}) icon;

  const _NavEntry(this.label, this.route, this.icon);
}

class _NavItem extends StatelessWidget {
  final String label;
  final Widget Function({IconSize size}) icon;
  final bool active;
  final VoidCallback onTap;

  const _NavItem({
    required this.label,
    required this.icon,
    required this.active,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return dom.button(
      classes: active ? 'reactor-nav-item active' : 'reactor-nav-item',
      attributes: const <String, String>{'type': 'button'},
      events: <String, EventCallback>{'click': (_) => onTap()},
      <Widget>[
        dom.span(classes: 'reactor-nav-ico', <Widget>[icon(size: IconSize.sm)]),
        dom.span(<Widget>[Component.text(label)]),
      ],
    );
  }
}

final List<_NavEntry> _kServerNav = <_NavEntry>[
  _NavEntry(ReactorText.overviewTitle, 'overview', ArcaneIcon.gauge),
  _NavEntry(ReactorText.performanceTitle, 'performance', ArcaneIcon.activity),
  _NavEntry(ReactorText.memoryTitle, 'memory', ArcaneIcon.memoryStick),
  _NavEntry(ReactorText.entitiesTitle, 'entities', ArcaneIcon.boxes),
  _NavEntry(ReactorText.chunksTitle, 'chunks', ArcaneIcon.grid3x3),
  _NavEntry(ReactorText.mechanicsTitle, 'mechanics', ArcaneIcon.cog),
  _NavEntry(ReactorText.eventsTitle, 'events', ArcaneIcon.zap),
  _NavEntry(ReactorText.internalsTitle, 'internals', ArcaneIcon.cpu),
  _NavEntry(ReactorText.incidentsTitle, 'incidents', ArcaneIcon.triangleAlert),
  _NavEntry(ReactorText.worldsTitle, 'worlds', ArcaneIcon.globe),
  _NavEntry(
    ReactorText.worldOverridesTitle,
    'world-overrides',
    ArcaneIcon.settings2,
  ),
  _NavEntry(ReactorText.integrationsTitle, 'integrations', ArcaneIcon.plug),
  _NavEntry(ReactorText.heatmapsTitle, 'heatmaps', ArcaneIcon.thermometer),
  _NavEntry(ReactorText.optimizationTitle, 'optimization', ArcaneIcon.rocket),
  _NavEntry(ReactorText.tweaksTitle, 'tweaks', ArcaneIcon.slidersHorizontal),
  _NavEntry(ReactorText.governorsTitle, 'governors', ArcaneIcon.signal),
  _NavEntry(ReactorText.actionsTitle, 'actions', ArcaneIcon.play),
  _NavEntry(
    ReactorText.incidentCenterTitle,
    'incident-center',
    ArcaneIcon.siren,
  ),
  _NavEntry(ReactorText.environmentTitle, 'environment', ArcaneIcon.serverCog),
  _NavEntry(ReactorText.configEditorTitle, 'config', ArcaneIcon.braces),
  _NavEntry(ReactorText.logsTitle, 'logs', ArcaneIcon.scrollText),
];

class ReactorShell extends StatelessWidget {
  final List<ServerEntry> servers;
  final VoidCallback? onReconnect;
  final Widget body;
  final String currentPath;

  const ReactorShell({
    this.servers = const <ServerEntry>[],
    this.onReconnect,
    this.body = const _EmptyBody(),
    this.currentPath = '/',
    super.key,
  });

  bool _isUnhealthy(ConnState state) =>
      state == ConnState.offline || state == ConnState.degraded;

  bool get _hasUnhealthy =>
      servers.any((ServerEntry s) => _isUnhealthy(s.state));

  int get _liveCount =>
      servers.where((ServerEntry s) => s.state == ConnState.live).length;

  ReactorStatus get _fleetStatus {
    if (servers.isEmpty) return ReactorStatus.neutral;
    if (servers.any((ServerEntry s) => s.state == ConnState.offline)) {
      return ReactorStatus.critical;
    }
    if (servers.any((ServerEntry s) => s.state == ConnState.degraded)) {
      return ReactorStatus.warning;
    }
    if (servers.any((ServerEntry s) => s.state == ConnState.connecting)) {
      return ReactorStatus.info;
    }
    return ReactorStatus.healthy;
  }

  @override
  Widget build(BuildContext context) {
    return ArcaneScaffold(
      sidebar: ArcaneSidebar(
        header: _brandHeader(),
        footer: _connectionFooter(context),
        showCollapseToggle: false,
        children: <Widget>[
          _fleetGroup(context),
          if (servers.isNotEmpty) _serverList(context),
          if (_hasUnhealthy)
            _OfflineBanner(servers: servers, onReconnect: onReconnect),
          if (_activeServer != null) _serverSection(context, _activeServer!),
        ],
      ),
      body: dom.div(
        classes: 'reactor-shell-content',
        styles: const dom.Styles(
          raw: <String, String>{
            'padding': '1.75rem clamp(1rem, 3vw, 2.25rem)',
            'max-width': '1480px',
            'width': '100%',
            'margin': '0 auto',
            'box-sizing': 'border-box',
          },
        ),
        <Widget>[body],
      ),
    );
  }

  Widget _fleetGroup(BuildContext context) {
    return dom.div(classes: 'reactor-nav', <Widget>[
      dom.div(classes: 'reactor-nav-label', <Widget>[
        Component.text(reactorText(ReactorText.fleetTitle)),
      ]),
      _navItem(
        context,
        reactorText(ReactorText.overviewTitle),
        kRouteRoot,
        ArcaneIcon.layoutDashboard,
      ),
      _navItem(
        context,
        reactorText(ReactorText.addServerTitle),
        kRouteAddServer,
        ArcaneIcon.circlePlus,
      ),
      _navItem(
        context,
        reactorText(ReactorText.alertsTitle),
        kRouteAlerts,
        ArcaneIcon.bell,
      ),
      _navItem(
        context,
        reactorText(ReactorText.comparisonTitle),
        kRouteComparison,
        ArcaneIcon.gitCompare,
      ),
      _navItem(
        context,
        reactorText(ReactorText.settingsTitle),
        kRouteSettings,
        ArcaneIcon.settings,
      ),
    ]);
  }

  ServerEntry? get _activeServer {
    if (servers.isEmpty) return null;
    final String? id = _serverIdFromPath();
    if (id != null) {
      for (final ServerEntry server in servers) {
        if (server.id == id) return server;
      }
    }
    return servers.first;
  }

  String? _serverIdFromPath() {
    final List<String> parts = currentPath
        .split('/')
        .where((String part) => part.isNotEmpty)
        .toList();
    if (parts.length < 2 || parts.first != 'server') return null;
    return parts[1];
  }

  String _serverRouteFromPath() {
    final List<String> parts = currentPath
        .split('/')
        .where((String part) => part.isNotEmpty)
        .toList();
    if (parts.length < 3 || parts.first != 'server') return 'overview';
    final String route = parts[2];
    for (final _NavEntry entry in _kServerNav) {
      if (entry.route == route) return route;
    }
    return 'overview';
  }

  Widget _serverList(BuildContext context) {
    final String route = _serverRouteFromPath();
    return dom.div(classes: 'reactor-server-list', <Widget>[
      dom.div(classes: 'reactor-server-list-label', <Widget>[
        Component.text(
          reactorText(
            servers.length == 1
                ? ReactorText.shellServerCount
                : ReactorText.shellServersCount,
            <String, Object?>{'count': servers.length},
          ),
        ),
      ]),
      dom.div(classes: 'reactor-server-list-scroll', <Widget>[
        for (final ServerEntry server in servers)
          _serverRow(context, server, route),
      ]),
    ]);
  }

  Widget _serverRow(BuildContext context, ServerEntry server, String route) {
    final bool active = _activeServer?.id == server.id;
    return dom.button(
      classes: active ? 'reactor-server-row active' : 'reactor-server-row',
      attributes: const <String, String>{'type': 'button'},
      events: <String, EventCallback>{
        'click': (_) => context.push('/server/${server.id}/$route'),
      },
      <Widget>[
        StatusDot(state: server.state),
        dom.span(classes: 'reactor-server-row-name', <Widget>[
          Component.text(server.name),
        ]),
        dom.span(classes: 'reactor-server-row-state', <Widget>[
          Component.text(StatusDot.labelFor(server.state)),
        ]),
      ],
    );
  }

  Widget _navItem(
    BuildContext context,
    String label,
    String route,
    Widget Function({IconSize size}) icon,
  ) {
    return _NavItem(
      label: label,
      icon: icon,
      active: currentPath == route,
      onTap: () => context.push(route),
    );
  }

  Widget _brandHeader() {
    final ReactorStatus status = _fleetStatus;
    final String statusLabel = switch (status) {
      ReactorStatus.healthy => reactorText(ReactorText.commonHealthy),
      ReactorStatus.warning => reactorText(ReactorText.shellWarn),
      ReactorStatus.critical => reactorText(ReactorText.statusCritical),
      ReactorStatus.info => reactorText(ReactorText.shellSyncing),
      ReactorStatus.neutral => reactorText(ReactorText.shellStandby),
    };
    final String fleetValue = servers.isEmpty
        ? reactorText(ReactorText.shellPairedCount, <String, Object?>{
            'count': 0,
          })
        : reactorText(ReactorText.shellLiveCount, <String, Object?>{
            'live': _liveCount,
            'total': servers.length,
          });
    return dom.div(classes: 'reactor-brand', <Widget>[
      dom.div(classes: 'reactor-brand-mark', <Widget>[
        ArcaneIcon.activity(size: IconSize.sm),
      ]),
      dom.div(classes: 'reactor-brand-body', <Widget>[
        dom.div(classes: 'reactor-brand-title-row', <Widget>[
          dom.span(classes: 'reactor-brand-title', <Widget>[
            Component.text(reactorText(ReactorText.appTitle)),
          ]),
          dom.span(classes: 'reactor-brand-chip', <Widget>[
            reactorStatusDot(status, size: 6),
            Component.text(statusLabel),
          ]),
        ]),
        dom.span(classes: 'reactor-brand-subtitle', <Widget>[
          Component.text(reactorText(ReactorText.shellFleetMonitor)),
        ]),
      ]),
      dom.div(classes: 'reactor-brand-meta', <Widget>[
        _brandStat(reactorText(ReactorText.shellState), statusLabel),
        _brandStat(reactorText(ReactorText.fleetTitle), fleetValue),
      ]),
    ]);
  }

  Widget _brandStat(String label, String value) {
    return dom.div(classes: 'reactor-brand-stat', <Widget>[
      dom.span(classes: 'reactor-brand-stat-label', <Widget>[
        Component.text(label),
      ]),
      dom.span(classes: 'reactor-brand-stat-value', <Widget>[
        Component.text(value),
      ]),
    ]);
  }

  Widget _connectionFooter(BuildContext context) {
    final ReactorStatus status = _fleetStatus;
    final String summary = servers.isEmpty
        ? reactorText(ReactorText.fleetNoServersPaired)
        : reactorText(ReactorText.shellServersLive, <String, Object?>{
            'live': _liveCount,
            'total': servers.length,
          });
    final String subtitle = servers.isEmpty
        ? reactorText(ReactorText.shellReadyForPairing)
        : reactorText(ReactorText.shellRealtimeTelemetry);
    return dom.div(classes: 'reactor-sidebar-status', <Widget>[
      dom.div(classes: 'reactor-sidebar-status-top', <Widget>[
        dom.div(classes: 'reactor-sidebar-status-copy', <Widget>[
          dom.span(classes: 'reactor-sidebar-status-title', <Widget>[
            Component.text(summary),
          ]),
          dom.span(classes: 'reactor-sidebar-status-subtitle', <Widget>[
            Component.text(subtitle),
          ]),
        ]),
        reactorStatusDot(status),
      ]),
      if (servers.isEmpty)
        dom.button(
          classes: 'reactor-sidebar-action',
          attributes: const <String, String>{'type': 'button'},
          events: <String, EventCallback>{
            'click': (_) => context.push(kRouteAddServer),
          },
          <Widget>[
            ArcaneIcon.circlePlus(size: IconSize.xs),
            dom.span(<Widget>[
              Component.text(reactorText(ReactorText.shellPairServer)),
            ]),
          ],
        ),
    ]);
  }

  Widget _serverSection(BuildContext context, ServerEntry server) {
    final bool unhealthy = _isUnhealthy(server.state);
    final Widget section = dom.div(classes: 'reactor-nav-section', <Widget>[
      dom.div(classes: 'reactor-nav-section-header', <Widget>[
        StatusDot(state: server.state),
        dom.span(classes: 'reactor-nav-section-name', <Widget>[
          Component.text(reactorText(ReactorText.shellWorkspace)),
        ]),
      ]),
      for (final _NavEntry e in _kServerNav)
        _NavItem(
          label: reactorText(e.label),
          icon: e.icon,
          active: currentPath == '/server/${server.id}/${e.route}',
          onTap: () => context.push('/server/${server.id}/${e.route}'),
        ),
    ]);
    if (!unhealthy) return section;
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'opacity': '0.5', 'pointer-events': 'none'},
      ),
      <Widget>[section],
    );
  }
}

class _EmptyBody extends StatelessWidget {
  const _EmptyBody();

  @override
  Widget build(BuildContext context) => Component.fragment(const <Widget>[]);
}

class _FirstRunFleetView extends StatelessWidget {
  const _FirstRunFleetView();

  @override
  Widget build(BuildContext context) {
    return dom.div(classes: 'reactor-first-run', <Widget>[
      dom.section(classes: 'reactor-first-run-hero', <Widget>[
        dom.div(classes: 'reactor-first-run-main', <Widget>[
          dom.div(classes: 'reactor-empty-mark', <Widget>[
            ArcaneIcon.activity(size: IconSize.lg),
          ]),
          dom.div(classes: 'reactor-empty-kicker', <Widget>[
            Component.text(reactorText(ReactorText.shellFleetControlPlane)),
          ]),
          dom.h1(classes: 'reactor-empty-title', <Widget>[
            Component.text(reactorText(ReactorText.shellNoServersConnected)),
          ]),
          dom.p(classes: 'reactor-empty-copy', <Widget>[
            Component.text(reactorText(ReactorText.shellFirstRunDescription)),
          ]),
          dom.div(classes: 'reactor-empty-actions', <Widget>[
            Button(
              label: reactorText(ReactorText.shellPairServer),
              onPressed: () => context.push(kRouteAddServer),
            ),
            Button.secondary(
              label: reactorText(ReactorText.shellFleetSettings),
              onPressed: () => context.push(kRouteSettings),
            ),
          ]),
        ]),
        dom.div(classes: 'reactor-first-run-proof', <Widget>[
          _proofCell('0', reactorText(ReactorText.shellPairedServers)),
          _proofCell('RCT1', reactorText(ReactorText.shellSecurePairing)),
          _proofCell(
            reactorText(ReactorText.statusLive),
            reactorText(ReactorText.shellTelemetryStream),
          ),
        ]),
      ]),
      dom.aside(classes: 'reactor-first-run-side', <Widget>[
        _sidePanel(
          title: reactorText(ReactorText.shellSignalReadiness),
          trailing: reactorBadge(
            reactorText(ReactorText.shellWaiting),
            ReactorStatus.info,
          ),
          child: dom.div(classes: 'reactor-side-body', <Widget>[
            _signalRow(
              reactorText(ReactorText.overviewTps),
              '0%',
              reactorText(ReactorText.shellStandbyLower),
            ),
            _signalRow(
              reactorText(ReactorText.shellMemory),
              '0%',
              reactorText(ReactorText.shellStandbyLower),
            ),
            _signalRow(
              reactorText(ReactorText.shellIncidents),
              '0%',
              reactorText(ReactorText.shellStandbyLower),
            ),
            _signalRow(
              reactorText(ReactorText.shellActions),
              '0%',
              reactorText(ReactorText.shellLockedLower),
            ),
          ]),
        ),
        _sidePanel(
          title: reactorText(ReactorText.shellConsoleStatus),
          trailing: reactorStatusDot(
            ReactorStatus.info,
            label: reactorText(ReactorText.shellStandby),
          ),
          child: dom.div(classes: 'reactor-side-body', <Widget>[
            _commandLine(reactorText(ReactorText.shellConsoleWaiting)),
            _commandLine(reactorText(ReactorText.shellSamplersIdle)),
            _commandLine(reactorText(ReactorText.shellAlertsArmed)),
          ]),
        ),
        _sidePanel(
          title: reactorText(ReactorText.shellConnectionFlow),
          child: dom.div(classes: 'reactor-side-body', <Widget>[
            dom.div(classes: 'reactor-step-list', <Widget>[
              _step(
                '01',
                reactorText(ReactorText.shellPair),
                reactorText(ReactorText.shellPairDescription),
              ),
              _step(
                '02',
                reactorText(ReactorText.shellVerify),
                reactorText(ReactorText.shellVerifyDescription),
              ),
              _step(
                '03',
                reactorText(ReactorText.shellMonitor),
                reactorText(ReactorText.shellMonitorDescription),
              ),
            ]),
          ]),
        ),
      ]),
    ]);
  }

  Widget _proofCell(String value, String label) {
    return dom.div(classes: 'reactor-proof-cell', <Widget>[
      dom.span(classes: 'reactor-proof-value', <Widget>[Component.text(value)]),
      dom.span(classes: 'reactor-proof-label', <Widget>[Component.text(label)]),
    ]);
  }

  Widget _sidePanel({
    required String title,
    required Widget child,
    Widget? trailing,
  }) {
    return dom.div(classes: 'reactor-side-panel', <Widget>[
      dom.div(classes: 'reactor-side-panel-head', <Widget>[
        dom.span(classes: 'reactor-side-title', <Widget>[
          Component.text(title),
        ]),
        ?trailing,
      ]),
      child,
    ]);
  }

  Widget _signalRow(String label, String width, String value) {
    return dom.div(classes: 'reactor-signal-row', <Widget>[
      dom.span(<Widget>[Component.text(label)]),
      dom.span(
        classes: 'reactor-signal-bar',
        styles: dom.Styles(raw: <String, String>{'--signal': width}),
        <Widget>[dom.span(classes: 'reactor-signal-fill', const <Widget>[])],
      ),
      dom.span(<Widget>[Component.text(value)]),
    ]);
  }

  Widget _commandLine(String text) {
    return dom.div(classes: 'reactor-command-line', <Widget>[
      dom.span(classes: 'reactor-command-dot', const <Widget>[]),
      dom.span(<Widget>[Component.text(text)]),
    ]);
  }

  Widget _step(String index, String title, String copy) {
    return dom.div(classes: 'reactor-step', <Widget>[
      dom.span(classes: 'reactor-step-index', <Widget>[Component.text(index)]),
      dom.div(<Widget>[
        dom.div(classes: 'reactor-step-title', <Widget>[Component.text(title)]),
        dom.div(classes: 'reactor-step-copy', <Widget>[Component.text(copy)]),
      ]),
    ]);
  }
}

class _OfflineBanner extends StatelessWidget {
  final List<ServerEntry> servers;
  final VoidCallback? onReconnect;

  const _OfflineBanner({required this.servers, this.onReconnect});

  @override
  Widget build(BuildContext context) {
    final bool anyOffline = servers.any(
      (ServerEntry s) => s.state == ConnState.offline,
    );
    final ReactorStatus status = anyOffline
        ? ReactorStatus.critical
        : ReactorStatus.warning;
    final String color = reactorStatusColor(status);
    final String label = anyOffline
        ? reactorText(ReactorText.shellConnectionLost)
        : reactorText(ReactorText.shellConnectionDegraded);
    return dom.div(
      styles: dom.Styles(
        raw: <String, String>{
          'margin': '0.25rem 0',
          'padding': '0.7rem 0.75rem',
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.6rem',
          'border-radius': '0.6rem',
          'border': '1px solid color-mix(in srgb, $color 40%, var(--border))',
          'background': 'color-mix(in srgb, $color 12%, transparent)',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.5rem',
            },
          ),
          <Widget>[
            reactorStatusDot(status),
            dom.span(
              styles: const dom.Styles(
                raw: <String, String>{
                  'font-size': '0.8rem',
                  'font-weight': '600',
                  'color': 'var(--foreground)',
                },
              ),
              <Widget>[Component.text(label)],
            ),
          ],
        ),
        Button.secondary(
          label: reactorText(ReactorText.shellReconnect),
          size: ButtonSize.small,
          fullWidth: true,
          onPressed: onReconnect,
        ),
      ],
    );
  }
}

class _ActiveServer {
  final String id;
  final String name;
  final ConnectionManager manager;
  ConnState state;
  late StreamSubscription<ConnState> sub;

  _ActiveServer({
    required this.id,
    required this.name,
    required this.manager,
    required this.state,
  });
}

class ReactorFleetObserver extends StatefulWidget {
  final FleetManager? fleetManager;

  const ReactorFleetObserver({this.fleetManager, super.key});

  @override
  State<ReactorFleetObserver> createState() => ReactorFleetObserverState();
}

class ReactorFleetObserverState extends State<ReactorFleetObserver>
    implements FleetController {
  final List<_ActiveServer> _servers = <_ActiveServer>[];
  late final FleetManager _fleet;
  late final AlertStore _alertStore;
  late final ServerTagsStore _tagsStore;
  int _revision = 0;

  @override
  FleetManager get fleetManager => _fleet;

  @override
  AlertStore get alertStore => _alertStore;

  @override
  ServerTagsStore get tagsStore => _tagsStore;

  @override
  void initState() {
    super.initState();
    if (component.fleetManager != null) {
      _fleet = component.fleetManager!;
    } else {
      final FleetStorage storage = InMemoryFleetStorage();
      _fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential cred) =>
            ReactClient(cred, counter: MonotonicCounter(storage)),
        relayClientFactory: (ServerCredential cred) {
          if (!kIsWeb) return null;
          final String? fp = cred.fingerprint;
          if (fp == null) return null;
          final String? ru = cred.relayUrl;
          if (ru == null || ru.isEmpty) return null;
          return RelayReactClient(
            createRelayConnection(ru, fp),
            cred,
            counter: MonotonicCounter(storage),
          );
        },
      );
    }
    _alertStore = AlertStore(_fleet.storage);
    _tagsStore = ServerTagsStore(_fleet.storage);
    for (final ServerCredential cred in _fleet.servers) {
      _beginTracking(cred.id, cred.label);
    }
  }

  @override
  ConnectionManager? managerFor(String id) => _fleet.managerFor(id);

  @override
  String? labelFor(String id) {
    final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
    return idx < 0 ? null : _servers[idx].name;
  }

  @override
  void trackPaired(String id) {
    final ServerCredential? cred = _credentialFor(id);
    if (cred == null) return;
    _beginTracking(id, cred.label);
  }

  ServerCredential? _credentialFor(String id) {
    for (final ServerCredential cred in _fleet.servers) {
      if (cred.id == id) return cred;
    }
    return null;
  }

  void _beginTracking(String id, String name) {
    if (_servers.any((_ActiveServer s) => s.id == id)) return;
    final ConnectionManager? manager = _fleet.managerFor(id);
    if (manager == null) return;
    final _ActiveServer entry = _ActiveServer(
      id: id,
      name: name,
      manager: manager,
      state: manager.state,
    );
    entry.sub = manager.stateChanges.listen(
      (ConnState next) => _onTransition(id, name, next),
    );
    setState(() {
      _servers.add(entry);
      _revision++;
    });
    manager.start();
  }

  @override
  void removeServer(String id) {
    final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
    if (idx < 0) return;
    final _ActiveServer entry = _servers[idx];
    entry.sub.cancel();
    _fleet.remove(id);
    setState(() {
      _servers.removeAt(idx);
      _revision++;
    });
  }

  @override
  void clearFleet() {
    for (final _ActiveServer server in _servers) {
      server.sub.cancel();
    }
    _fleet.clearAll();
    setState(() {
      _servers.clear();
      _revision++;
    });
  }

  @override
  void importFleet(List<ServerCredential> creds) {
    for (final _ActiveServer server in _servers) {
      server.sub.cancel();
    }
    _fleet.importReplace(creds);
    final List<_ActiveServer> rebuilt = <_ActiveServer>[];
    for (final ServerCredential cred in _fleet.servers) {
      final ConnectionManager? manager = _fleet.managerFor(cred.id);
      if (manager == null) continue;
      final _ActiveServer entry = _ActiveServer(
        id: cred.id,
        name: cred.label,
        manager: manager,
        state: manager.state,
      );
      entry.sub = manager.stateChanges.listen(
        (ConnState next) => _onTransition(cred.id, cred.label, next),
      );
      rebuilt.add(entry);
    }
    setState(() {
      _servers
        ..clear()
        ..addAll(rebuilt);
      _revision++;
    });
    for (final _ActiveServer entry in rebuilt) {
      entry.manager.start();
    }
  }

  void _onTransition(String id, String name, ConnState next) {
    if (next == ConnState.offline) {
      ArcaneSonner.error(
        reactorText(ReactorText.shellServerOffline),
        description: name,
      );
    } else if (next == ConnState.degraded) {
      ArcaneSonner.warning(
        reactorText(ReactorText.shellConnectionDegraded),
        description: name,
      );
    } else if (next == ConnState.live) {
      ArcaneSonner.success(
        reactorText(ReactorText.shellServerReconnected),
        description: name,
      );
    }
    setState(() {
      final int idx = _servers.indexWhere((_ActiveServer s) => s.id == id);
      if (idx >= 0) {
        _servers[idx].state = next;
      }
      _revision++;
    });
  }

  void _reconnectAll() {
    for (final _ActiveServer s in _servers) {
      s.manager.start();
    }
  }

  @override
  void dispose() {
    for (final _ActiveServer s in _servers) {
      s.sub.cancel();
    }
    _fleet.dispose();
    super.dispose();
  }

  List<FleetLiveSource> _buildSources() => _servers
      .map(
        (_ActiveServer e) => FleetLiveSource(
          id: e.id,
          name: e.name,
          initialState: e.state,
          snapshots: e.manager.snapshots,
          stateChanges: e.manager.stateChanges,
        ),
      )
      .toList();

  @override
  Widget build(BuildContext context) {
    return FleetScope(
      controller: this,
      revision: _revision,
      child: FleetLiveObserver(
        sources: _buildSources(),
        child: FleetAlertWatcher(
          child: Router(
            routes: buildReactorShellRoutes(
              servers: _servers
                  .map(
                    (_ActiveServer s) =>
                        ServerEntry(id: s.id, name: s.name, state: s.state),
                  )
                  .toList(),
              onReconnect: _reconnectAll,
            ),
          ),
        ),
      ),
    );
  }
}
