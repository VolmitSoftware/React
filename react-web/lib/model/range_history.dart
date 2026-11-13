/// The stored ranges of the Overview and Performance charts: what the
/// server keeps for them (`GET /metrics/range`), extended with the live
/// frames that arrive after it.
library;

/// The samplers those charts draw, in the order the plugin keeps them.
const List<String> kChartSamplerIds = <String>[
  'tick-time',
  'tick-ms-p50',
  'tick-ms-p95',
  'tick-ms-p99',
  'memory-used',
  'memory-free',
  'memory-used-after-gc',
  'disk-read-rate',
  'disk-write-rate',
  'network-receive-rate',
  'network-send-rate',
  'react-async-tick-time',
  'react-sync-tick-time',
];

/// A chart's time range: the live frames since the page opened, or a span
/// the server keeps in [buckets] averages.
enum ChartRange {
  live('live', Duration.zero),
  hour('1h', Duration(seconds: 10)),
  sixHours('6h', Duration(minutes: 1)),
  day('24h', Duration(minutes: 4));

  const ChartRange(this.id, this.step);

  /// Buckets in every stored range.
  static const int buckets = 360;

  /// The wire and address name.
  final String id;

  /// One bucket; zero for [live].
  final Duration step;

  bool get stored => this != ChartRange.live;

  Duration get span => step * buckets;

  static ChartRange? byId(String id) {
    for (final ChartRange range in values) {
      if (range.id == id) return range;
    }
    return null;
  }
}

/// What one chart draws: the bucket times and each series' averages there.
class RangeView {
  const RangeView(this.times, this.values);

  final List<DateTime> times;

  /// Series with data, in the order asked for.
  final Map<String, List<double>> values;

  bool get isEmpty => times.isEmpty;
}

class _OpenSum {
  double sum = 0;
  int count = 0;
}

/// One stored range: [ChartRange.buckets] closed buckets from [from], then
/// the open bucket from [to], which live frames fill. When the open bucket
/// closes, the oldest bucket leaves, so the window keeps its length.
class RangeHistory {
  RangeHistory._(this.range, this._stepMs, this._fromMs, this._closed);

  factory RangeHistory.fromJson(Map<String, dynamic> data) {
    final ChartRange range =
        ChartRange.byId(data['range'] as String) ??
        (throw FormatException('Unknown range ${data['range']}'));
    final int stepMs = (data['stepMs'] as num).toInt();
    final int toMs = (data['toMs'] as num).toInt();
    final Map<String, List<double?>> closed = <String, List<double?>>{};
    for (final dynamic raw in data['series'] as List<dynamic>) {
      final Map<String, dynamic> series = raw as Map<String, dynamic>;
      final List<double?> values = <double?>[
        for (final dynamic value in series['values'] as List<dynamic>)
          (value as num?)?.toDouble(),
      ];
      // The window ends at toMs: the newest buckets, empty ones before.
      closed[series['id'] as String] = values.length >= ChartRange.buckets
          ? values.sublist(values.length - ChartRange.buckets)
          : <double?>[
              ...List<double?>.filled(ChartRange.buckets - values.length, null),
              ...values,
            ];
    }
    return RangeHistory._(
      range,
      stepMs,
      toMs - ChartRange.buckets * stepMs,
      closed,
    );
  }

  final ChartRange range;
  final int _stepMs;
  int _fromMs;
  final Map<String, List<double?>> _closed;
  final Map<String, _OpenSum> _open = <String, _OpenSum>{};
  bool _gapped = false;
  final Map<String, RangeView> _views = <String, RangeView>{};

  /// The start of the oldest closed bucket.
  DateTime get from => DateTime.fromMillisecondsSinceEpoch(_fromMs);

  /// The start of the open bucket.
  DateTime get to => DateTime.fromMillisecondsSinceEpoch(_openMs);

  int get _openMs => _fromMs + ChartRange.buckets * _stepMs;

  /// True once live frames skipped two buckets or more: the server holds
  /// what the page missed.
  bool get gapped => _gapped;

  /// Adds one live frame taken [at]. A frame from before the open bucket is
  /// already in the server's range and is left out.
  void add(DateTime at, Map<String, double> values) {
    final int atMs = at.millisecondsSinceEpoch;
    if (atMs < _openMs) return;
    final int ahead = (atMs - _openMs) ~/ _stepMs;
    if (ahead > ChartRange.buckets) {
      _restart(_openMs + ahead * _stepMs);
    } else {
      for (int index = 0; index < ahead; index++) {
        _close();
      }
      if (ahead >= 3) _gapped = true;
    }
    for (final MapEntry<String, double> entry in values.entries) {
      if (!entry.value.isFinite) continue;
      final _OpenSum open = _open.putIfAbsent(entry.key, _OpenSum.new);
      open.sum += entry.value;
      open.count++;
    }
    _changed();
  }

  void _close() {
    final Set<String> ids = <String>{..._closed.keys, ..._open.keys};
    for (final String id in ids) {
      final List<double?> closed = _closed.putIfAbsent(
        id,
        () => List<double?>.filled(ChartRange.buckets, null, growable: true),
      );
      final _OpenSum? open = _open[id];
      closed
        ..removeAt(0)
        ..add(open == null || open.count == 0 ? null : open.sum / open.count);
    }
    _open.clear();
    _fromMs += _stepMs;
  }

  void _restart(int openMs) {
    for (final List<double?> closed in _closed.values) {
      closed.fillRange(0, closed.length, null);
    }
    _open.clear();
    _fromMs = openMs - ChartRange.buckets * _stepMs;
    _gapped = true;
  }

  void _changed() {
    _views.clear();
  }

  /// The buckets of [ids] for one chart. Series without any data are left
  /// out; a bucket shows only when every remaining series has a value there
  /// (they are captured together, so they nearly always do).
  RangeView view(List<String> ids) => _views[ids.join(',')] ??= _buildView(ids);

  RangeView _buildView(List<String> ids) {
    final List<String> shown = <String>[
      for (final String id in ids)
        if ((_closed[id]?.any((double? value) => value != null) ?? false) ||
            (_open[id]?.count ?? 0) > 0)
          id,
    ];
    final List<DateTime> times = <DateTime>[];
    final Map<String, List<double>> values = <String, List<double>>{
      for (final String id in shown) id: <double>[],
    };
    if (shown.isEmpty) return RangeView(times, values);
    for (int index = 0; index < ChartRange.buckets; index++) {
      if (shown.any((String id) => _closed[id]?[index] == null)) continue;
      times.add(DateTime.fromMillisecondsSinceEpoch(_fromMs + index * _stepMs));
      for (final String id in shown) {
        values[id]!.add(_closed[id]![index]!);
      }
    }
    if (shown.every((String id) => (_open[id]?.count ?? 0) > 0)) {
      times.add(DateTime.fromMillisecondsSinceEpoch(_openMs));
      for (final String id in shown) {
        final _OpenSum open = _open[id]!;
        values[id]!.add(open.sum / open.count);
      }
    }
    return RangeView(times, values);
  }
}
