/// Metrics (Advanced): every sampler the server publishes and the stored
/// history of one of them. The picked sampler lives in the address
/// (`?metric=<id>`), so other pages link straight to a history.
library;

import 'dart:async' show unawaited;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/metric_history.dart';
import '../model/reading.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../state/server_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/density.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import '../ui/kit/toggle_choice.dart';
import '../ui/error_text.dart';
import '../widget/row_window.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

/// A history range: its `?` free id, label and span (null: everything).
typedef HistoryRange = ({String id, String? label, Duration? span});

const List<HistoryRange> kHistoryRanges = <HistoryRange>[
  (id: '1h', label: '1h', span: Duration(hours: 1)),
  (id: '24h', label: '24h', span: Duration(hours: 24)),
  (id: '7d', label: '7d', span: Duration(days: 7)),
  (id: '30d', label: '30d', span: Duration(days: 30)),
  (id: 'all', label: null, span: null),
];

class MetricsExplorerScreen extends StatefulWidget {
  const MetricsExplorerScreen({this.serverId, this.metric, super.key});

  /// The open server; rows link to their own history on it.
  final String? serverId;

  /// The sampler named by the address (`?metric=`).
  final String? metric;

  @override
  State<MetricsExplorerScreen> createState() => _MetricsExplorerScreenState();
}

class _MetricsExplorerScreenState extends State<MetricsExplorerScreen> {
  String _query = '';
  String _range = '24h';
  IHistoryClient? _historyClient;
  List<MetricHistoryDescriptor> _catalog = <MetricHistoryDescriptor>[];
  bool _loadingCatalog = false;

  /// The sampler whose history is on screen (or loading).
  String? _shownId;
  String? _shownRange;
  List<MetricHistoryPoint> _points = <MetricHistoryPoint>[];
  Duration? _resolution;
  bool _loadingHistory = false;
  Object? _historyError;
  int _catalogGeneration = 0;
  int _queryGeneration = 0;
  List<MetricHistoryPoint>? _chartPoints;
  ReactorLocaleScope? _chartLocale;
  Widget? _chart;
  final Map<(String, String), (DateTime, List<MetricHistoryPoint>, Duration?)>
  _historyCache =
      <(String, String), (DateTime, List<MetricHistoryPoint>, Duration?)>{};

  /// The sampler to show: the address's, else the default.
  String? get _selected =>
      component.metric ??
      _shownId ??
      MetricHistoryDescriptor.preferredDefault(_catalog)?.id;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    _bindHistoryClient(scope?.historyClient);
    final String? selected = _selected;
    if (selected != null &&
        !_loadingCatalog &&
        (selected != _shownId || _range != _shownRange)) {
      _shownId = selected;
      _shownRange = _range;
      _points = <MetricHistoryPoint>[];
      _historyError = null;
      _loadingHistory = true;
      unawaited(Future<void>.microtask(() => _loadHistory(selected)));
    }

    final List<_MetricRow> rows = _rows(snapshot);
    if (rows.isEmpty && !_loadingCatalog) {
      return EmptyState(
        message: reactorText(ReactorText.metricsNonePublished),
        description: reactorText(ReactorText.metricsNonePublishedDescription),
        icon: ArcaneIcon.listFilter(size: IconSize.sm),
      );
    }
    final String query = _query.trim().toLowerCase();
    final List<_MetricRow> filtered = query.isEmpty
        ? rows
        : <_MetricRow>[
            for (final _MetricRow row in rows)
              if (row.id.toLowerCase().contains(query) ||
                  row.name.toLowerCase().contains(query) ||
                  row.suffix.toLowerCase().contains(query))
                row,
          ];

