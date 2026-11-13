import 'dart:math' as math;

import '../state/fleet_manager.dart';

/// Per-credential `X-React-Counter` source. Values come from the wall clock
/// unless a stored or server-reported counter is ahead, so a client seeded
/// earlier never stays below the server's high-water mark for good.
class MonotonicCounter {
  final FleetStorage _storage;
  final int Function() _clock;

  MonotonicCounter(this._storage, {int Function()? clock})
    : _clock = clock ?? (() => DateTime.now().millisecondsSinceEpoch);

  static String _key(String credentialId) => 'reactor.counter.$credentialId';

  int? _stored(String credentialId) {
    final String? raw = _storage.read(_key(credentialId));
    return raw == null ? null : int.tryParse(raw);
  }

  int next(String credentialId) {
    final int? stored = _stored(credentialId);
    final int now = _clock();
    final int value = stored == null ? now : math.max(stored + 1, now);
    _storage.write(_key(credentialId), value.toString());
    return value;
  }

  /// Moves the counter past [lastCounter], the value the server reported in a
  /// 409. The next [next] returns at least `lastCounter + 1`.
  void rebase(String credentialId, int lastCounter) {
    final int? stored = _stored(credentialId);
    final int floor = stored == null
        ? lastCounter
        : math.max(stored, lastCounter);
    _storage.write(_key(credentialId), floor.toString());
  }
}
