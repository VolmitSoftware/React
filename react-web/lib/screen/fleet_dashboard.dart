/// Server group > Servers: health tiles, the server table and the servers
/// that need attention. A row opens the server's Overview; an attention item
/// opens its Incidents. An entry whose access was revoked offers Pair again
/// and Remove.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/units.dart';
import '../model/role_info.dart';
import '../model/server_credential.dart';
import '../state/connection_manager.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../state/server_tags_store.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import 'alerts_inbox.dart';
import 'pairing/pair_dialog.dart';
import 'form_parts.dart';
import 'server_roles.dart';

class FleetDashboardScreen extends StatefulWidget {
  const FleetDashboardScreen({super.key});

  @override
  State<FleetDashboardScreen> createState() => _FleetDashboardScreenState();
}

class _FleetDashboardScreenState extends State<FleetDashboardScreen> {
  static const String _tagSelectId = 'fleet-tag';

  String _tag = '';
  late final ServerRoles _roles = ServerRoles(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _roles.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetController? fleet = FleetScope.of(context);
    final List<FleetServerLive> all =
        FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[];
    if (all.isEmpty) return const _NoServers();

    final ServerTagsStore? tags = fleet?.tagsStore;
    final List<String> allTags = tags?.allTags() ?? const <String>[];
    final String tag = allTags.contains(_tag) ? _tag : '';
    final List<FleetServerLive> servers = tag.isEmpty || tags == null
        ? all
        : <FleetServerLive>[
            for (final FleetServerLive server in all)
              if (tags.tagsFor(server.id).contains(tag)) server,
          ];
    // Entries paired before roles were kept ask the server. The answer comes
    // later, and a page rendered on the server cannot redraw.
    if (fleet != null && context.binding.isClient) {
      _roles.ask(fleet.fleetManager, <String>[
        for (final FleetServerLive server in servers)
          if (server.role == null) server.id,
      ]);
    }

    final AlertThresholds thresholds =
        fleet?.alertStore.thresholds ?? AlertThresholds.defaults;
    final FleetRollup rollup = FleetRollup.compute(
      servers: servers,
      openAlerts: openFleetAlerts(servers, fleet?.alertStore),
      thresholds: thresholds,
    );
    final bool advanced = ModeScope.advanced(context);
    final bool anyRevoked = rollup.servers.any(_revoked);

    return PageStack(
      children: <Widget>[
        _tiles(rollup, thresholds),
        // Only when something needs attention: the Healthy tile above
        // already says when nothing does.
        if (rollup.needsAttention.isNotEmpty)
          DataList<FleetServerHealth>(
            label: reactorText(ReactorText.fleetNeedsAttention),
            title: reactorText(ReactorText.fleetNeedsAttention),
            items: rollup.needsAttention,
            href: (FleetServerHealth server) =>
                serverPath(server.id, 'incidents'),
            columns: <ListColumn<FleetServerHealth>>[
              ListColumn<FleetServerHealth>.text(
                reactorText(ReactorText.commonServer),
                (FleetServerHealth server) => server.name,
                width: 'minmax(0, 2fr)',
              ),
              ListColumn<FleetServerHealth>(
                reactorText(ReactorText.commonState),
                _stateBadge,
                width: 'minmax(0, 1fr)',
              ),
              ListColumn<FleetServerHealth>(
                reactorText(ReactorText.commonAlerts),
                (FleetServerHealth server) =>
                    Component.text(server.alertCount.toString()),
                numeric: true,
                width: '5rem',
              ),
            ],
            actions: fleet != null && rollup.needsAttention.any(_revoked)
                ? (FleetServerHealth server) => _revokedActions(fleet, server)
                : null,
          ),
        AppCard(
          title: reactorText(ReactorText.fleetServers),
          actions: allTags.isEmpty
              ? null
              : ActionRow(
                  dense: true,
                  label: reactorText(ReactorText.fleetTag),
                  leading: <Widget>[_tagFilter(allTags, tag)],
                ),
          children: <Widget>[
            DataList<FleetServerHealth>(
              label: reactorText(ReactorText.fleetServers),
              items: rollup.servers,
              emptyMessage: reactorText(ReactorText.fleetNoFilterMatches),
              href: (FleetServerHealth server) => serverPath(server.id),
              columns: <ListColumn<FleetServerHealth>>[
                ListColumn<FleetServerHealth>.text(
                  reactorText(ReactorText.commonServer),
                  (FleetServerHealth server) => server.name,
                  width: 'minmax(0, 1.5fr)',
                ),
                ListColumn<FleetServerHealth>(
                  reactorText(ReactorText.commonState),
                  _stateBadge,
                  width: 'minmax(0, 1fr)',
                ),
                if (advanced) ...<ListColumn<FleetServerHealth>>[
                  ListColumn<FleetServerHealth>.text(
                    reactorText(ReactorText.fleetRole),
                    _role,
                    width: 'minmax(0, 1fr)',
                  ),
                  ListColumn<FleetServerHealth>.text(
                    reactorText(ReactorText.fleetTransport),
                    (FleetServerHealth server) => _transport(server.transport),
                    width: 'minmax(0, 1fr)',
                  ),
                ],
                ListColumn<FleetServerHealth>(
                  reactorText(ReactorText.commonPlayers),
                  (FleetServerHealth server) =>
                      Component.text(server.players?.toString() ?? '--'),
                  numeric: true,
                  width: '5rem',
                ),
                ListColumn<FleetServerHealth>(
                  reactorText(ReactorText.overviewTps),
                  (FleetServerHealth server) =>
                      Component.text(server.tps?.toStringAsFixed(1) ?? '--'),
                  numeric: true,
                  width: '5rem',
                ),
                ListColumn<FleetServerHealth>(
                  reactorText(ReactorText.commonAlerts),
                  (FleetServerHealth server) =>
                      Component.text(server.alertCount.toString()),
                  numeric: true,
                  width: '5rem',
                ),
              ],
              actions: anyRevoked && fleet != null
                  ? (FleetServerHealth server) => _revokedActions(fleet, server)
                  : null,
            ),
          ],
        ),
      ],
    );
  }