    return PageStack(
      children: <Widget>[
        _historyCard(snapshot, selected),
        AppCard(
          title: reactorText(ReactorText.metricsCatalog),
          tip: reactorText(ReactorText.metricsCatalogDescription),
          actions: ActionRow(
            dense: true,
            label: reactorText(ReactorText.metricsCatalog),
            leading: <Widget>[_search()],
          ),
          children: <Widget>[
            dom.p(classes: 'ui-caption', <Widget>[
              Component.text(
                reactorText(ReactorText.metricsVisibleCount, <String, Object?>{
                  'visible': filtered.length,
                  'total': rows.length,
                }),
              ),
            ]),
            if (filtered.isEmpty)
              EmptyState(
                message: reactorText(ReactorText.metricsNoMatches),
                description: reactorText(ReactorText.metricsTryDifferentFilter),
                icon: ArcaneIcon.searchX(size: IconSize.sm),
              )
            else
              RowWindow<_MetricRow>(
                key: ValueKey<String>(query),
                rows: filtered,
                pageSize: 20,
                builder: _table,
              ),
          ],
        ),
      ],
    );
  }

  Widget _search() => TextInput(
    type: TextInputType.search,
    size: controlSize(Density.dense),
    value: _query,
    placeholder: reactorText(ReactorText.metricsSearchPlaceholder),
    attributes: <String, String>{
      'aria-label': reactorText(ReactorText.metricsSearchPlaceholder),
    },
    fullWidth: true,
    onChanged: (String value) => setState(() => _query = value),
  );

  Widget _historyCard(ServerSnapshot snapshot, String? selected) {
    final MetricHistoryDescriptor? descriptor = selected == null
        ? null
        : _descriptor(selected);
    final SamplerSample? live = selected == null
        ? null
        : snapshot.sampler(selected);
    final String title =
        descriptor?.name ??
        live?.name ??
        selected ??
        reactorText(ReactorText.metricsHistory);
    return ChartCard(
      title: title,
      tip:
          _facts(selected, descriptor) ??
          reactorText(ReactorText.metricsHistoryDescription),
      actions: ActionRow(
        dense: true,
        label: reactorText(ReactorText.metricsHistory),
        leading: <Widget>[
          ToggleChoice<String>(
            label: reactorText(ReactorText.metricsHistory),
            options: <ChoiceOption<String>>[
              for (final HistoryRange range in kHistoryRanges)
                ChoiceOption<String>(
                  range.id,
                  range.label ?? reactorText(ReactorText.metricsRangeAll),
                ),
            ],
            value: _range,
            onChanged: _selectRange,
          ),
        ],
      ),
      child: _historyBody(selected, descriptor, live),
    );
  }

  Widget _historyBody(
    String? selected,
    MetricHistoryDescriptor? descriptor,
    SamplerSample? live,
  ) {
    if (_historyClient == null) {
      return EmptyState(
        message: reactorText(ReactorText.metricsHistoryUnavailable),
      );
    }
    if (selected == null) {
      return _loadingCatalog
          ? LoadingState(
              label: reactorText(ReactorText.metricsLoadingHistory),
              height: '260px',
            )
          : EmptyState(message: reactorText(ReactorText.metricsSelectHistory));
    }
    final Object? error = _historyError;
    final Widget body;
    if (_loadingHistory) {
      body = LoadingState(
        label: reactorText(ReactorText.metricsLoadingHistory),
        height: '260px',
      );
    } else if (error != null) {
      body = ErrorState(
        message: localizedReactorError(error),
        onRetry: () => _reload(selected),
      );
    } else if (_points.isEmpty) {
      body = EmptyState(message: reactorText(ReactorText.metricsNoSamples));
    } else {
      body = _historyChart(descriptor?.suffix ?? live?.suffix ?? '');
    }
    return body;
  }

  /// What the chart shows (sampler, stored span, state, points,
  /// resolution), behind the chart's (i); null before a pick.
  String? _facts(String? selected, MetricHistoryDescriptor? descriptor) {
    if (selected == null) return null;
    final bool stored = descriptor == null || descriptor.hasSamples;
    return <String>[
      selected,
      if (descriptor != null && stored)
        reactorText(ReactorText.metricsStoredCoverage, <String, Object?>{
          'from': _formatTimestamp(descriptor.firstAt),
          'to': _formatTimestamp(descriptor.lastAt),
        }),
      if (descriptor != null)
        descriptor.active
            ? reactorText(ReactorText.metricsActive)
            : reactorText(ReactorText.metricsDormant),
      if (_points.isNotEmpty)
        reactorText(ReactorText.metricsPointCount, <String, Object?>{
          'count': _points.length,
        }),
      if (_points.isNotEmpty && _resolution != null)
        reactorText(ReactorText.metricsResolution, <String, Object?>{
          'resolution': _formatResolution(_resolution!),
        }),
    ].join(' · ');
  }

  Widget _historyChart(String suffix) {
    final ReactorLocaleScope? locale = dependOnReactorLocale(context);
    if (!identical(_chartPoints, _points) || !identical(_chartLocale, locale)) {
      _chartPoints = _points;
      _chartLocale = locale;
      _chart = TimeseriesChart(
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
            .map((MetricHistoryPoint point) => _axisLabel(point.at))
            .toList(),
        sampleTimestamps: _points
            .map((MetricHistoryPoint point) => point.at)
            .toList(growable: false),
        secondarySeries: const <int>{1, 2},
        unit: suffix,
        valueFormatter: sizeFormatter(suffix),
        height: 260,
      );
    }
    return _chart!;
  }

  Widget _table(List<_MetricRow> rows) {
    final String? serverId = component.serverId;
    return DataList<_MetricRow>(
      label: reactorText(ReactorText.metricsCatalog),
      items: rows,
      href: serverId == null
          ? null
          : (_MetricRow row) => serverPagePath(
              serverId,
              'metrics',
              <String, String>{'metric': row.id},
            ),
      columns: <ListColumn<_MetricRow>>[
        ListColumn<_MetricRow>(
          reactorText(ReactorText.metricsSampler),
          (_MetricRow row) => dom.div(
            attributes: <String, String>{
              if (row.id == _shownId) 'aria-current': 'true',
            },
            <Widget>[
              dom.div(<Widget>[Component.text(row.name)]),
              // The unit once per row; the figures beside it are bare.
              dom.div(classes: 'ui-caption', <Widget>[
                dom.code(<Widget>[Component.text(row.id)]),
                if (row.unit.isNotEmpty) Component.text(' · ${row.unit}'),
              ]),
            ],
          ),
          width: 'minmax(0, 2fr)',
        ),
        ListColumn<_MetricRow>(
          reactorText(ReactorText.metricsCurrent),
          (_MetricRow row) => Component.text(readingOf(row.sample).value),
          numeric: true,
        ),
        ListColumn<_MetricRow>(
          reactorText(ReactorText.metricsMinimum),
          (_MetricRow row) => Component.text(
            row.sample == null
                ? '--'
                : readingAt(row.sample!, row.sample!.min).value,
          ),
          numeric: true,
        ),
        ListColumn<_MetricRow>(
          reactorText(ReactorText.metricsMaximum),
          (_MetricRow row) => Component.text(
            row.sample == null
                ? '--'
                : readingAt(row.sample!, row.sample!.max).value,
          ),
          numeric: true,
        ),
        ListColumn<_MetricRow>(
          reactorText(ReactorText.metricsSamples),
          (_MetricRow row) =>
              Component.text(row.sample?.history.length.toString() ?? '--'),
          numeric: true,
          width: '6rem',
        ),
      ],
    );
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
      if (!sample.available) continue;
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
    // Samplers with a reading first, then by id.
    return rows.values.toList()..sort((_MetricRow left, _MetricRow right) {
      final bool leftData = left.sample?.available ?? false;
      final bool rightData = right.sample?.available ?? false;
      if (leftData != rightData) return leftData ? -1 : 1;
      return left.id.compareTo(right.id);
    });
  }

  /// A new server (or a re-pair) starts over: its catalog, its history.
  void _bindHistoryClient(IHistoryClient? client) {
    if (identical(client, _historyClient)) return;
    _historyClient = client;
    _historyCache.clear();
    _catalog = <MetricHistoryDescriptor>[];
    _points = <MetricHistoryPoint>[];
    _shownId = null;
    _shownRange = null;
    _historyError = null;
    _loadingHistory = false;
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
      setState(() {
        _catalog = catalog;
        _loadingCatalog = false;
      });
    } on Object catch (error) {
      if (!mounted || generation != _catalogGeneration) return;
      setState(() {
        _loadingCatalog = false;
        _historyError = error;
      });
    }
  }

  void _selectRange(String range) {
    if (_range == range) return;
    setState(() => _range = range);
  }

  void _reload(String id) {
    _historyCache.remove((id, _range));
    setState(() {
      _shownId = null;
      _shownRange = null;
    });
  }

  Future<void> _loadHistory(String id) async {
    final IHistoryClient? client = _historyClient;
    if (client == null) return;
    final int generation = ++_queryGeneration;
    final String rangeId = _range;
    final (String, String) cacheKey = (id, rangeId);
    final (DateTime, List<MetricHistoryPoint>, Duration?)? cached =
        _historyCache[cacheKey];
    if (cached != null &&
        DateTime.now().difference(cached.$1) < const Duration(seconds: 30)) {
      if (!mounted) return;
      setState(() {
        _points = cached.$2;
        _resolution = cached.$3;
        _loadingHistory = false;
        _historyError = null;
      });
      return;
    }
    final MetricHistoryDescriptor? descriptor = _descriptor(id);
    if (descriptor != null && !descriptor.hasSamples) {
      if (!mounted) return;
      setState(() {
        _points = <MetricHistoryPoint>[];
        _loadingHistory = false;
      });
      return;
    }
    final DateTime to = DateTime.now().add(const Duration(milliseconds: 1));
    final Duration? span = kHistoryRanges
        .firstWhere((HistoryRange range) => range.id == rangeId)
        .span;
    final DateTime from = span == null
        ? descriptor?.firstAt ?? to.subtract(const Duration(days: 30))
        : to.subtract(span);
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
      if (_historyCache.length >= 8) {
        _historyCache.remove(_historyCache.keys.first);
      }
      _historyCache[cacheKey] = (DateTime.now(), points, resolution);
      setState(() {
        _points = points;
        _resolution = resolution;
        _loadingHistory = false;
      });
    } on Object catch (error) {
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        _loadingHistory = false;
        _historyError = error;
      });
    }
  }

  MetricHistoryDescriptor? _descriptor(String id) {
    for (final MetricHistoryDescriptor descriptor in _catalog) {
      if (descriptor.id == id) return descriptor;
    }
    return null;
  }

  /// A short time for the chart's axis, so three fit on a phone: the time
  /// of day within a day's span, else the date and time.
  String _axisLabel(DateTime value) {
    final String full = _formatTimestamp(value);
    final bool withinDay =
        _points.isNotEmpty &&
        _points.last.at.difference(_points.first.at) <=
            const Duration(hours: 24);
    return withinDay ? full.substring(11, 16) : full.substring(5, 16);
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

  /// The unit of the figures in this row: the live reading's (sizes in
  /// the unit it is shown in), else the catalog's.
  String get unit {
    final SamplerSample? live = sample;
    return (live == null ? suffix : readingOf(live).unit).trim();
  }

  const _MetricRow({
    required this.id,
    required this.name,
    required this.suffix,
    required this.sample,
  });
}
