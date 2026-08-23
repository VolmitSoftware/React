library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/alert.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/server_snapshot.dart';
import '../state/alert_engine.dart';
import '../state/alert_store.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';

class AlertsInboxScreen extends StatefulWidget {
  const AlertsInboxScreen({super.key});

  @override
  State<AlertsInboxScreen> createState() => _AlertsInboxScreenState();
}

class _AlertsInboxScreenState extends State<AlertsInboxScreen> {
  AlertSeverity? _severityFilter;
  String? _serverIdFilter;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);

    final List<FleetServerLive> servers =
        liveScope?.servers ?? <FleetServerLive>[];
    final AlertStore? alertStore = fleet?.alertStore;

    final List<({String id, String name, ServerSnapshot? snapshot})>
    serverData = servers
        .map(
          (FleetServerLive s) =>
              (id: s.id, name: s.name, snapshot: currentFleetSnapshot(s)),
        )
        .toList();

    final AlertThresholds thresholds =
        alertStore?.thresholds ?? AlertThresholds.defaults;
    final List<FleetAlert> raw = AlertEngine.computeFleet(
      servers: serverData,
      thresholds: thresholds,
      now: DateTime.now(),
    );
    List<FleetAlert> open = alertStore?.reconcile(raw) ?? raw;

    if (_severityFilter != null) {
      open = open
          .where((FleetAlert a) => a.severity == _severityFilter)
          .toList();
    }
    if (_serverIdFilter != null) {
      open = open
          .where((FleetAlert a) => a.serverId == _serverIdFilter)
          .toList();
    }

    open.sort((FleetAlert a, FleetAlert b) {
      final int tsCmp = b.firstSeen.compareTo(a.firstSeen);
      if (tsCmp != 0) return tsCmp;
      return alertSeverityRank(
        b.severity,
      ).compareTo(alertSeverityRank(a.severity));
    });

    final List<String> distinctServerIds = servers
        .map((FleetServerLive s) => s.id)
        .toList();
    final Map<String, String> idToName = <String, String>{
      for (final FleetServerLive s in servers) s.id: s.name,
    };

    return ReactorPage(
      title: reactorText(ReactorText.alertsTitle),
      subtitle: reactorText(ReactorText.alertsSubtitle),
      children: <Widget>[
        _filterRow(distinctServerIds, idToName),
        if (open.isEmpty)
          sectionCard(
            label: reactorText(ReactorText.alertsTitle),
            flush: true,
            child: ReactorEmptyState(
              title: _severityFilter != null || _serverIdFilter != null
                  ? reactorText(ReactorText.alertsNoMatches)
                  : reactorText(ReactorText.alertsNoneOpen),
              description: _severityFilter != null || _serverIdFilter != null
                  ? reactorText(ReactorText.alertsAdjustFilters)
                  : reactorText(ReactorText.alertsNoOpenConditions),
            ),
          )
        else
          sectionCard(
            label: reactorText(ReactorText.alertsTitle),
            description: reactorText(
              ReactorText.commonOpenCount,
              <String, Object?>{'count': open.length},
            ),
            flush: true,
            child: dom.div(classes: 'reactor-alert-list', <Widget>[
              for (final FleetAlert alert in open)
                _AlertRow(
                  alert: alert,
                  isAcked: alertStore?.isAcked(alert.key) ?? false,
                  onAck: alertStore == null
                      ? null
                      : () => setState(() => alertStore.ack(alert.key)),
                  onResolve: alertStore == null
                      ? null
                      : () => setState(() => alertStore.resolve(alert.key)),
                ),
            ]),
          ),
      ],
    );
  }

  Widget _filterRow(List<String> serverIds, Map<String, String> idToName) {
    final List<ArcaneSelectOption> severityOptions = <ArcaneSelectOption>[
      ArcaneSelectOption(label: reactorText(ReactorText.commonAll), value: ''),
      ArcaneSelectOption(
        label: reactorText(ReactorText.alertsSeverityCritical),
        value: 'critical',
      ),
      ArcaneSelectOption(
        label: reactorText(ReactorText.alertsSeverityWarning),
        value: 'warning',
      ),
      ArcaneSelectOption(
        label: reactorText(ReactorText.alertsSeverityInfo),
        value: 'info',
      ),
    ];

    final List<ArcaneSelectOption> serverOptions = <ArcaneSelectOption>[
      ArcaneSelectOption(label: reactorText(ReactorText.commonAll), value: ''),
      for (final String id in serverIds)
        ArcaneSelectOption(label: idToName[id] ?? id, value: id),
    ];

    return dom.div(classes: 'reactor-filter-bar', <Widget>[
      ArcaneNativeSelect(
        options: severityOptions,
        value: _severityFilter == null ? '' : _severityFilter!.name,
        onChange: (String v) => setState(() {
          if (v.isEmpty) {
            _severityFilter = null;
          } else {
            _severityFilter = AlertSeverity.values.firstWhere(
              (AlertSeverity s) => s.name == v,
            );
          }
        }),
      ),
      ArcaneNativeSelect(
        options: serverOptions,
        value: _serverIdFilter ?? '',
        onChange: (String v) =>
            setState(() => _serverIdFilter = v.isEmpty ? null : v),
      ),
    ]);
  }
}

class _AlertRow extends StatelessWidget {
  final FleetAlert alert;
  final bool isAcked;
  final VoidCallback? onAck;
  final VoidCallback? onResolve;

  const _AlertRow({
    required this.alert,
    required this.isAcked,
    this.onAck,
    this.onResolve,
  });

  static Widget _severityBadge(AlertSeverity s) {
    return switch (s) {
      AlertSeverity.critical => reactorBadge(
        reactorText(ReactorText.alertsSeverityCritical),
        ReactorStatus.critical,
      ),
      AlertSeverity.warning => reactorBadge(
        reactorText(ReactorText.alertsSeverityWarning),
        ReactorStatus.warning,
      ),
      AlertSeverity.info => reactorBadge(
        reactorText(ReactorText.alertsSeverityInfo),
        ReactorStatus.info,
      ),
    };
  }

  static String _formatTime(DateTime dt) {
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
    return dt.toIso8601String().substring(0, 16);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(
      classes: 'reactor-alert-row${isAcked ? ' is-acked' : ''}',
      <Widget>[
        dom.div(classes: 'reactor-alert-severity', <Widget>[
          _severityBadge(alert.severity),
        ]),
        dom.div(classes: 'reactor-alert-copy', <Widget>[
          dom.div(classes: 'reactor-alert-heading', <Widget>[
            dom.strong(<Widget>[Component.text(alert.serverName)]),
            dom.span(<Widget>[Component.text(alert.title)]),
          ]),
          dom.p(<Widget>[Component.text(alert.message)]),
        ]),
        dom.span(classes: 'reactor-alert-time', <Widget>[
          Component.text(
            reactorText(ReactorText.alertsFirstSeen, <String, Object?>{
              'time': _formatTime(alert.firstSeen),
            }),
          ),
        ]),
        dom.div(classes: 'reactor-alert-actions', <Widget>[
          if (!isAcked)
            Button.ghost(
              label: reactorText(ReactorText.alertsAck),
              size: ButtonSize.small,
              onPressed: onAck,
            )
          else ...<Widget>[
            reactorBadge(
              reactorText(ReactorText.alertsAcked),
              ReactorStatus.neutral,
            ),
            Button.ghost(
              label: reactorText(ReactorText.alertsResolve),
              size: ButtonSize.small,
              onPressed: onResolve,
            ),
          ],
        ]),
      ],
    );
  }
}
