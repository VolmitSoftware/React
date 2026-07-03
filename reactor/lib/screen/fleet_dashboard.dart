library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import '../state/alert_engine.dart';
import '../state/alert_store.dart';
import '../state/connection_manager.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../state/server_tags_store.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/status_dot.dart';

class FleetDashboardScreen extends StatefulWidget {
  const FleetDashboardScreen({super.key});

  @override
  State<FleetDashboardScreen> createState() => _FleetDashboardScreenState();
}

class _FleetDashboardScreenState extends State<FleetDashboardScreen> {
  String _selectedTag = 'All';

  @override
  Widget build(BuildContext context) {
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);

    final List<FleetServerLive> allServers =
        liveScope?.servers ?? <FleetServerLive>[];
    final AlertStore? alertStore = fleet?.alertStore;
    final ServerTagsStore? tagsStore = fleet?.tagsStore;

    final List<String> allTags = tagsStore?.allTags() ?? <String>[];

    List<FleetServerLive> filteredServers = allServers;
    if (_selectedTag != 'All' && tagsStore != null) {
      final List<String> taggedIds = tagsStore.serverIdsWithTag(_selectedTag);
      filteredServers = allServers
          .where((FleetServerLive s) => taggedIds.contains(s.id))
          .toList();
    }

    final List<({String id, String name, ServerSnapshot? snapshot})>
        serverData = filteredServers
            .map((FleetServerLive s) =>
                (id: s.id, name: s.name, snapshot: s.snapshot))
            .toList();

    final AlertThresholds thresholds =
        alertStore?.thresholds ?? AlertThresholds.defaults;
    final List<FleetAlert> alerts = AlertEngine.computeFleet(
      servers: serverData,
      thresholds: thresholds,
      now: DateTime.now(),
    );
    final List<FleetAlert> openAlerts =
        alertStore?.reconcile(alerts) ?? alerts;

    final FleetRollup rollup = FleetRollup.compute(
      servers: filteredServers,
      openAlerts: openAlerts,
    );

    final int total = rollup.servers.length;
    final int attention = rollup.needsAttention.length;
    final String subtitle = total == 0
        ? 'No servers paired'
        : attention == 0
            ? 'All $total servers nominal'
            : '$total servers · $attention need attention';