  /// How many servers are healthy (coloured by the worst one), the worst
  /// TPS and tick time, and the players online: four tiles, one row.
  Widget _tiles(FleetRollup rollup, AlertThresholds thresholds) {
    final int healthy = rollup.servers
        .where((FleetServerHealth s) => s.health == FleetHealth.healthy)
        .map((FleetServerHealth s) => s.serverKey)
        .toSet()
        .length;
    final BadgeTone overall = healthTone(rollup.overall);
    return MetricGrid(
      children: <Widget>[
        MetricTile(
          label: reactorText(ReactorText.commonHealthy),
          value: '$healthy / ${rollup.serverCount}',
          tone: overall,
          meter: rollup.serverCount == 0 ? null : healthy / rollup.serverCount,
        ),
        _readingTile(
          ReactorText.fleetWorstTps,
          HealthMetric.tps,
          rollup.worstTps,
          null,
          thresholds,
        ),
        _readingTile(
          ReactorText.fleetWorstMspt,
          HealthMetric.tickTime,
          rollup.worstMspt,
          'ms',
          thresholds,
        ),
        MetricTile(
          label: reactorText(ReactorText.commonPlayers),
          value: rollup.totalPlayers == null
              ? '--'
              : groupThousands(rollup.totalPlayers!),
        ),
      ],
    );
  }

  /// A fleet-wide reading coloured and named by the shared verdict.
  Widget _readingTile(
    ReactorText label,
    HealthMetric metric,
    double? value,
    String? unit,
    AlertThresholds thresholds,
  ) {
    final HealthLevel level = value == null
        ? HealthLevel.unknown
        : metricHealth(metric, value, thresholds);
    return MetricTile(
      label: reactorText(label),
      value: value?.toStringAsFixed(1) ?? '--',
      unit: unit,
      tone: levelTone(level),
      caption: levelWord(level),
    );
  }

