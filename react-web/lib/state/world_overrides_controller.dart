import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';

class WorldOverridesController {
  final IControlClient _client;
  void Function()? onChange;
  void Function(Object error)? onError;

  List<WorldSettings> _worlds = const <WorldSettings>[];
  bool _loading = false;
  Object? _error;

  WorldOverridesController(this._client, {this.onChange, this.onError});

  List<WorldSettings> get worlds => _worlds;
  bool get loading => _loading;
  Object? get error => _error;

  void _notify() => onChange?.call();

  Future<void> load() async {
    _loading = true;
    _notify();
    try {
      _worlds = await _client.worlds();
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  Future<void> setBudget(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    final int idx = _worlds.indexWhere((WorldSettings w) => w.name == name);
    if (idx < 0) return;
    final WorldSettings previous = _worlds[idx];
    final List<WorldSettings> optimistic = List<WorldSettings>.of(_worlds);
    optimistic[idx] = previous.copyWith(
      budgetMs: budgetMs,
      panicMs: panicMs,
      releaseMs: releaseMs,
    );
    _worlds = List<WorldSettings>.unmodifiable(optimistic);
    _notify();
    try {
      final WorldSettings result = await _client.setWorld(
        name,
        budgetMs: budgetMs,
        panicMs: panicMs,
        releaseMs: releaseMs,
      );
      final List<WorldSettings> updated = List<WorldSettings>.of(_worlds);
      final int i = updated.indexWhere((WorldSettings w) => w.name == name);
      if (i >= 0) {
        updated[i] = result;
        _worlds = List<WorldSettings>.unmodifiable(updated);
      }
    } catch (e) {
      final List<WorldSettings> rolled = List<WorldSettings>.of(_worlds);
      final int i = rolled.indexWhere((WorldSettings w) => w.name == name);
      if (i >= 0) {
        rolled[i] = previous;
        _worlds = List<WorldSettings>.unmodifiable(rolled);
      }
      onError?.call(e);
    } finally {
      _notify();
    }
  }
}