    return ReactorPage(
      title: 'Fleet',
      subtitle: subtitle,
      actions: _tagFilter(allTags),
      children: <Widget>[
        SectionPanel(
          label: 'Fleet Health',
          trailing: _alertBadges(rollup),
          child: _rollupRow(rollup),
        ),
        _serverGrid(rollup),
        _needsAttentionSection(rollup),
      ],
    );
  }

  Widget _tagFilter(List<String> allTags) {
    final List<String> options = <String>['All', ...allTags];
    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'align-items': 'center',
        'gap': '0.6rem',
      }),
      <Widget>[
        reactorEyebrow('Tag'),
        ArcaneNativeSelect(
          options: options
              .map((String t) => ArcaneSelectOption(label: t, value: t))
              .toList(),
          value: _selectedTag,
          onChange: (String v) => setState(() => _selectedTag = v),
        ),
      ],
    );
  }

  Widget _rollupRow(FleetRollup rollup) {
    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'flex-wrap': 'wrap',
        'gap': '2rem',
        'justify-content': 'space-around',
        'align-items': 'center',
        'padding': '0.5rem 0',
      }),
      <Widget>[
        Gauge(
          label: 'Mean TPS',
          value: rollup.meanTps,
          display: rollup.meanTps.toStringAsFixed(1),
          max: 20.0,
          thresholds: const (5.0, 10.0),
          invertStatus: true,
        ),
        Gauge(
          label: 'Worst TPS',
          value: rollup.worstTps,
          display: rollup.worstTps.toStringAsFixed(1),
          max: 20.0,
          thresholds: const (5.0, 10.0),
          invertStatus: true,
        ),
        Gauge(
          label: 'Composite Health',
          value: rollup.compositeHealth.toDouble(),
          display: '${rollup.compositeHealth}%',
          max: 100.0,
          thresholds: const (20.0, 50.0),
          invertStatus: true,
        ),
        ReactorStat(
          label: 'Total Players',
          value: rollup.totalPlayers.toString(),
        ),
        ReactorStat(
          label: 'Worst MSPT',
          value: rollup.worstMspt.toStringAsFixed(1),
          unit: 'ms',
        ),
      ],
    );
  }

  Widget _alertBadges(FleetRollup rollup) {
    final int critical = rollup.alertCounts[AlertSeverity.critical] ?? 0;
    final int warning = rollup.alertCounts[AlertSeverity.warning] ?? 0;
    final int info = rollup.alertCounts[AlertSeverity.info] ?? 0;

    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'gap': '0.5rem',
        'flex-wrap': 'wrap',
      }),
      <Widget>[
        ArcaneStatusBadge.error('$critical critical'),
        ArcaneStatusBadge.warning('$warning warning'),
        ArcaneStatusBadge.info('$info info'),
      ],
    );
  }

  Widget _serverGrid(FleetRollup rollup) {
    return sectionCard(
      label: 'Servers',
      description: '${rollup.servers.length} paired',
      child: reactorGrid(
        minWidth: '250px',
        children: <Widget>[
          for (final FleetServerHealth s in rollup.servers)
            _ServerCard(server: s),
        ],
      ),
    );
  }

  Widget _needsAttentionSection(FleetRollup rollup) {
    return sectionCard(
      label: 'Needs Attention',
      child: rollup.needsAttention.isEmpty
          ? dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'gap': '0.5rem',
                'color': kReactorMuted,
                'font-size': '0.875rem',
              }),
              <Widget>[
                reactorStatusDot(ReactorStatus.healthy),
                Component.text('All servers healthy'),
              ],
            )
          : dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
              }),
              <Widget>[
                for (final FleetServerHealth s in rollup.needsAttention)
                  _NeedsAttentionRow(server: s),
              ],
            ),
    );
  }
}

class _ServerCard extends StatelessWidget {
  final FleetServerHealth server;

  const _ServerCard({required this.server});

  static (String, ReactorStatus) _health(FleetServerHealth s) {
    return switch (s.health) {
      FleetHealth.healthy => ('Healthy', ReactorStatus.healthy),
      FleetHealth.warning => ('Warning', ReactorStatus.warning),
      FleetHealth.critical => ('Critical', ReactorStatus.critical),
      FleetHealth.offline => ('Offline', ReactorStatus.critical),
    };
  }

  static String _formatLastSeen(DateTime? dt) {
    if (dt == null) return 'Never';
    final Duration ago = DateTime.now().difference(dt);
    if (ago.inSeconds < 60) return '${ago.inSeconds}s ago';
    if (ago.inMinutes < 60) return '${ago.inMinutes}m ago';
    if (ago.inHours < 24) return '${ago.inHours}h ago';
    return '${ago.inDays}d ago';
  }

