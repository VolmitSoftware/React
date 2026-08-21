library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/connection_manager.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../ui/reactor_ui.dart';

const List<String> _kComparableMetrics = <String>[
  'ticks-per-second',
  'tick-time',
  'players',
  'entities',
  'chunks',
  'memory-used',
  'incident-score',
  'gc-time-percent',
  'player-ping-p95',
];

typedef _ServerMetricEntry = ({
  String id,
  String name,
  double value,
  String display,
  String suffix,
  List<double> history,
});

bool _hasComparableSnapshot(FleetServerLive server) =>
    currentFleetSnapshot(server) != null;

class ComparisonScreen extends StatefulWidget {
  const ComparisonScreen({super.key});

  @override
  State<ComparisonScreen> createState() => _ComparisonScreenState();
}

class _ComparisonScreenState extends State<ComparisonScreen> {
  String _selectedMetric = 'ticks-per-second';
  Set<String>? _selectedServerIds;

  @override
  Widget build(BuildContext context) {
    final List<FleetServerLive> servers =
        FleetLiveScope.of(context)?.servers ?? <FleetServerLive>[];

    final Set<String> selectedIds =
        _selectedServerIds ?? servers.map((FleetServerLive s) => s.id).toSet();

    final Set<String> presentMetrics = <String>{};
    for (final FleetServerLive srv in servers) {
      if (_hasComparableSnapshot(srv)) {
        presentMetrics.addAll(srv.snapshot!.byId.keys);
      }
    }

    final List<String> availableMetrics = _kComparableMetrics
        .where((String m) => presentMetrics.contains(m))
        .toList();
    final List<_ServerMetricEntry> activeEntries = servers
        .where(
          (FleetServerLive s) =>
              selectedIds.contains(s.id) &&
              _hasComparableSnapshot(s) &&
              s.snapshot?.sampler(_selectedMetric) != null,
        )
        .map((FleetServerLive s) {
          final SamplerSample sample = s.snapshot!.sampler(_selectedMetric)!;
          return (
            id: s.id,
            name: s.name,
            value: sample.value,
            display: sample.display,
            suffix: sample.suffix,
            history: sample.history,
          );
        })
        .toList();

    final List<_ServerMetricEntry> leaderboard =
        List<_ServerMetricEntry>.from(activeEntries)..sort(
          (_ServerMetricEntry a, _ServerMetricEntry b) =>
              b.value.compareTo(a.value),
        );

    final List<(String, List<double>)> chartSeries = activeEntries
        .map((_ServerMetricEntry e) => (e.name, e.history))
        .toList();

    final bool isEmpty = activeEntries.isEmpty;
    final int selectedServerCount = servers
        .where((FleetServerLive server) => selectedIds.contains(server.id))
        .length;
    final int selectedComparableCount = servers
        .where(
          (FleetServerLive server) =>
              selectedIds.contains(server.id) && _hasComparableSnapshot(server),
        )
        .length;
    final int selectedUnavailableCount =
        selectedServerCount - selectedComparableCount;

    return ReactorPage(
      title: reactorText(ReactorText.comparisonTitle),
      subtitle: reactorText(ReactorText.comparisonSubtitle),
      actions: _metricFilter(availableMetrics),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.comparisonServers),
          description: servers.isEmpty
              ? 'Pair servers to compare telemetry.'
              : '$selectedServerCount of ${servers.length} selected'
                    '${selectedUnavailableCount > 0 ? ' · $selectedUnavailableCount unavailable excluded' : ''}',
          flush: true,
          children: <Widget>[
            _serverSelector(servers: servers, selectedIds: selectedIds),
            if (isEmpty)
              ReactorEmptyState(
                title: servers.isEmpty
                    ? 'No servers available'
                    : selectedServerCount == 0
                    ? 'No servers selected'
                    : reactorText(ReactorText.comparisonNoData),
                description: servers.isEmpty
                    ? 'Pair at least one server to open the comparison workspace.'
                    : selectedServerCount == 0
                    ? 'Select one or more servers from the toolbar above.'
                    : selectedComparableCount == 0
                    ? 'Selected servers are offline or awaiting current telemetry.'
                    : 'The selected metric has not published comparable samples.',
              )
            else ...<Widget>[
              dom.div(classes: 'reactor-comparison-ranking', <Widget>[
                dom.div(classes: 'reactor-subsection-heading', <Widget>[
                  reactorEyebrow(
                    reactorText(ReactorText.comparisonLeaderboard),
                  ),
                ]),
                _leaderboardTable(leaderboard),
              ]),
              dom.div(classes: 'reactor-comparison-chart', <Widget>[
                dom.div(classes: 'reactor-subsection-heading', <Widget>[
                  reactorEyebrow(reactorText(ReactorText.comparisonOverlay)),
                ]),
                TimeseriesChart(series: chartSeries, height: 240),
              ]),
            ],
          ],
        ),
      ],
    );
  }

  Widget _metricFilter(List<String> availableMetrics) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '0.6rem',
        },
      ),
      <Widget>[
        reactorEyebrow(reactorText(ReactorText.comparisonMetric)),
        if (availableMetrics.isEmpty)
          reactorBadge('No sampled metrics', ReactorStatus.neutral)
        else
          ArcaneNativeSelect(
            options: <ArcaneSelectOption>[
              if (!availableMetrics.contains(_selectedMetric))
                ArcaneSelectOption(
                  label: '$_selectedMetric (unavailable)',
                  value: _selectedMetric,
                ),
              ...availableMetrics.map(
                (String m) => ArcaneSelectOption(label: m, value: m),
              ),
            ],
            value: _selectedMetric,
            onChange: (String v) => setState(() => _selectedMetric = v),
          ),
      ],
    );
  }

  Widget _serverSelector({
    required List<FleetServerLive> servers,
    required Set<String> selectedIds,
  }) {
    return dom.div(
      classes: 'reactor-comparison-selector reactor-filter-bar',
      <Widget>[
        for (final FleetServerLive srv in servers)
          ArcaneCheckbox(
            label: _serverLabel(srv),
            checked: selectedIds.contains(srv.id),
            onChanged: (bool checked) {
              setState(() {
                final Set<String> next = Set<String>.from(selectedIds);
                if (checked) {
                  next.add(srv.id);
                } else {
                  next.remove(srv.id);
                }
                _selectedServerIds = next;
              });
            },
          ),
      ],
    );
  }

  String _serverLabel(FleetServerLive server) {
    if (server.state == ConnState.offline) {
      return '${server.name} · Offline (excluded)';
    }
    if (server.state == ConnState.connecting) {
      return '${server.name} · Connecting (excluded)';
    }
    if (server.state == ConnState.degraded) {
      return server.snapshot == null
          ? '${server.name} · Degraded (no telemetry)'
          : '${server.name} · Degraded (last received)';
    }
    if (server.snapshot == null) {
      return '${server.name} · Awaiting telemetry (excluded)';
    }
    return server.name;
  }

  Widget _leaderboardTable(List<_ServerMetricEntry> ranked) {
    return dom.div(classes: 'reactor-table reactor-leaderboard-table', <Widget>[
      for (int i = 0; i < ranked.length; i++)
        _leaderboardRow(rank: i + 1, entry: ranked[i]),
    ]);
  }

  Widget _leaderboardRow({
    required int rank,
    required _ServerMetricEntry entry,
  }) {
    return dom.div(classes: 'reactor-leaderboard-row', <Widget>[
      dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'min-width': '2rem',
            'font-size': '0.75rem',
            'color': 'var(--muted-foreground)',
            'font-weight': '600',
          },
        ),
        <Widget>[Component.text('#$rank')],
      ),
      dom.div(
        styles: const dom.Styles(
          raw: <String, String>{'flex': '1', 'font-size': '0.875rem'},
        ),
        <Widget>[Component.text(entry.name)],
      ),
      dom.div(
        styles: const dom.Styles(
          raw: <String, String>{'font-size': '0.875rem', 'font-weight': '500'},
        ),
        <Widget>[
          Component.text(
            entry.suffix.isEmpty
                ? entry.display
                : '${entry.display} ${entry.suffix}',
          ),
        ],
      ),
    ]);
  }
}