  Widget _tagFilter(List<String> allTags, String tag) => FilterSelect(
    id: _tagSelectId,
    label: reactorText(ReactorText.fleetTag),
    value: tag,
    options: <ArcaneSelectOption>[
      ArcaneSelectOption(
        label: reactorText(ReactorText.fleetAllTags),
        value: '',
      ),
      for (final String each in allTags)
        ArcaneSelectOption(label: each, value: each),
    ],
    onChanged: (String value) => setState(() => _tag = value),
  );

  /// The server's state: what blocks its access, else its health.
  static Widget _stateBadge(FleetServerHealth server) =>
      _accessBadge(server) ??
      (server.health == FleetHealth.pending
          ? StatusBadge(
              server.state == ConnState.connecting
                  ? reactorText(ReactorText.startChecking)
                  : reactorText(ReactorText.fleetAwaitingTelemetry),
              tone: BadgeTone.info,
            )
          : server.state == ConnState.degraded &&
                server.health == FleetHealth.healthy
          ? StatusBadge(
              reactorText(ReactorText.startChecking),
              tone: BadgeTone.warning,
            )
          : HealthBadge(server.health));

  static bool _revoked(FleetServerHealth server) =>
      server.access == ConnAccess.revoked;

  /// Pair again and Remove for an entry the server no longer accepts.
  List<RowAction> _revokedActions(
    FleetController fleet,
    FleetServerHealth server,
  ) {
    if (!_revoked(server)) return const <RowAction>[];
    return <RowAction>[
      RowAction(
        reactorText(ReactorText.fleetPairAgain),
        PairDialogScope.of(context),
      ),
      RowAction(
        reactorText(ReactorText.settingsRemove),
        () => _remove(fleet, server),
        destructive: true,
        attributes: <String, String>{
          'aria-label': reactorText(
            ReactorText.settingsRemoveServer,
            <String, Object?>{'server': server.name},
          ),
        },
      ),
    ];
  }

  Future<void> _remove(FleetController fleet, FleetServerHealth server) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.settingsRemoveServer, <String, Object?>{
        'server': server.name,
      }),
      message: reactorText(ReactorText.settingsRemoveServerMessage),
      confirmLabel: reactorText(ReactorText.settingsRemove),
      destructive: true,
    );
    if (yes && mounted) fleet.removeServer(server.id);
  }

  /// The token's role; old entries ask the server.
  String _role(FleetServerHealth server) => switch (server.role) {
    final String role => roleLabel(
      RoleInfo(role: role, scopes: const <String>[]),
    ),
    null => _roles.label(server.id),
  };

  /// What blocks an entry from its server, as a badge; null when nothing.
  static Widget? _accessBadge(FleetServerHealth server) =>
      switch (server.access) {
        ConnAccess.revoked => StatusBadge(
          reactorText(ReactorText.pairEndedState),
          tone: BadgeTone.error,
        ),
        ConnAccess.wrongServer => StatusBadge(
          reactorText(ReactorText.fleetWrongServer),
          tone: BadgeTone.error,
        ),
        ConnAccess.granted => null,
      };

  /// How this browser reaches the server.
  static String _transport(ServerTransport? transport) => switch (transport) {
    ServerTransport.direct => reactorText(ReactorText.fleetDirect),
    ServerTransport.relay => reactorText(ReactorText.pairRelay),
    ServerTransport.dual => reactorText(ReactorText.fleetDirectAndRelay),
    null => '--',
  };
}

/// The server group with nothing paired: one way forward.
class _NoServers extends StatelessWidget {
  const _NoServers();

  @override
  Widget build(BuildContext context) => EmptyState(
    message: reactorText(ReactorText.fleetNoServersPaired),
    icon: ArcaneIcon.server(),
    action: ActionRow.footer(
      primary: RowAction(
        reactorText(ReactorText.pairTitle),
        PairDialogScope.of(context),
      ),
    ),
  );
}
