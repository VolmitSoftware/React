/// Alerts: every open alert of the server group, newest first. Filters sit
/// in the page header; a row opens the server's Incidents. Acknowledge
/// marks an alert seen, Clear takes it out of the list.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import '../state/alert_engine.dart';
import '../state/alert_store.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import 'form_parts.dart';

/// The open alerts of [servers] under the saved thresholds, minus the ones
/// cleared in [store]. A server paired twice (two tokens) raises them once,
/// from the entry with live data.
List<FleetAlert> openFleetAlerts(
  List<FleetServerLive> servers,
  AlertStore? store,
) {
  final Map<String, FleetServerLive> speaking = <String, FleetServerLive>{};
  for (final FleetServerLive server in servers) {
    final FleetServerLive? current = speaking[server.serverKey];
    if (current == null ||
        (currentFleetSnapshot(current) == null &&
            currentFleetSnapshot(server) != null)) {
      speaking[server.serverKey] = server;
    }
  }
  final List<FleetAlert> raised = AlertEngine.computeFleet(
    servers: <({String id, String name, ServerSnapshot? snapshot})>[
      for (final FleetServerLive server in speaking.values)
        (
          id: server.id,
          name: server.name,
          snapshot: currentFleetSnapshot(server),
        ),
    ],
    thresholds: store?.thresholds ?? AlertThresholds.defaults,
    now: DateTime.now(),
  );
  return store?.reconcile(
        raised,
        reporting: <String>{
          for (final FleetServerLive server in speaking.values)
            if (currentFleetSnapshot(server) != null) server.id,
        },
      ) ??
      raised;
}

