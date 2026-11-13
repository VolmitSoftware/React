import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/range_history.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import 'fleet_manager.dart';

/// The storage key the last picked range is kept under, per browser.
const String chartRangeStorageKey = 'reactor.chart_range';

/// The time range of one server's Overview and Performance charts, and the
/// stored ranges loaded for it. A range loads once, in one request for
/// every chart, then live frames extend it; Live needs no request. Owned
/// by the server's session, so a pick holds across pages; the last pick is
/// kept in the browser for the next visit.
class ChartHistory extends ChangeNotifier {
  ChartHistory(
    this._client,
    Stream<ServerSnapshot> snapshots, {
    this.ids = kChartSamplerIds,
    this._storage,
  }) {
    _frames = snapshots.listen(_onFrame);
    _range = _remembered();
    if (_range.stored) unawaited(_load(_range));
  }

  final FleetStorage? _storage;
  final IHistoryClient _client;

  /// The samplers asked for and extended.
  final List<String> ids;

  late final StreamSubscription<ServerSnapshot> _frames;
  final Map<ChartRange, RangeHistory> _loaded = <ChartRange, RangeHistory>{};
  final Map<ChartRange, Object> _errors = <ChartRange, Object>{};
  final Set<ChartRange> _inFlight = <ChartRange>{};
  ChartRange _range = ChartRange.live;
  bool _disposed = false;

  ChartRange get range => _range;

  /// The picked stored range, once loaded (null for Live).
  RangeHistory? get current => _range.stored ? _loaded[_range] : null;

  /// The picked range is on its way and nothing is there to show yet.
  bool get loading =>
      _range.stored && _loaded[_range] == null && _errors[_range] == null;

  /// Why the picked range could not load.
  Object? get error => _range.stored ? _errors[_range] : null;

  void select(ChartRange range) {
    if (_disposed || range == _range) return;
    _range = range;
    _remember(range);
    // Picking a range again after an error asks again.
    if (range.stored && _loaded[range] == null) {
      _errors.remove(range);
      unawaited(_load(range));
    }
    notifyListeners();
  }

  /// Asks again for the picked range after an error.
  void retry() {
    if (_disposed || !_range.stored) return;
    _errors.remove(_range);
    notifyListeners();
    unawaited(_load(_range));
  }

  Future<void> _load(ChartRange range) async {
    if (!_inFlight.add(range)) return;
    try {
      final RangeHistory loaded = await _client.metricRange(range, ids: ids);
      if (_disposed) return;
      _loaded[range] = loaded;
      _errors.remove(range);
    } on Object catch (failure) {
      if (_disposed) return;
      // A failed refresh keeps what is on screen.
      if (_loaded[range] == null) _errors[range] = failure;
    } finally {
      _inFlight.remove(range);
    }
    if (range == _range) notifyListeners();
  }

  void _onFrame(ServerSnapshot snapshot) {
    if (_disposed || _loaded.isEmpty) return;
    final Map<String, double> values = <String, double>{
      for (final String id in ids)
        if (snapshot.sampler(id) case final SamplerSample sample)
          id: sample.value,
    };
    for (final ChartRange range in _loaded.keys.toList()) {
      final RangeHistory history = _loaded[range]!;
      history.add(snapshot.at, values);
      if (!history.gapped) continue;
      // The server kept what the page missed: shown ranges ask again,
      // hidden ones are dropped and asked for when shown.
      if (range == _range) {
        unawaited(_load(range));
      } else {
        _loaded.remove(range);
      }
    }
    if (_range.stored && _loaded[_range] != null) notifyListeners();
  }

  ChartRange _remembered() {
    try {
      final String? stored = _storage?.read(chartRangeStorageKey);
      return stored == null
          ? ChartRange.live
          : ChartRange.byId(stored) ?? ChartRange.live;
    } on Object {
      return ChartRange.live;
    }
  }

  void _remember(ChartRange range) {
    try {
      _storage?.write(chartRangeStorageKey, range.id);
    } on Object {
      // Private mode or storage switched off: the pick lasts this page.
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    unawaited(_frames.cancel());
    super.dispose();
  }
}
