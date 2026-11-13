/// Overview: is the server ok, and if not, why. One verdict line (the state
/// and what drives it), four vitals, the incident state with this server's
/// open alerts beside the tick time chart, and shortcuts to the actions
/// admins run most.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../chart/range_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/action_descriptor.dart';
import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/reading.dart';
import '../model/units.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/alert_engine.dart';
import '../state/actions_controller.dart';
import '../state/alert_store.dart';
import '../state/fleet_scope.dart';
import '../state/sampler_catalog.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/role_gate.dart';
import '../ui/kit/states.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

/// The most shortcuts the Overview offers.
const int kQuickActionCount = 3;

class OverviewScreen extends StatelessWidget {
  const OverviewScreen({this.serverId, super.key});

  /// The open server, for links to its other pages and its alerts.
  final String? serverId;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final FleetController? fleet = FleetScope.of(context);
    final AlertThresholds thresholds =
        fleet?.alertStore.thresholds ?? AlertThresholds.defaults;
    final SamplerCatalog? catalog = ServerSessionScope.of(context)?.catalog;
    if (catalog == null) return _page(snapshot, fleet, thresholds, null);
    return ListenableBuilder(
      listenable: catalog,
      builder: (BuildContext context) =>
          _page(snapshot, fleet, thresholds, catalog),
    );
  }

  Widget _page(
    ServerSnapshot snapshot,
    FleetController? fleet,
    AlertThresholds thresholds,
    SamplerCatalog? catalog,
  ) {
    final SamplerSample? tickTime = snapshot.sampler('tick-time');
    // A server without tick time (Folia) keeps the card, saying so, so
    // Overview has one layout on every server.
    final bool notReported =
        tickTime == null && (catalog?.notReported('tick-time') ?? false);
    final HealthVerdict verdict = assessHealth(snapshot, thresholds);
    final Widget incidents = _IncidentCard(
      serverId: serverId,
      score: snapshot.sampler('incident-score'),
      thresholds: thresholds,
      alerts: _openAlerts(fleet, snapshot, thresholds),
    );

    // Health first: the verdict, the tiles, then what drives it.
    return PageStack(
      children: <Widget>[
        VerdictLine(verdict: verdict),
        MetricGrid(
          children: vitalTiles(
            snapshot,
            thresholds,
            notReported: catalog?.notReported ?? _reportsAll,
          ),
        ),
        dom.div(classes: 'ui-split', <Widget>[
          incidents,
          _tickChart(tickTime, notReported: notReported),
        ]),
        RoleGate(
          need: RoleNeed.operator,
          child: _QuickActions(serverId: serverId),
        ),
      ],
    );
  }

  Widget _tickChart(SamplerSample? tickTime, {required bool notReported}) =>
      RangeChartCard(
        title: reactorText(ReactorText.commonTickTime),
        unit: 'ms',
        series: <RangeSeries>[
          RangeSeries(
            reactorText(ReactorText.commonTickTime),
            'tick-time',
            tickTime,
          ),
        ],
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.incidentsDetails),
            null,
            href: serverId == null
                ? null
                : serverPagePath(serverId!, 'performance'),
            attributes: <String, String>{
              'aria-label': reactorText(ReactorText.performanceTitle),
            },
          ),
        ],
        replacement: notReported
            ? EmptyState(message: reactorText(ReactorText.commonNotReported))
            : null,
      );

  /// This server's open alerts, from the same rules as the Alerts inbox,
  /// without the ones the user resolved and without the incident score
  /// alert (the status line above them already says it).
  List<FleetAlert> _openAlerts(
    FleetController? fleet,
    ServerSnapshot snapshot,
    AlertThresholds thresholds,
  ) {
    final String? id = serverId;
    if (fleet == null || id == null) return const <FleetAlert>[];
    final AlertStore store = fleet.alertStore;
    return <FleetAlert>[
      for (final FleetAlert alert in AlertEngine.computeForServer(
        serverId: id,
        serverName: fleet.labelFor(id) ?? id,
        snapshot: snapshot,
        thresholds: thresholds,
        now: DateTime.now(),
      ))
        if (alert.metricId != 'incident-score' && !store.isResolved(alert.key))
          alert,
    ];
  }
}

/// TPS, tick time, players and memory, each coloured and named by the
/// shared verdict ([readingOf]). Memory takes its state from heap use
/// against the maximum. A sampler the server cannot report (Folia has no
/// tick time) says so instead of waiting for it.
List<Widget> vitalTiles(
  ServerSnapshot snapshot,
  AlertThresholds thresholds, {
  bool Function(String samplerId) notReported = _reportsAll,
}) {
  final SamplerSample? memory = snapshot.sampler('memory-used');
  final SamplerSample? pressure = snapshot.sampler(
    HealthMetric.memoryPressure.samplerId,
  );
  final HealthLevel pressureLevel = readingOf(pressure, thresholds).level;
  final HealthLevel memoryLevel = memory == null
      ? HealthLevel.unknown
      : pressureLevel == HealthLevel.unknown
      ? HealthLevel.healthy
      : pressureLevel;
  return <Widget>[
    samplerTile(
      reactorText(ReactorText.overviewTps),
      snapshot.sampler('ticks-per-second'),
      thresholds: thresholds,
      notReported: notReported('ticks-per-second'),
    ),
    samplerTile(
      reactorText(ReactorText.commonTickTime),
      snapshot.sampler('tick-time'),
      thresholds: thresholds,
      notReported: notReported('tick-time'),
    ),
    samplerTile(
      reactorText(ReactorText.commonPlayers),
      snapshot.sampler('players'),
    ),
    memoryTile(
      reactorText(ReactorText.memoryTitle),
      memory,
      pressure: pressure,
      max: snapshot.sampler('jvm-heap-max'),
      tone: levelTone(memoryLevel),
      caption: levelWord(memoryLevel),
    ),
  ];
}

