library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/input/native_select.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';

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
      if (srv.snapshot != null) {
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

    return ReactorPage(
      title: reactorText(ReactorText.comparisonTitle),
      subtitle: reactorText(ReactorText.comparisonSubtitle),
      actions: _metricFilter(availableMetrics),
      children: <Widget>[
        _serversCard(servers: servers, selectedIds: selectedIds),
        sectionCard(
          label: reactorText(ReactorText.comparisonOverlay),
          child: isEmpty
              ? _emptyState(reactorText(ReactorText.comparisonNoData))
              : TimeseriesChart(series: chartSeries, height: 220),
        ),
        sectionCard(
          label: reactorText(ReactorText.comparisonLeaderboard),
          flush: true,
          child: isEmpty
              ? _emptyState(reactorText(ReactorText.comparisonNoData))
              : _leaderboardTable(leaderboard),
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
        ArcaneNativeSelect(
          options: availableMetrics
              .map((String m) => ArcaneSelectOption(label: m, value: m))
              .toList(),
          value: _selectedMetric,
          onChange: (String v) => setState(() => _selectedMetric = v),
        ),
      ],
    );
  }

  Widget _serversCard({
    required List<FleetServerLive> servers,
    required Set<String> selectedIds,
  }) {
    return sectionCard(
      label: reactorText(ReactorText.comparisonServers),
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-wrap': 'wrap',
            'gap': '0.75rem',
          },
        ),
        <Widget>[
          for (final FleetServerLive srv in servers)
            ArcaneCheckbox(
              label: srv.name,
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
      ),
    );
  }

  Widget _leaderboardTable(List<_ServerMetricEntry> ranked) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'display': 'flex', 'flex-direction': 'column'},
      ),
      <Widget>[
        for (int i = 0; i < ranked.length; i++)
          _leaderboardRow(rank: i + 1, entry: ranked[i]),
      ],
    );
  }

  Widget _leaderboardRow({
    required int rank,
    required _ServerMetricEntry entry,
  }) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '1rem',
          'padding': '0.65rem 1.15rem',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
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
            raw: <String, String>{
              'font-size': '0.875rem',
              'font-weight': '500',
            },
          ),
          <Widget>[
            Component.text(
              entry.suffix.isEmpty
                  ? entry.display
                  : '${entry.display} ${entry.suffix}',
            ),
          ],
        ),
      ],
    );
  }

  Widget _emptyState(String message) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'padding': '2rem',
          'text-align': 'center',
          'color': 'var(--muted-foreground)',
          'font-size': '0.875rem',
        },
      ),
      <Widget>[Component.text(message)],
    );
  }
}
