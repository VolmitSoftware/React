/// Every address in the app. Server pages live under `/server/<id>`; the
/// page's tab is the `?tab=` query. Old addresses (`/server/<id>/overview`,
/// `/comparison`, `/server/<id>/governors` and the rest) are gone without
/// redirects. Pure Dart, so the routing rules are unit tested.
library;

const String kRouteRoot = '/';
const String kRouteFleet = '/fleet';
const String kRouteAlerts = '/alerts';
const String kRouteSettings = '/settings';
const String kRoutePair = '/pair';
const String kRouteServer = '/server/:id';
const String kRouteServerPerformance = '/server/:id/performance';
const String kRouteServerActivity = '/server/:id/activity';
const String kRouteServerMap = '/server/:id/map';
const String kRouteServerIncidents = '/server/:id/incidents';
const String kRouteServerConsole = '/server/:id/console';
const String kRouteServerActions = '/server/:id/actions';
const String kRouteServerTuning = '/server/:id/tuning';
const String kRouteServerPlugins = '/server/:id/plugins';
const String kRouteServerMetrics = '/server/:id/metrics';

/// A server page's address: its Overview without [page].
String serverPath(String id, [String page = '']) =>
    page.isEmpty ? '/server/$id' : '/server/$id/$page';

List<String> _parts(String path) =>
    Uri.parse(path).pathSegments
        .where((String part) => part.isNotEmpty)
        .toList();

/// The server id in [path] (`/server/<id>/...`), or null on a fleet page.
String? serverIdOf(String path) {
  final List<String> parts = _parts(path);
  if (parts.length < 2 || parts.first != 'server') return null;
  return parts[1];
}

/// The server page of [path]: '' for Overview, else the page segment.
String serverPageOf(String path) {
  final List<String> parts = _parts(path);
  if (parts.length < 3 || parts.first != 'server') return '';
  return parts[2];
}

/// [path] on server [id], keeping the page and its tab: switching servers
/// stays on the same view.
String switchServerPath(String path, String id) {
  final Uri uri = Uri.parse(path);
  final String page = serverPageOf(path);
  final String target = serverPath(id, page);
  return uri.hasQuery ? '$target?${uri.query}' : target;
}
