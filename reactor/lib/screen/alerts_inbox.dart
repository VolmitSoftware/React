library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/alert.dart';
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
    final FleetLiveScope? liveScope = FleetLiveScope.of(context);
    final FleetController? fleet = FleetScope.of(context);

    final List<FleetServerLive> servers =
        liveScope?.servers ?? <FleetServerLive>[];
    final AlertStore? alertStore = fleet?.alertStore;

    final List<({String id, String name, ServerSnapshot? snapshot})>
        serverData = servers
            .map((FleetServerLive s) =>
                (id: s.id, name: s.name, snapshot: s.snapshot))
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
      return alertSeverityRank(b.severity)
          .compareTo(alertSeverityRank(a.severity));
    });

    final List<String> distinctServerIds = servers.map((FleetServerLive s) => s.id).toList();
    final Map<String, String> idToName = <String, String>{
      for (final FleetServerLive s in servers) s.id: s.name,
    };

    return ReactorPage(
      title: 'Alerts',
      subtitle: 'Open fleet alerts',
      children: <Widget>[
        _filterRow(distinctServerIds, idToName),
        if (open.isEmpty)
          sectionCard(
            label: 'Alerts',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
                'padding': '0.5rem 0',
              }),
              <Widget>[Component.text('No open alerts')],
            ),
          )
        else
          sectionCard(
            label: 'Alerts',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
                'gap': '0.75rem',
              }),
              <Widget>[
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
              ],
            ),
          ),
      ],
    );
  }

  Widget _filterRow(List<String> serverIds, Map<String, String> idToName) {
    final List<ArcaneSelectOption> severityOptions = <ArcaneSelectOption>[
      const ArcaneSelectOption(label: 'All', value: ''),
      const ArcaneSelectOption(label: 'critical', value: 'critical'),
      const ArcaneSelectOption(label: 'warning', value: 'warning'),
      const ArcaneSelectOption(label: 'info', value: 'info'),
    ];

    final List<ArcaneSelectOption> serverOptions = <ArcaneSelectOption>[
      const ArcaneSelectOption(label: 'All', value: ''),
      for (final String id in serverIds)
        ArcaneSelectOption(label: idToName[id] ?? id, value: id),
    ];

    return dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'align-items': 'center',
        'gap': '0.75rem',
        'flex-wrap': 'wrap',
      }),
      <Widget>[
        ArcaneNativeSelect(
          options: severityOptions,
          value: _severityFilter == null
              ? ''
              : _severityFilter!.name,
          onChange: (String v) => setState(() {
            if (v.isEmpty) {
              _severityFilter = null;
            } else {
              _severityFilter = AlertSeverity.values
                  .firstWhere((AlertSeverity s) => s.name == v);
            }
          }),
        ),
        ArcaneNativeSelect(
          options: serverOptions,
          value: _serverIdFilter ?? '',
          onChange: (String v) =>
              setState(() => _serverIdFilter = v.isEmpty ? null : v),
        ),
      ],
    );
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

  static ArcaneStatusBadge _severityBadge(AlertSeverity s) {
    return switch (s) {
      AlertSeverity.critical => ArcaneStatusBadge.error('critical'),
      AlertSeverity.warning => ArcaneStatusBadge.warning('warning'),
      AlertSeverity.info => ArcaneStatusBadge.info('info'),
    };
  }

  static String _formatTime(DateTime dt) {
    final Duration ago = DateTime.now().difference(dt);
    if (ago.inSeconds < 60) return '${ago.inSeconds}s ago';
    if (ago.inMinutes < 60) return '${ago.inMinutes}m ago';
    if (ago.inHours < 24) return '${ago.inHours}h ago';
    return dt.toIso8601String().substring(0, 16);
  }

  @override
  Widget build(BuildContext context) {
    return Card.elevated(
      fillWidth: true,
      child: dom.div(
        styles: const dom.Styles(raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.4rem',
          'padding': '0.75rem 1rem',
        }),
        <Widget>[
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.5rem',
              'flex-wrap': 'wrap',
            }),
            <Widget>[
              _severityBadge(alert.severity),
              dom.span(
                styles: const dom.Styles(raw: <String, String>{
                  'font-weight': '600',
                  'font-size': '0.9rem',
                }),
                <Widget>[Component.text(alert.serverName)],
              ),
              dom.span(
                styles: const dom.Styles(raw: <String, String>{
                  'font-weight': '500',
                  'font-size': '0.875rem',
                }),
                <Widget>[Component.text(alert.title)],
              ),
            ],
          ),
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'font-size': '0.8rem',
              'color': 'var(--muted-foreground)',
            }),
            <Widget>[Component.text(alert.message)],
          ),
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'font-size': '0.75rem',
              'color': 'var(--muted-foreground)',
            }),
            <Widget>[
              Component.text('First seen: ${_formatTime(alert.firstSeen)}'),
            ],
          ),
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'gap': '0.5rem',
              'align-items': 'center',
            }),
            <Widget>[
              if (!isAcked)
                Button.secondary(
                  label: 'Ack',
                  size: ButtonSize.small,
                  onPressed: onAck,
                )
              else ...<Widget>[
                dom.span(
                  styles: const dom.Styles(raw: <String, String>{
                    'font-size': '0.8rem',
                    'color': 'var(--muted-foreground)',
                  }),
                  <Widget>[Component.text('Acked')],
                ),
                Button.secondary(
                  label: 'Resolve',
                  size: ButtonSize.small,
                  onPressed: onResolve,
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