  Widget _miniStat(String label, String value, {ReactorStatus? accent}) {
    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'flex-direction': 'column',
        'gap': '0.2rem',
        'min-width': '0',
      }),
      <Widget>[
        reactorEyebrow(label),
        dom.span(
          styles: dom.Styles(raw: <String, String>{
            'font-size': '0.95rem',
            'font-weight': '600',
            'font-variant-numeric': 'tabular-nums',
            'color': accent == null
                ? 'var(--foreground)'
                : reactorStatusColor(accent),
          }),
          <Widget>[Component.text(value)],
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext ctx) {
    final double? tps = server.tps;
    final (String, ReactorStatus) health = _health(server);

    return Card.flat(
      fillWidth: true,
      padding: '0',
      borderRadius: kReactorRadius,
      child: dom.div(
        styles: const dom.Styles(raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'overflow': 'hidden',
          'border-radius': kReactorRadius,
        }),
        <Widget>[
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'justify-content': 'space-between',
              'gap': '0.5rem',
              'padding': '0.85rem 1rem 0.7rem',
            }),
            <Widget>[
              dom.div(
                styles: const dom.Styles(raw: <String, String>{
                  'display': 'flex',
                  'align-items': 'center',
                  'gap': '0.55rem',
                  'min-width': '0',
                }),
                <Widget>[
                  StatusDot(state: server.state),
                  dom.span(
                    styles: const dom.Styles(raw: <String, String>{
                      'font-weight': '600',
                      'font-size': '0.95rem',
                      'color': 'var(--foreground)',
                      'overflow': 'hidden',
                      'text-overflow': 'ellipsis',
                      'white-space': 'nowrap',
                    }),
                    <Widget>[Component.text(server.name)],
                  ),
                ],
              ),
              reactorBadge(health.$1, health.$2),
            ],
          ),
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'grid',
              'grid-template-columns': 'repeat(2, 1fr)',
              'gap': '0.75rem 1rem',
              'padding': '0.5rem 1rem 0.9rem',
              'border-top': '1px solid $kReactorHairline',
            }),
            <Widget>[
              _miniStat('TPS', tps != null ? tps.toStringAsFixed(1) : '--'),
              _miniStat('Players', server.players.toString()),
              _miniStat(
                'Alerts',
                server.alertCount.toString(),
                accent: server.alertCount > 0
                    ? ReactorStatus.warning
                    : ReactorStatus.neutral,
              ),
              _miniStat('Last Seen', _formatLastSeen(server.lastSeen)),
            ],
          ),
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'padding': '0 1rem 0.9rem',
            }),
            <Widget>[
              Button.secondary(
                label: 'Open dashboard',
                size: ButtonSize.small,
                fullWidth: true,
                onPressed: () => ctx.push('/server/${server.id}/overview'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _NeedsAttentionRow extends StatelessWidget {
  final FleetServerHealth server;

  const _NeedsAttentionRow({required this.server});

  static (String, ReactorStatus) _reason(FleetServerHealth s) {
    if (s.state == ConnState.offline) return ('Offline', ReactorStatus.critical);
    if (s.state == ConnState.degraded) {
      return ('Degraded', ReactorStatus.warning);
    }
    if (s.health == FleetHealth.critical) {
      return ('Critical', ReactorStatus.critical);
    }
    if (s.health == FleetHealth.warning) {
      return ('Warning', ReactorStatus.warning);
    }
    if (s.alertCount > 0) {
      return ('${s.alertCount} alert(s)', ReactorStatus.warning);
    }
    return ('Degraded', ReactorStatus.warning);
  }

  @override
  Widget build(BuildContext context) {
    final (String, ReactorStatus) reason = _reason(server);
    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'justify-content': 'space-between',
        'align-items': 'center',
        'gap': '0.75rem',
        'padding': '0.65rem 0',
        'border-bottom': '1px solid $kReactorHairline',
      }),
      <Widget>[
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'flex',
            'align-items': 'center',
            'gap': '0.55rem',
            'min-width': '0',
          }),
          <Widget>[
            reactorStatusDot(reason.$2),
            dom.span(
              styles: const dom.Styles(raw: <String, String>{
                'font-weight': '500',
                'font-size': '0.9rem',
                'color': 'var(--foreground)',
              }),
              <Widget>[Component.text(server.name)],
            ),
          ],
        ),
        dom.span(
          styles: dom.Styles(raw: <String, String>{
            'font-size': '0.8rem',
            'font-weight': '500',
            'color': reactorStatusColor(reason.$2),
          }),
          <Widget>[Component.text(reason.$1)],
        ),
      ],
    );
  }
}