bool _reportsAll(String samplerId) => false;

/// The incident score's level, from the shared verdict.
BadgeTone incidentTone(SamplerSample? score, AlertThresholds thresholds) =>
    levelTone(sampleHealth(HealthMetric.incidentScore, score, thresholds));

/// The server's state in one line: a badge, then the readings past their
/// lines (worst first, at most two).
class VerdictLine extends StatelessWidget {
  const VerdictLine({required this.verdict, super.key});

  final HealthVerdict verdict;

  static String _name(HealthMetric metric) => reactorText(switch (metric) {
    HealthMetric.tps => ReactorText.overviewTps,
    HealthMetric.tickTime => ReactorText.commonTickTime,
    HealthMetric.incidentScore => ReactorText.commonIncidentScore,
    HealthMetric.gcPercent => ReactorText.commonGcTime,
    HealthMetric.pingP95 => ReactorText.commonPingP95,
    HealthMetric.memoryPressure => ReactorText.memoryPressure,
  });

  @override
  Widget build(BuildContext context) {
    final HealthLevel level = verdict.level;
    final String causes = verdict.causes.isEmpty
        ? reactorText(ReactorText.overviewNoAlerts)
        : verdict.causes
              .take(2)
              .map((HealthCause cause) {
                final Reading reading = readingOf(cause.sample);
                final String name = _name(cause.metric);
                return '$name '
                    '${withUnit(reading.value, unitBeside(name, reading.unit))}';
              })
              .join(', ');
    return dom.p(
      classes: 'ui-verdict',
      attributes: const <String, String>{'data-verdict': ''},
      <Widget>[
        StatusBadge(
          level == HealthLevel.healthy
              ? reactorText(ReactorText.commonHealthy)
              : levelWord(level) ?? reactorText(ReactorText.metricsWaiting),
          tone: levelTone(level),
        ),
        dom.span(<Widget>[Component.text(causes)]),
      ],
    );
  }
}

class _IncidentCard extends StatelessWidget {
  const _IncidentCard({
    required this.serverId,
    required this.score,
    required this.thresholds,
    required this.alerts,
  });

  final String? serverId;
  final SamplerSample? score;
  final AlertThresholds thresholds;
  final List<FleetAlert> alerts;

  @override
  Widget build(BuildContext context) {
    final BadgeTone tone = incidentTone(score, thresholds);
    final SamplerSample? reading = score;
    final String scoreLine =
        '${reactorText(ReactorText.commonIncidentScore)} '
        '${reading?.display ?? '--'}';
    // The verdict line above says the state; the card holds the score and
    // what is open, without saying either twice.
    return AppCard(
      title: reactorText(ReactorText.incidentsTitle),
      actions: ActionRow(
        dense: true,
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.overviewViewIncidents),
            null,
            href: serverId == null
                ? null
                : serverPagePath(serverId!, 'incidents'),
          ),
        ],
      ),
      attributes: const <String, String>{'data-incident-status': ''},
      children: <Widget>[
        AppAlert(
          tone: switch (tone) {
            BadgeTone.error => AlertTone.error,
            BadgeTone.warning => AlertTone.warning,
            BadgeTone.ok => AlertTone.ok,
            _ => AlertTone.info,
          },
          message: reading == null
              ? reactorText(ReactorText.metricsWaiting)
              : scoreLine,
        ),
        for (final FleetAlert alert in alerts)
          AppAlert(
            tone: switch (alert.severity) {
              AlertSeverity.critical => AlertTone.error,
              AlertSeverity.warning => AlertTone.warning,
              AlertSeverity.info => AlertTone.info,
            },
            title: alert.title,
            message: alert.message,
          ),
      ],
    );
  }
}

/// Up to [kQuickActionCount] of the server's actions, each opening its form
/// on the Actions page. The list comes from the server's session, which
/// loads it once and refetches it when the server's actions change.
class _QuickActions extends StatelessWidget {
  const _QuickActions({required this.serverId});

  final String? serverId;

  /// Non-destructive actions first, in the server's order.
  static List<ActionDescriptor> pick(List<ActionDescriptor> actions) =>
      <ActionDescriptor>[
        ...actions.where((ActionDescriptor action) => !action.destructive),
        ...actions.where((ActionDescriptor action) => action.destructive),
      ].take(kQuickActionCount).toList(growable: false);

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    final String? id = serverId;
    if (session == null || id == null) {
      return const Component.fragment(<Widget>[]);
    }
    final ActionsController controller = session.actions;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) {
        final List<ActionDescriptor> picked = pick(controller.actions);
        if (picked.isEmpty) return const Component.fragment(<Widget>[]);
        // The title and "All actions" in the head; the shortcuts under it
        // from the left, folding into "..." when short of room.
        return AppCard(
          title: reactorText(ReactorText.overviewQuickActions),
          actions: ActionRow(
            dense: true,
            secondary: <RowAction>[
              RowAction(
                reactorText(ReactorText.overviewAllActions),
                null,
                href: serverPagePath(id, 'actions'),
              ),
            ],
          ),
          children: <Widget>[
            ActionRow(
              dense: true,
              start: true,
              label: reactorText(ReactorText.overviewQuickActions),
              secondary: <RowAction>[
                for (final ActionDescriptor action in picked)
                  RowAction(
                    action.name,
                    null,
                    href: serverPagePath(id, 'actions', <String, String>{
                      'action': action.id,
                    }),
                  ),
              ],
            ),
          ],
        );
      },
    );
  }
}
