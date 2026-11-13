/// The sidebar: the server switcher on top, the open server's pages in
/// Monitor, Operate and Configure, then the server group's pages with the
/// open alert count. Every entry is a link, so it opens in a new tab and
/// the shell routes a plain click without reloading.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import '../screen/pairing/pair_dialog.dart';
import '../state/alert_engine.dart';
import '../state/alert_store.dart';
import '../state/connection_manager.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/mode.dart';
import 'fleet_observer.dart';
import 'nav.dart';
import 'paths.dart';

/// Open alerts across the server group, as the Alerts page counts them.
int openAlertCount(BuildContext context) {
  final List<FleetServerLive> servers =
      FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[];
  if (servers.isEmpty) return 0;
  final AlertStore? store = FleetScope.of(context)?.alertStore;
  final List<FleetAlert> live = AlertEngine.computeFleet(
    servers: <({String id, String name, ServerSnapshot? snapshot})>[
      for (final FleetServerLive server in servers)
        (
          id: server.id,
          name: server.name,
          snapshot: currentFleetSnapshot(server),
        ),
    ],
    thresholds: store?.thresholds ?? AlertThresholds.defaults,
    now: DateTime.now(),
  );
  return live
      .where((FleetAlert alert) => !(store?.isResolved(alert.key) ?? false))
      .length;
}

String connectionLabel(ConnState state) => switch (state) {
  ConnState.live => reactorText(ReactorText.statusLive),
  ConnState.connecting ||
  ConnState.degraded => reactorText(ReactorText.startChecking),
  ConnState.offline => reactorText(ReactorText.startNotReachable),
};

/// The switcher: All servers, each server with its connection, Pair a
/// server.
/// Picking a server keeps the current page and tab.
class ServerSwitcher extends StatelessWidget {
  const ServerSwitcher({
    required this.servers,
    required this.currentPath,
    this.wide = true,
    this.rail = false,
    super.key,
  });

  final List<ServerEntry> servers;
  final String currentPath;

  /// The sidebar form; the phone top bar uses its own id.
  final bool wide;

  /// An icon button named by the server, for the icon rail.
  final bool rail;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final String? active = serverIdOf(currentPath);
    ServerEntry? current;
    for (final ServerEntry server in servers) {
      if (server.id == active) current = server;
    }
    void go(String path) => Router.maybeOf(context)?.push(path);
    final String name =
        current?.name ?? reactorText(ReactorText.shellAllServers);
    final String named = '${reactorText(ReactorText.shellSwitchServer)}: $name';
    return AppMenu(
      id: wide ? 'ui-switcher' : 'ui-switcher-bar',
      label: rail ? null : name,
      iconLabel: named,
      icon: current == null ? ArcaneIcon.layoutGrid() : ArcaneIcon.server(),
      // Fills its box, so a long name ends in an ellipsis in the sidebar
      // and in the phone top bar alike.
      fullWidth: !rail,
      truncate: true,
      attributes: rail
          ? const <String, String>{}
          : <String, String>{'aria-label': named},
      entries: <AppMenuEntry>[
        AppMenuEntry(
          reactorText(ReactorText.shellAllServers),
          () => go(kRouteFleet),
          icon: ArcaneIcon.layoutGrid(),
        ),
        if (servers.isNotEmpty) const AppMenuEntry.separator(),
        for (final ServerEntry server in servers)
          AppMenuEntry(
            server.name,
            () => go(switchServerPath(currentPath, server.id)),
            hint: connectionLabel(server.state),
            icon: server.state == ConnState.offline
                ? ArcaneIcon.serverOff()
                : ArcaneIcon.server(),
          ),
        const AppMenuEntry.separator(),
        AppMenuEntry(
          reactorText(ReactorText.pairTitle),
          PairDialogScope.of(context),
          icon: ArcaneIcon.plus(),
        ),
      ],
    );
  }
}

/// The nav itself. [switcher] goes on top (the sidebar); the phone drawer
/// leaves it out because the top bar has it.
class ShellNav extends StatelessWidget {
  const ShellNav({
    required this.servers,
    required this.currentPath,
    this.switcher = true,
    this.rail = false,
    super.key,
  });

  final List<ServerEntry> servers;
  final String currentPath;

  /// The sidebar's own switcher on top; off in the phone drawer, which
  /// fills the sheet instead and leaves the switcher to the top bar.
  final bool switcher;

  /// Icons only (761-1100 px); the labels become the links' names.
  final bool rail;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final bool advanced = ModeScope.advanced(context);
    final String? serverId = serverIdOf(currentPath);
    final bool known = servers.any((ServerEntry s) => s.id == serverId);
    final String bare = Uri.parse(currentPath).path;
    final ServerPage? here = serverPageAt(currentPath);
    final int alerts = openAlertCount(context);
    return ArcaneSidebar(
      width: rail ? 56 : 240,
      collapsedWidth: 56,
      collapsed: rail,
      showCollapseToggle: false,
      styles: switcher
          ? null
          : const ArcaneStyleData(
              widthCustom: '100%',
              backgroundCustom: 'transparent',
              borderCustom: 'none',
            ),
      header: switcher
          ? ServerSwitcher(
              servers: servers,
              currentPath: currentPath,
              rail: rail,
            )
          : null,
      children: <Widget>[
        if (serverId != null && known)
          for (final NavGroup group in NavGroup.values)
            ArcaneSidebarGroup(
              label: reactorText(group.label),
              collapsed: rail,
              children: <Widget>[
                for (final ServerPage page in serverPages)
                  if (page.group == group && (advanced || !page.advanced))
                    ArcaneSidebarItem(
                      label: reactorText(page.label),
                      icon: page.icon(size: IconSize.sm),
                      href: page.pathFor(serverId),
                      selected: identical(page, here),
                      collapsed: rail,
                    ),
              ],
            ),
        ArcaneSidebarGroup(
          label: reactorText(ReactorText.shellAllServers),
          collapsed: rail,
          children: <Widget>[
            for (final FleetPage page in fleetPages)
              ArcaneSidebarItem(
                label: reactorText(page.label),
                icon: page.icon(size: IconSize.sm),
                href: page.path,
                selected: bare == page.path,
                collapsed: rail,
                badge: page.path == kRouteAlerts && alerts > 0
                    ? '$alerts'
                    : null,
              ),
          ],
        ),
      ],
    );
  }
}

/// The breadcrumbs of [path]: the server (or the server group), then the
/// page.
List<BreadcrumbItem> breadcrumbsFor(String path, List<ServerEntry> servers) {
  final String? serverId = serverIdOf(path);
  final ReactorText? title = pageTitleAt(path);
  final List<BreadcrumbItem> items = <BreadcrumbItem>[];
  if (serverId != null) {
    String name = serverId;
    for (final ServerEntry server in servers) {
      if (server.id == serverId) name = server.name;
    }
    items.add(BreadcrumbItem(label: name, href: serverPath(serverId)));
  } else if (fleetPages.first.path == Uri.parse(path).path ||
      Uri.parse(path).path == kRoutePair) {
    items.add(
      BreadcrumbItem(
        label: reactorText(ReactorText.pageHomeTitle),
        href: kRouteRoot,
      ),
    );
  } else {
    items.add(
      BreadcrumbItem(
        label: reactorText(ReactorText.shellAllServers),
        href: kRouteFleet,
      ),
    );
  }
  if (title != null &&
      !(serverId != null && serverPageOf(path).isEmpty) &&
      Uri.parse(path).path != kRouteFleet &&
      Uri.parse(path).path != kRouteRoot) {
    items.add(BreadcrumbItem(label: reactorText(title)));
  }
  return items;
}
