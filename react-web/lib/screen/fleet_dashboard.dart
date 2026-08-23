library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../model/alert.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
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
  String _selectedTag = '';

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);

    final List<FleetServerLive> allServers =
        liveScope?.servers ?? <FleetServerLive>[];
    final AlertStore? alertStore = fleet?.alertStore;
    final ServerTagsStore? tagsStore = fleet?.tagsStore;

    final List<String> allTags = tagsStore?.allTags() ?? <String>[];

    List<FleetServerLive> filteredServers = allServers;
    if (_selectedTag.isNotEmpty && tagsStore != null) {
      final List<String> taggedIds = tagsStore.serverIdsWithTag(_selectedTag);
      filteredServers = allServers
          .where((FleetServerLive s) => taggedIds.contains(s.id))
          .toList();
    }

    final List<({String id, String name, ServerSnapshot? snapshot})>
    serverData = filteredServers
        .map(
          (FleetServerLive s) =>
              (id: s.id, name: s.name, snapshot: currentFleetSnapshot(s)),
        )
        .toList();

    final AlertThresholds thresholds =
        alertStore?.thresholds ?? AlertThresholds.defaults;
    final List<FleetAlert> alerts = AlertEngine.computeFleet(
      servers: serverData,
      thresholds: thresholds,
      now: DateTime.now(),
    );
    final List<FleetAlert> openAlerts = alertStore?.reconcile(alerts) ?? alerts;

    final FleetRollup rollup = FleetRollup.compute(
      servers: filteredServers,
      openAlerts: openAlerts,
    );

    final int total = rollup.servers.length;
    final int attention = rollup.needsAttention.length;
    final String subtitle = total == 0
        ? reactorText(ReactorText.fleetNoServersPaired)
        : attention == 0
        ? reactorText(ReactorText.fleetAllServersNominal, <String, Object?>{
            'count': total,
          })
        : reactorText(ReactorText.fleetServersNeedAttention, <String, Object?>{
            'total': total,
            'attention': attention,
          });

    return ReactorPage(
      title: reactorText(ReactorText.fleetTitle),
      subtitle: subtitle,
      actions: _tagFilter(allTags),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.fleetHealth),
          trailing: _alertBadges(rollup),
          child: _rollupRow(rollup),
        ),
        _serverGrid(rollup),
        _needsAttentionSection(rollup),
      ],
    );
  }

  Widget _tagFilter(List<String> allTags) {
    final List<ArcaneSelectOption> options = <ArcaneSelectOption>[
      ArcaneSelectOption(label: reactorText(ReactorText.commonAll), value: ''),
      for (final String tag in allTags)
        ArcaneSelectOption(label: tag, value: tag),
    ];
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '0.6rem',
        },
      ),
      <Widget>[
        reactorEyebrow(reactorText(ReactorText.fleetTag)),
        ArcaneNativeSelect(
          options: options,
          value: _selectedTag,
          onChange: (String v) => setState(() => _selectedTag = v),
        ),
      ],
    );
  }

  Widget _rollupRow(FleetRollup rollup) {
    if (rollup.servers.isEmpty) {
      return ReactorEmptyState(
        title: reactorText(ReactorText.fleetNoFilterMatches),
        description: reactorText(ReactorText.fleetChooseAnotherTag),
      );
    }
    return dom.div(classes: 'reactor-fleet-rollup reactor-metric-bank', <
      Widget
    >[
      if (rollup.meanTps == null)
        ReactorStat(label: reactorText(ReactorText.fleetMeanTps), value: '--')
      else
        Gauge(
          label: reactorText(ReactorText.fleetMeanTps),
          value: rollup.meanTps!,
          display: rollup.meanTps!.toStringAsFixed(1),
          max: 20.0,
          thresholds: const (5.0, 10.0),
          invertStatus: true,
        ),
      if (rollup.worstTps == null)
        ReactorStat(label: reactorText(ReactorText.fleetWorstTps), value: '--')
      else
        Gauge(
          label: reactorText(ReactorText.fleetWorstTps),
          value: rollup.worstTps!,
          display: rollup.worstTps!.toStringAsFixed(1),
          max: 20.0,
          thresholds: const (5.0, 10.0),
          invertStatus: true,
        ),
      if (rollup.compositeHealth == null)
        ReactorStat(
          label: reactorText(ReactorText.fleetCompositeHealth),
          value: '--',
        )
      else
        Gauge(
          label: reactorText(ReactorText.fleetCompositeHealth),
          value: rollup.compositeHealth!.toDouble(),
          display: '${rollup.compositeHealth}%',
          max: 100.0,
          thresholds: const (20.0, 50.0),
          invertStatus: true,
        ),
      ReactorStat(
        label: reactorText(ReactorText.fleetTotalPlayers),
        value: rollup.totalPlayers?.toString() ?? '--',
      ),
      ReactorStat(
        label: reactorText(ReactorText.fleetWorstMspt),
        value: rollup.worstMspt?.toStringAsFixed(1) ?? '--',
        unit: 'ms',
      ),
    ]);
  }

  Widget _alertBadges(FleetRollup rollup) {
    final int critical = rollup.alertCounts[AlertSeverity.critical] ?? 0;
    final int warning = rollup.alertCounts[AlertSeverity.warning] ?? 0;
    final int info = rollup.alertCounts[AlertSeverity.info] ?? 0;

    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'gap': '0.5rem',
          'flex-wrap': 'wrap',
        },
      ),
      <Widget>[
        reactorBadge(
          reactorText(ReactorText.fleetCriticalCount, <String, Object?>{
            'count': critical,
          }),
          ReactorStatus.critical,
        ),
        reactorBadge(
          reactorText(ReactorText.fleetWarningCount, <String, Object?>{
            'count': warning,
          }),
          ReactorStatus.warning,
        ),
        reactorBadge(
          reactorText(ReactorText.fleetInfoCount, <String, Object?>{
            'count': info,
          }),
          ReactorStatus.info,
        ),
      ],
    );
  }

  Widget _serverGrid(FleetRollup rollup) {
    return sectionCard(
      label: reactorText(ReactorText.fleetServers),
      description: reactorText(ReactorText.fleetPairedCount, <String, Object?>{
        'count': rollup.servers.length,
      }),
      flush: true,
      child: rollup.servers.isEmpty
          ? ReactorEmptyState(
              title: reactorText(ReactorText.fleetNoMatchingServers),
              description: reactorText(ReactorText.fleetFilterEmpty),
            )
          : dom.div(classes: 'reactor-server-table', <Widget>[
              dom.div(
                classes: 'reactor-fleet-server-row reactor-table-header',
                <Widget>[
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.commonServer)),
                  ]),
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.commonState)),
                  ]),
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.overviewTps)),
                  ]),
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.commonPlayers)),
                  ]),
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.commonAlerts)),
                  ]),
                  dom.span(<Widget>[
                    Component.text(reactorText(ReactorText.commonLastSeen)),
                  ]),
                  const dom.span(<Widget>[]),
                ],
              ),
              for (final FleetServerHealth s in rollup.servers)
                _ServerCard(server: s),
            ]),
    );
  }

  Widget _needsAttentionSection(FleetRollup rollup) {
    return sectionCard(
      label: reactorText(ReactorText.fleetNeedsAttention),
      child: rollup.needsAttention.isEmpty
          ? ReactorEmptyState(
              title: rollup.servers.isEmpty
                  ? reactorText(ReactorText.fleetNoServersInScope)
                  : reactorText(ReactorText.fleetAllHealthy),
              description: rollup.servers.isEmpty
                  ? reactorText(ReactorText.fleetChooseAnotherFilter)
                  : reactorText(ReactorText.fleetNoHealthConditions),
              icon: rollup.servers.isEmpty
                  ? ArcaneIcon.minus(size: IconSize.sm)
                  : ArcaneIcon.check(size: IconSize.sm),
            )
          : dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                },
              ),
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
      FleetHealth.healthy => (
        reactorText(ReactorText.commonHealthy),
        ReactorStatus.healthy,
      ),
      FleetHealth.warning => (
        reactorText(ReactorText.commonWarning),
        ReactorStatus.warning,
      ),
      FleetHealth.critical => (
        reactorText(ReactorText.statusCritical),
        ReactorStatus.critical,
      ),
      FleetHealth.pending => (
        s.state == ConnState.connecting
            ? reactorText(ReactorText.statusConnecting)
            : reactorText(ReactorText.fleetAwaitingTelemetry),
        s.state == ConnState.degraded
            ? ReactorStatus.warning
            : ReactorStatus.neutral,
      ),
      FleetHealth.offline => (
        reactorText(ReactorText.statusOffline),
        ReactorStatus.critical,
      ),
    };
  }

  static String _formatLastSeen(DateTime? dt) {
    if (dt == null) return reactorText(ReactorText.commonNever);
    final Duration ago = DateTime.now().difference(dt);
    if (ago.inSeconds < 60) {
      return reactorText(ReactorText.commonSecondsAgo, <String, Object?>{
        'value': ago.inSeconds,
      });
    }
    if (ago.inMinutes < 60) {
      return reactorText(ReactorText.commonMinutesAgo, <String, Object?>{
        'value': ago.inMinutes,
      });
    }
    if (ago.inHours < 24) {
      return reactorText(ReactorText.commonHoursAgo, <String, Object?>{
        'value': ago.inHours,
      });
    }
    return reactorText(ReactorText.commonDaysAgo, <String, Object?>{
      'value': ago.inDays,
    });
  }

  Widget _cell(String label, String value, {ReactorStatus? accent}) {
    return dom.div(
      classes: 'reactor-server-cell',
      attributes: <String, String>{'data-label': label},
      <Widget>[
        dom.span(
          styles: dom.Styles(
            raw: <String, String>{
              'font-size': '0.75rem',
              'font-weight': '500',
              'font-variant-numeric': 'tabular-nums',
              'color': accent == null
                  ? 'var(--foreground)'
                  : reactorStatusColor(accent),
            },
          ),
          <Widget>[Component.text(value)],
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext ctx) {
    final double? tps = server.tps;
    final (String, ReactorStatus) health = _health(server);

    return dom.div(classes: 'reactor-fleet-server-row', <Widget>[
      dom.div(classes: 'reactor-server-identity', <Widget>[
        StatusDot(state: server.state),
        dom.span(<Widget>[Component.text(server.name)]),
      ]),
      dom.div(
        classes: 'reactor-server-cell is-state',
        attributes: <String, String>{
          'data-label': reactorText(ReactorText.commonState),
        },
        <Widget>[reactorBadge(health.$1, health.$2)],
      ),
      _cell(
        reactorText(ReactorText.overviewTps),
        tps != null ? tps.toStringAsFixed(1) : '--',
      ),
      _cell(
        reactorText(ReactorText.commonPlayers),
        server.players?.toString() ?? '--',
      ),
      _cell(
        reactorText(ReactorText.fleetAlerts),
        server.alertCount.toString(),
        accent: server.alertCount > 0
            ? ReactorStatus.warning
            : ReactorStatus.neutral,
      ),
      _cell(
        reactorText(ReactorText.fleetLastSeen),
        _formatLastSeen(server.lastSeen),
      ),
      Button.ghost(
        label: reactorText(ReactorText.fleetOpenDashboard),
        size: ButtonSize.small,
        onPressed: () => ctx.push('/server/${server.id}/overview'),
      ),
    ]);
  }
}

class _NeedsAttentionRow extends StatelessWidget {
  final FleetServerHealth server;

  const _NeedsAttentionRow({required this.server});

  static (String, ReactorStatus) _reason(FleetServerHealth s) {
    if (s.health == FleetHealth.pending) {
      if (s.state == ConnState.connecting) {
        return (
          reactorText(ReactorText.statusConnecting),
          ReactorStatus.neutral,
        );
      }
      if (s.state == ConnState.degraded) {
        return (
          reactorText(ReactorText.fleetDegradedAwaiting),
          ReactorStatus.warning,
        );
      }
      return (
        reactorText(ReactorText.fleetAwaitingTelemetry),
        ReactorStatus.neutral,
      );
    }
    if (s.state == ConnState.offline) {
      return (reactorText(ReactorText.statusOffline), ReactorStatus.critical);
    }
    if (s.state == ConnState.degraded) {
      return (reactorText(ReactorText.statusDegraded), ReactorStatus.warning);
    }
    if (s.health == FleetHealth.critical) {
      return (reactorText(ReactorText.statusCritical), ReactorStatus.critical);
    }
    if (s.health == FleetHealth.warning) {
      return (reactorText(ReactorText.commonWarning), ReactorStatus.warning);
    }
    if (s.alertCount > 0) {
      return (
        reactorText(ReactorText.fleetAlertCount, <String, Object?>{
          'count': s.alertCount,
        }),
        ReactorStatus.warning,
      );
    }
    return (reactorText(ReactorText.statusDegraded), ReactorStatus.warning);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final (String, ReactorStatus) reason = _reason(server);
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'justify-content': 'space-between',
          'align-items': 'center',
          'gap': '0.75rem',
          'padding': '0.65rem 0',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.55rem',
              'min-width': '0',
            },
          ),
          <Widget>[
            reactorStatusDot(reason.$2),
            dom.span(
              styles: const dom.Styles(
                raw: <String, String>{
                  'font-weight': '500',
                  'font-size': '0.9rem',
                  'color': 'var(--foreground)',
                },
              ),
              <Widget>[Component.text(server.name)],
            ),
          ],
        ),
        dom.span(
          styles: dom.Styles(
            raw: <String, String>{
              'font-size': '0.8rem',
              'font-weight': '500',
              'color': reactorStatusColor(reason.$2),
            },
          ),
          <Widget>[Component.text(reason.$1)],
        ),
      ],
    );
  }
}
