import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/owner_cost.dart';
import '../service/react_client.dart';

/// `GET /owners/cost`, asked again every [interval] while something
/// listens.
class OwnerCostController extends ChangeNotifier {
  OwnerCostController(
    this._client, {
    this.interval = const Duration(seconds: 5),
  });

  final IOwnerClient _client;
  final Duration interval;

  OwnerCostBreakdown? _data;
  Object? _error;
  int _listeners = 0;
  bool _disposed = false;
  Timer? _timer;
  Future<void>? _inFlight;

  OwnerCostBreakdown? get data => _data;

  /// The last failed load; cleared by the next good one.
  Object? get error => _error;

  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    if (++_listeners == 1) {
      unawaited(load());
      _timer = Timer.periodic(interval, (Timer _) => unawaited(load()));
    }
  }

  @override
  void removeListener(void Function() listener) {
    super.removeListener(listener);
    if (_listeners > 0 && --_listeners == 0) {
      _timer?.cancel();
      _timer = null;
    }
  }

  Future<void> load() =>
      _inFlight ??= _load().whenComplete(() => _inFlight = null);

  Future<void> _load() async {
    try {
      final OwnerCostBreakdown data = await _client.ownerCostBreakdown();
      if (_disposed) return;
      _data = data;
      _error = null;
    } on Object catch (error) {
      if (_disposed) return;
      _error = error;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _timer = null;
    super.dispose();
  }
}
