/// The nav: a server's pages in three groups, then the server group's own
/// pages. Labels are localization keys, so the nav and the breadcrumbs read
/// the same words.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_localizations.dart';
import 'paths.dart';

typedef GlyphBuilder = ArcaneGlyph Function({IconSize size});

enum NavGroup {
  monitor(ReactorText.shellNavMonitor),
  operate(ReactorText.shellNavOperate),
  configure(ReactorText.shellNavConfigure);

  const NavGroup(this.label);

  final ReactorText label;
}

/// One page of a server.
class ServerPage {
  const ServerPage(
    this.slug,
    this.label,
    this.icon,
    this.group, {
    this.advanced = false,
  });

  /// The address segment after `/server/<id>`; '' is Overview.
  final String slug;
  final ReactorText label;
  final GlyphBuilder icon;
  final NavGroup group;

  /// Listed in Advanced mode only.
  final bool advanced;

  String pathFor(String serverId) => serverPath(serverId, slug);
}

const List<ServerPage> serverPages = <ServerPage>[
  ServerPage('', ReactorText.overviewTitle, ArcaneIcon.gauge, NavGroup.monitor),
  ServerPage(
    'performance',
    ReactorText.performanceTitle,
    ArcaneIcon.activity,
    NavGroup.monitor,
  ),
  ServerPage(
    'activity',
    ReactorText.pageActivityTitle,
    ArcaneIcon.boxes,
    NavGroup.monitor,
  ),
  ServerPage('map', ReactorText.pageMapTitle, ArcaneIcon.map, NavGroup.monitor),
  ServerPage(
    'incidents',
    ReactorText.incidentsTitle,
    ArcaneIcon.siren,
    NavGroup.monitor,
  ),
  ServerPage(
    'metrics',
    ReactorText.metricsTitle,
    ArcaneIcon.chartLine,
    NavGroup.monitor,
    advanced: true,
  ),
  ServerPage(
    'console',
    ReactorText.pageConsoleTitle,
    ArcaneIcon.squareTerminal,
    NavGroup.operate,
  ),
  ServerPage(
    'actions',
    ReactorText.actionsTitle,
    ArcaneIcon.play,
    NavGroup.operate,
  ),
  ServerPage(
    'tuning',
    ReactorText.pageTuningTitle,
    ArcaneIcon.slidersHorizontal,
    NavGroup.configure,
  ),
  ServerPage(
    'plugins',
    ReactorText.pagePluginsTitle,
    ArcaneIcon.plug,
    NavGroup.configure,
  ),
];

/// The server page at [path], or null on a fleet page or an unknown one.
ServerPage? serverPageAt(String path) {
  if (serverIdOf(path) == null) return null;
  final String slug = serverPageOf(path);
  for (final ServerPage page in serverPages) {
    if (page.slug == slug) return page;
  }
  return null;
}

/// One page of the server group.
class FleetPage {
  const FleetPage(this.path, this.label, this.icon);

  final String path;
  final ReactorText label;
  final GlyphBuilder icon;
}

const List<FleetPage> fleetPages = <FleetPage>[
  FleetPage(kRouteRoot, ReactorText.pageHomeTitle, ArcaneIcon.house),
  FleetPage(kRouteFleet, ReactorText.pageFleetServers, ArcaneIcon.layoutGrid),
  FleetPage(kRouteAlerts, ReactorText.alertsTitle, ArcaneIcon.bell),
  FleetPage(kRouteSettings, ReactorText.settingsTitle, ArcaneIcon.settings),
];

/// The title of the page at [path] for the breadcrumbs.
ReactorText? pageTitleAt(String path) {
  final ServerPage? page = serverPageAt(path);
  if (page != null) return page.label;
  final String bare = Uri.parse(path).path;
  if (bare == kRoutePair) return ReactorText.pairTitle;
  for (final FleetPage fleet in fleetPages) {
    if (fleet.path == bare) return fleet.label;
  }
  return null;
}
