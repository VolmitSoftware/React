library;

import 'dart:async' show unawaited;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/metric_history.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/server_snapshot_state.dart';

class MetricsExplorerScreen extends StatefulWidget {
  const MetricsExplorerScreen({super.key});

  @override
  State<MetricsExplorerScreen> createState() => _MetricsExplorerScreenState();
}

class _MetricsExplorerScreenState extends State<MetricsExplorerScreen> {
  static const Map<String, Duration?> _ranges = <String, Duration?>{
    '1h': Duration(hours: 1),
    '24h': Duration(hours: 24),
    '7d': Duration(days: 7),
    '30d': Duration(days: 30),
    'All': null,
  };

  String _query = '';
  String _range = '24h';
  String? _selectedId;
  IHistoryClient? _historyClient;
  List<MetricHistoryDescriptor> _catalog = <MetricHistoryDescriptor>[];
  List<MetricHistoryPoint> _points = <MetricHistoryPoint>[];
  Duration? _resolution;
  bool _loadingCatalog = false;
  bool _loadingHistory = false;
  String? _historyError;
  int _catalogGeneration = 0;
  int _queryGeneration = 0;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      icon: ArcaneIcon.listFilter(size: IconSize.sm),
    );
    if (snapshot == null) return statePage!;

    _bindHistoryClient(scope?.historyClient);
    final List<_MetricRow> rows = _rows(snapshot);
    final String normalizedQuery = _query.trim().toLowerCase();
    final List<_MetricRow> filtered = normalizedQuery.isEmpty
        ? rows
        : rows.where((_MetricRow row) {
            return row.id.toLowerCase().contains(normalizedQuery) ||
                row.name.toLowerCase().contains(normalizedQuery) ||
                row.suffix.toLowerCase().contains(normalizedQuery);
          }).toList();

    if (rows.isEmpty && !_loadingCatalog) {
      return _statePage(
        ReactorEmptyState(
          title: reactorText(ReactorText.metricsNonePublished),
          description: reactorText(ReactorText.metricsNonePublishedDescription),
          icon: ArcaneIcon.listFilter(size: IconSize.sm),
        ),
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      actions: reactorBadge(
        reactorText(ReactorText.metricsVisibleCount, <String, Object?>{
          'visible': filtered.length,
          'total': rows.length,
        }),
        ReactorStatus.neutral,
      ),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.metricsHistory),
          description: reactorText(ReactorText.metricsHistoryDescription),
          flush: true,
          children: <Widget>[_historyPanel(snapshot)],
        ),
        SectionPanel(
          label: reactorText(ReactorText.metricsCatalog),
          description: reactorText(ReactorText.metricsCatalogDescription),
          flush: true,
          children: <Widget>[
            dom.div(classes: 'reactor-metrics-toolbar', <Widget>[
              dom.div(classes: 'reactor-metrics-search', <Widget>[
                TextInput(
                  value: _query,
                  placeholder: reactorText(
                    ReactorText.metricsSearchPlaceholder,
                  ),
                  onChange: (String value) => setState(() => _query = value),
                  fullWidth: true,
                ),
              ]),
              dom.span(classes: 'reactor-metrics-updated', <Widget>[
                Component.text(
                  reactorText(ReactorText.metricsSequence, <String, Object?>{
                    'sequence': snapshot.seq,
                  }),
                ),
              ]),
            ]),
            if (filtered.isEmpty)
              ReactorEmptyState(
                title: reactorText(ReactorText.metricsNoMatches),
                description: reactorText(ReactorText.metricsTryDifferentFilter),
                icon: ArcaneIcon.searchX(size: IconSize.sm),
              )
            else
              _metricTable(filtered),
          ],
        ),
      ],
    );
  }

  Widget _historyPanel(ServerSnapshot snapshot) {
    if (_historyClient == null) {
      return dom.div(classes: 'reactor-history-state', <Widget>[
        Component.text(reactorText(ReactorText.metricsHistoryUnavailable)),
      ]);
    }
    final String? selectedId = _selectedId;
    if (selectedId == null) {
      return dom.div(classes: 'reactor-history-state', <Widget>[
        Component.text(
          _loadingCatalog
              ? reactorText(ReactorText.metricsLoadingHistory)
              : reactorText(ReactorText.metricsSelectHistory),
        ),
      ]);
    }
    final MetricHistoryDescriptor? descriptor = _descriptor(selectedId);
    final SamplerSample? live = snapshot.sampler(selectedId);
    final String name = descriptor?.name ?? live?.name ?? selectedId;
    final String suffix = descriptor?.suffix ?? live?.suffix ?? '';

    return dom.div(classes: 'reactor-history-panel', <Widget>[
      dom.div(classes: 'reactor-history-toolbar', <Widget>[
        dom.div(classes: 'reactor-history-heading', <Widget>[
          dom.strong(<Widget>[Component.text(name)]),
          dom.code(<Widget>[Component.text(selectedId)]),
        ]),
        dom.div(classes: 'reactor-history-ranges', <Widget>[
          for (final String label in _ranges.keys)
            dom.button(
              classes:
                  'reactor-history-range${_range == label ? ' is-selected' : ''}',
              attributes: <String, String>{
                'type': 'button',
                'aria-pressed': (_range == label).toString(),
              },
              events: <String, EventCallback>{
                'click': (_) => _selectRange(label),
              },
              <Widget>[Component.text(label)],
            ),
        ]),
      ]),
      if (descriptor != null)
        dom.div(classes: 'reactor-history-meta', <Widget>[
          dom.span(<Widget>[
            Component.text(
              reactorText(ReactorText.metricsStoredCoverage, <String, Object?>{
                'from': _formatTimestamp(descriptor.firstAt),
                'to': _formatTimestamp(descriptor.lastAt),
              }),
            ),
          ]),
          dom.span(<Widget>[
            Component.text(
              descriptor.active
                  ? reactorText(ReactorText.metricsActive)
                  : reactorText(ReactorText.metricsDormant),
            ),
          ]),
        ]),
      if (_loadingHistory)
        dom.div(classes: 'reactor-history-state', <Widget>[
          Component.text(reactorText(ReactorText.metricsLoadingHistory)),
        ])
      else if (_historyError != null)
        dom.div(classes: 'reactor-history-state is-error', <Widget>[
          Component.text(_historyError!),
        ])
      else if (_points.isEmpty)
        dom.div(classes: 'reactor-history-state', <Widget>[
          Component.text(reactorText(ReactorText.chartAwaitingSamples)),
        ])
      else ...<Widget>[
        dom.div(classes: 'reactor-history-summary', <Widget>[
          dom.span(<Widget>[
            Component.text(
              reactorText(ReactorText.metricsPointCount, <String, Object?>{
                'count': _points.length,
              }),
            ),
          ]),
          if (_resolution != null)
            dom.span(<Widget>[
              Component.text(
                reactorText(ReactorText.metricsResolution, <String, Object?>{
                  'resolution': _formatResolution(_resolution!),
                }),
              ),
            ]),
        ]),
        TimeseriesChart(
          series: <(String, List<double>)>[
            (
              reactorText(ReactorText.metricsAverage),
              _points.map((MetricHistoryPoint point) => point.average).toList(),
            ),
            (
              reactorText(ReactorText.metricsMinimum),
              _points.map((MetricHistoryPoint point) => point.minimum).toList(),
            ),
            (
              reactorText(ReactorText.metricsMaximum),
              _points.map((MetricHistoryPoint point) => point.maximum).toList(),
            ),
            (
              reactorText(ReactorText.metricsLast),
              _points.map((MetricHistoryPoint point) => point.last).toList(),
            ),
          ],
          sampleLabels: _points
              .map((MetricHistoryPoint point) => _formatTimestamp(point.at))
              .toList(),
          sampleTimestamps: _points
              .map((MetricHistoryPoint point) => point.at)
              .toList(growable: false),
          secondarySeries: const <int>{1, 2},
          valueFormatter: (double value) => _format(value, suffix),
          height: 260,
        ),
      ],
    ]);
  }

  Widget _metricTable(List<_MetricRow> rows) {
    return dom.div(classes: 'reactor-metrics-table-wrap', <Widget>[
      dom.table(classes: 'reactor-metrics-table', <Widget>[
        dom.thead(<Widget>[
          dom.tr(<Widget>[
            _heading(reactorText(ReactorText.metricsSampler)),
            _heading(reactorText(ReactorText.metricsCurrent)),
            _heading(reactorText(ReactorText.metricsMinimum)),
            _heading(reactorText(ReactorText.metricsMaximum)),
            _heading(reactorText(ReactorText.metricsSamples)),
          ]),
        ]),
        dom.tbody(<Widget>[
          for (final _MetricRow row in rows)
            dom.tr(classes: _selectedId == row.id ? 'is-selected' : null, <
              Widget
            >[
              dom.td(<Widget>[
                dom.button(
                  classes: 'reactor-metric-select',
                  attributes: const <String, String>{'type': 'button'},
                  events: <String, EventCallback>{
                    'click': (_) => _selectMetric(row.id),
                  },
                  <Widget>[
                    dom.span(classes: 'reactor-metric-name', <Widget>[
                      Component.text(row.name),
                    ]),
                    dom.code(classes: 'reactor-metric-id', <Widget>[
                      Component.text(row.id),
                    ]),
                  ],
                ),
              ]),
              _numberCell(row.sample?.display ?? '—'),
              _numberCell(
                row.sample == null ? '—' : _format(row.sample!.min, row.suffix),
              ),
              _numberCell(
                row.sample == null ? '—' : _format(row.sample!.max, row.suffix),
              ),
              _numberCell(
                row.sample?.history.length.toString() ??
                    reactorText(ReactorText.metricsDormant),
              ),
            ]),
        ]),
      ]),
    ]);
  }

  List<_MetricRow> _rows(ServerSnapshot snapshot) {
    final Map<String, _MetricRow> rows = <String, _MetricRow>{};
    for (final MetricHistoryDescriptor descriptor in _catalog) {
      rows[descriptor.id] = _MetricRow(
        id: descriptor.id,
        name: descriptor.name,
        suffix: descriptor.suffix,
        sample: snapshot.sampler(descriptor.id),
      );
    }
    for (final SamplerSample sample in snapshot.byId.values) {
      rows.putIfAbsent(
        sample.id,
        () => _MetricRow(
          id: sample.id,
          name: sample.name,
          suffix: sample.suffix,
          sample: sample,
        ),
      );
    }
    final List<_MetricRow> result = rows.values.toList();
    result.sort(
      (_MetricRow left, _MetricRow right) => left.id.compareTo(right.id),
    );
    return result;
  }

  void _bindHistoryClient(IHistoryClient? client) {
    if (identical(client, _historyClient)) return;
    _historyClient = client;
    _catalog = <MetricHistoryDescriptor>[];
    _points = <MetricHistoryPoint>[];
    _selectedId = null;
    _historyError = null;
    final int generation = ++_catalogGeneration;
    _queryGeneration++;
    if (client == null) return;
    _loadingCatalog = true;
    unawaited(_loadCatalog(client, generation));
  }

  Future<void> _loadCatalog(IHistoryClient client, int generation) async {
    try {
      final List<MetricHistoryDescriptor> catalog = await client
          .historyCatalog();
      if (!mounted || generation != _catalogGeneration) return;
      catalog.sort(
        (MetricHistoryDescriptor left, MetricHistoryDescriptor right) =>
            left.id.compareTo(right.id),
      );
      final String? selected = catalog.isEmpty ? null : catalog.first.id;
      setState(() {
        _catalog = catalog;
        _loadingCatalog = false;
        _selectedId = selected;
      });
      if (selected != null) unawaited(_loadHistory(selected));
    } on Object catch (error) {
      if (!mounted || generation != _catalogGeneration) return;
      setState(() {
        _loadingCatalog = false;
        _historyError = error.toString();
      });
    }
  }

  void _selectMetric(String id) {
    if (_selectedId == id && _points.isNotEmpty) return;
    setState(() {
      _selectedId = id;
      _points = <MetricHistoryPoint>[];
      _historyError = null;
    });
    unawaited(_loadHistory(id));
  }

  void _selectRange(String range) {
    if (_range == range) return;
    setState(() {
      _range = range;
      _points = <MetricHistoryPoint>[];
      _historyError = null;
    });
    final String? selectedId = _selectedId;
    if (selectedId != null) unawaited(_loadHistory(selectedId));
  }

  Future<void> _loadHistory(String id) async {
    final IHistoryClient? client = _historyClient;
    if (client == null) return;
    final int generation = ++_queryGeneration;
    final DateTime to = DateTime.now().add(const Duration(milliseconds: 1));
    final Duration? range = _ranges[_range];
    final MetricHistoryDescriptor? descriptor = _descriptor(id);
    final DateTime from = range == null
        ? descriptor?.firstAt ?? to.subtract(const Duration(days: 30))
        : to.subtract(range);
    setState(() {
      _loadingHistory = true;
      _historyError = null;
    });
    try {
      final List<MetricHistoryPoint> points = <MetricHistoryPoint>[];
      String? cursor;
      Duration? resolution;
      int pages = 0;
      do {
        final MetricHistoryPage page = await client.historyPage(
          ids: cursor == null ? <String>[id] : null,
          from: cursor == null ? from : null,
          to: cursor == null ? to : null,
          maxPoints: 1200,
          pageSize: 256,
          cursor: cursor,
        );
        if (!mounted || generation != _queryGeneration) return;
        if (page.series.isNotEmpty) points.addAll(page.series.first.points);
        resolution = page.resolution;
        cursor = page.nextCursor;
        pages++;
        if (pages > 64) {
          throw StateError('History response exceeded the page safety limit');
        }
      } while (cursor != null);
      points.sort(
        (MetricHistoryPoint left, MetricHistoryPoint right) =>
            left.at.compareTo(right.at),
      );
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        _points = points;
        _resolution = resolution;
        _loadingHistory = false;
      });
    } on Object catch (error) {
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        _loadingHistory = false;
        _historyError = error.toString();
      });
    }
  }

  MetricHistoryDescriptor? _descriptor(String id) {
    for (final MetricHistoryDescriptor descriptor in _catalog) {
      if (descriptor.id == id) return descriptor;
    }
    return null;
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      children: <Widget>[state],
    );
  }

  Widget _heading(String label) => dom.th(<Widget>[Component.text(label)]);

  Widget _numberCell(String value) =>
      dom.td(classes: 'reactor-metric-number', <Widget>[Component.text(value)]);

  String _format(double value, String suffix) {
    final String number = value == value.roundToDouble()
        ? value.toInt().toString()
        : value.toStringAsFixed(2);
    return suffix.isEmpty ? number : '$number $suffix';
  }

  String _formatTimestamp(DateTime value) {
    final String local = value.toLocal().toIso8601String();
    return local.replaceFirst('T', ' ').split('.').first;
  }

  String _formatResolution(Duration value) {
    if (value.inHours >= 1) return '${value.inHours}h';
    if (value.inMinutes >= 1) return '${value.inMinutes}m';
    return '${value.inSeconds}s';
  }
}

class _MetricRow {
  final String id;
  final String name;
  final String suffix;
  final SamplerSample? sample;

  const _MetricRow({
    required this.id,
    required this.name,
    required this.suffix,
    required this.sample,
  });
}