/// "5m ago" for [time].
String alertAge(DateTime time) {
  final Duration ago = DateTime.now().difference(time);
  if (ago.inSeconds < 60) {
    return reactorText(ReactorText.commonSecondsAgo, <String, Object?>{
      'value': ago.inSeconds < 0 ? 0 : ago.inSeconds,
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

class AlertsInboxScreen extends StatefulWidget {
  const AlertsInboxScreen({super.key});

  @override
  State<AlertsInboxScreen> createState() => _AlertsInboxScreenState();
}

class _AlertsInboxScreenState extends State<AlertsInboxScreen> {
  static const String _severityId = 'alerts-severity';
  static const String _serverId = 'alerts-server';

  AlertSeverity? _severity;
  String? _server;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<FleetServerLive> servers =
        FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[];
    final AlertStore? store = FleetScope.of(context)?.alertStore;
    final String? server =
        servers.any((FleetServerLive each) => each.id == _server)
        ? _server
        : null;

    final List<FleetAlert> shown = <FleetAlert>[
      for (final FleetAlert alert in openFleetAlerts(servers, store))
        if ((_severity == null || alert.severity == _severity) &&
            (server == null || alert.serverId == server))
          alert,
    ]..sort(_newestFirst);
    final List<FleetAlert> unacked = <FleetAlert>[
      for (final FleetAlert alert in shown)
        if (!(store?.isAcked(alert.key) ?? false)) alert,
    ];
    final bool filtered = _severity != null || server != null;

    return AppPage(
      title: reactorText(ReactorText.alertsTitle),
      tip: reactorText(ReactorText.alertsSubtitle),
      actions: ActionRow(
        label: reactorText(ReactorText.alertsFilters),
        leading: <Widget>[_severityFilter(), _serverFilter(servers, server)],
        more: <AppMenuEntry>[
          AppMenuEntry(
            reactorText(ReactorText.alertsAckAll),
            store == null || unacked.isEmpty
                ? null
                : () => _ackAll(store, unacked),
            icon: ArcaneIcon.check(),
          ),
          AppMenuEntry(
            reactorText(ReactorText.alertsClearAll),
            store == null || shown.isEmpty
                ? null
                : () => _clearAll(store, shown),
            icon: ArcaneIcon.x(),
            destructive: true,
          ),
        ],
        moreId: 'alerts-bulk',
      ),
      children: <Widget>[
        if (shown.isEmpty && !filtered && !_anyReporting(servers))
          AppCard(
            children: <Widget>[
              EmptyState(
                message: reactorText(ReactorText.snapshotNone),
                description: reactorText(ReactorText.snapshotAfterReconnect),
              ),
            ],
          )
        else
          DataList<FleetAlert>(
            label: reactorText(ReactorText.alertsTitle),
            items: shown,
            emptyMessage: filtered
                ? reactorText(ReactorText.alertsNoMatches)
                : reactorText(ReactorText.alertsNoneOpen),
            href: (FleetAlert alert) => serverPath(alert.serverId, 'incidents'),
            columns: <ListColumn<FleetAlert>>[
              ListColumn<FleetAlert>.text(
                reactorText(ReactorText.alertsAlert),
                (FleetAlert alert) => alert.title,
                width: 'minmax(0, 1.5fr)',
              ),
              ListColumn<FleetAlert>.text(
                reactorText(ReactorText.commonServer),
                (FleetAlert alert) => alert.serverName,
                width: 'minmax(0, 1.2fr)',
              ),
              ListColumn<FleetAlert>(
                reactorText(ReactorText.alertsSeverity),
                (FleetAlert alert) => _severityCell(store, alert),
                width: 'minmax(0, 1fr)',
              ),
              ListColumn<FleetAlert>(
                reactorText(ReactorText.alertsValue),
                (FleetAlert alert) => dom.span(classes: 'reactor-ltr', <Widget>[
                  Component.text(alert.message),
                ]),
                numeric: true,
                width: 'max-content',
              ),
              ListColumn<FleetAlert>(
                reactorText(ReactorText.alertsSince),
                (FleetAlert alert) => Component.text(alertAge(alert.firstSeen)),
                numeric: true,
                width: 'max-content',
              ),
            ],
            actions: store == null
                ? null
                : (FleetAlert alert) => _rowActions(store, alert),
          ),
      ],
    );
  }

  /// Whether any server has data to raise alerts from.
  static bool _anyReporting(List<FleetServerLive> servers) => servers.any(
    (FleetServerLive server) => currentFleetSnapshot(server) != null,
  );

  /// Acknowledge (or its done state) and Clear: outline text buttons, the
  /// row action pattern of every list.
  List<RowAction> _rowActions(AlertStore store, FleetAlert alert) {
    final bool acked = store.isAcked(alert.key);
    final String where = '${alert.title}, ${alert.serverName}';
    return <RowAction>[
      RowAction(
        reactorText(acked ? ReactorText.alertsAcked : ReactorText.alertsAck),
        acked ? null : () => setState(() => store.ack(alert.key)),
        attributes: <String, String>{
          'aria-label':
              '${reactorText(acked ? ReactorText.alertsAcked : ReactorText.alertsAck)}: $where',
        },
      ),
      RowAction(
        reactorText(ReactorText.alertsResolve),
        () => setState(() => store.resolve(alert.key)),
        attributes: <String, String>{
          'aria-label': '${reactorText(ReactorText.alertsResolve)}: $where',
        },
      ),
    ];
  }

  /// The severity, and Acknowledged once it is.
  static Widget _severityCell(AlertStore? store, FleetAlert alert) {
    final bool acked = store?.isAcked(alert.key) ?? false;
    if (!acked) return _severityBadge(alert.severity);
    return dom.span(classes: 'ui-tip-label', <Widget>[
      _severityBadge(alert.severity),
      StatusBadge(reactorText(ReactorText.alertsAcked)),
    ]);
  }

  static int _newestFirst(FleetAlert a, FleetAlert b) {
    final int byTime = b.firstSeen.compareTo(a.firstSeen);
    if (byTime != 0) return byTime;
    return alertSeverityRank(b.severity)
        .compareTo(alertSeverityRank(a.severity));
  }

  Future<void> _ackAll(AlertStore store, List<FleetAlert> alerts) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.alertsAckAllTitle, <String, Object?>{
        'count': alerts.length,
      }),
      confirmLabel: reactorText(ReactorText.alertsAckAll),
    );
    if (!yes || !mounted) return;
    setState(() {
      for (final FleetAlert alert in alerts) {
        store.ack(alert.key);
      }
    });
  }

  Future<void> _clearAll(AlertStore store, List<FleetAlert> alerts) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.alertsClearAllTitle, <String, Object?>{
        'count': alerts.length,
      }),
      message: reactorText(ReactorText.alertsClearAllMessage),
      confirmLabel: reactorText(ReactorText.alertsClearAll),
      destructive: true,
    );
    if (!yes || !mounted) return;
    setState(() {
      for (final FleetAlert alert in alerts) {
        store.resolve(alert.key);
      }
    });
  }

  Widget _severityFilter() => FilterSelect(
    id: _severityId,
    label: reactorText(ReactorText.alertsSeverity),
    value: _severity?.name ?? '',
    options: <ArcaneSelectOption>[
      ArcaneSelectOption(
        label: reactorText(ReactorText.alertsAllSeverities),
        value: '',
      ),
      for (final AlertSeverity severity in AlertSeverity.values.reversed)
        ArcaneSelectOption(
          label: _severityLabel(severity),
          value: severity.name,
        ),
    ],
    onChanged: (String value) => setState(() {
      _severity = value.isEmpty ? null : AlertSeverity.values.byName(value);
    }),
  );

  Widget _serverFilter(List<FleetServerLive> servers, String? server) =>
      FilterSelect(
        id: _serverId,
        label: reactorText(ReactorText.commonServer),
        value: server ?? '',
        options: <ArcaneSelectOption>[
          ArcaneSelectOption(
            label: reactorText(ReactorText.shellAllServers),
            value: '',
          ),
          for (final FleetServerLive each in servers)
            ArcaneSelectOption(label: each.name, value: each.id),
        ],
        onChanged: (String value) =>
            setState(() => _server = value.isEmpty ? null : value),
      );

  static String _severityLabel(AlertSeverity severity) => switch (severity) {
    AlertSeverity.critical => reactorText(ReactorText.alertsSeverityCritical),
    AlertSeverity.warning => reactorText(ReactorText.alertsSeverityWarning),
    AlertSeverity.info => reactorText(ReactorText.alertsSeverityInfo),
  };

  static Widget _severityBadge(AlertSeverity severity) => StatusBadge(
    _severityLabel(severity),
    tone: switch (severity) {
      AlertSeverity.critical => BadgeTone.error,
      AlertSeverity.warning => BadgeTone.warning,
      AlertSeverity.info => BadgeTone.info,
    },
  );
}
