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
import '../screen/metrics_explorer.dart';
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

class _OfflineShadcnStylesheet extends ShadcnStylesheet {
  const _OfflineShadcnStylesheet({super.theme});

  @override
  List<String> get externalCssUrls => const <String>[];

  @override
  FontConfig get fonts => const FontConfig(
    sans:
        "'Geist', ui-sans-serif, system-ui, -apple-system, "
        "BlinkMacSystemFont, 'Segoe UI', sans-serif",
    mono:
        "'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, "
        'Consolas, monospace',
  );

  @override
  RadiusConfig get radius => const RadiusConfig.sharp();
}

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
const String kRouteServerMetrics = '/server/:id/metrics';
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
    path: kRouteServerMetrics,
    builder: (BuildContext ctx, RouteState state) =>
        _buildServerPage(ctx, state, const MetricsExplorerScreen()),
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
      consoleClient: fleet.fleetManager.consoleClientFor(id),
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
      stylesheet: const _OfflineShadcnStylesheet(theme: ShadcnTheme.midnight),
      head: <Widget>[
        const dom.link(href: '/styles/react-web.css', rel: 'stylesheet'),
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
      attributes: <String, String>{
        'type': 'button',
        'aria-label': label,
        'title': label,
        if (active) 'aria-current': 'page',
      },
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
  _NavEntry(ReactorText.metricsTitle, 'metrics', ArcaneIcon.listFilter),
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
      attributes: <String, String>{
        'type': 'button',
        'aria-label': server.name,
        'title': server.name,
        if (active) 'aria-current': 'true',
      },
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
          _proofCell('RCT2', reactorText(ReactorText.shellSecurePairing)),
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
          'border-radius': kReactorRadius,
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
