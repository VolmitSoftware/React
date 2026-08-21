import '../state/fleet_manager.dart';

class MonotonicCounter {
  final FleetStorage _storage;
  final int Function() _clock;

  MonotonicCounter(this._storage, {int Function()? clock})
    : _clock = clock ?? (() => DateTime.now().millisecondsSinceEpoch);

  int next(String credentialId) {
    final String key = 'reactor.counter.$credentialId';
    final String? stored = _storage.read(key);
    final int? parsed = stored != null ? int.tryParse(stored) : null;
    final int value = parsed == null ? _clock() : parsed + 1;
    _storage.write(key, value.toString());
    return value;
  }
}
