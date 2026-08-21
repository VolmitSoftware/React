import 'package:react_web/model/control_item.dart';
import 'package:react_web/service/react_client.dart';

class ControlListController {
  final IControlClient _client;
  final bool isTweaks;
  void Function()? onChange;
  void Function(Object error)? onError;

  List<ControlItem> _items = const <ControlItem>[];
  bool _loading = false;
  Object? _error;

  ControlListController(
    this._client, {
    required this.isTweaks,
    this.onChange,
    this.onError,
  });

  List<ControlItem> get items => _items;
  bool get loading => _loading;
  Object? get error => _error;
  int get enabledCount => _items.where((ControlItem i) => i.enabled).length;

  void _notify() => onChange?.call();

  Future<void> load() async {
    _loading = true;
    _notify();
    try {
      _items = isTweaks ? await _client.tweaks() : await _client.features();
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  Future<void> toggle(String id, bool enabled) async {
    final int idx = _items.indexWhere((ControlItem i) => i.id == id);
    if (idx < 0) return;
    final ControlItem previous = _items[idx];
    final List<ControlItem> optimistic = List<ControlItem>.of(_items);
    optimistic[idx] = previous.copyWith(enabled: enabled);
    _items = List<ControlItem>.unmodifiable(optimistic);
    _notify();
    try {
      final ControlItem result = isTweaks
          ? await _client.setTweakEnabled(id, enabled)
          : await _client.setFeatureEnabled(id, enabled);
      final List<ControlItem> updated = List<ControlItem>.of(_items);
      final int i = updated.indexWhere((ControlItem item) => item.id == id);
      if (i >= 0) {
        updated[i] = result;
        _items = List<ControlItem>.unmodifiable(updated);
      }
    } catch (e) {
      final List<ControlItem> rolled = List<ControlItem>.of(_items);
      final int i = rolled.indexWhere((ControlItem item) => item.id == id);
      if (i >= 0) {
        rolled[i] = previous;
        _items = List<ControlItem>.unmodifiable(rolled);
      }
      onError?.call(e);
    } finally {
      _notify();
    }
  }

  Future<void> setKnob(String id, String key, Object? value) async {
    final int idx = _items.indexWhere((ControlItem i) => i.id == id);
    if (idx < 0) return;
    final ControlItem previous = _items[idx];
    final List<ControlItem> optimistic = List<ControlItem>.of(_items);
    optimistic[idx] = previous.withKnobValue(key, value);
    _items = List<ControlItem>.unmodifiable(optimistic);
    _notify();
    try {
      final ControlItem result = isTweaks
          ? await _client.setTweakConfig(id, <String, Object?>{key: value})
          : await _client.setFeatureConfig(id, <String, Object?>{key: value});
      final List<ControlItem> updated = List<ControlItem>.of(_items);
      final int i = updated.indexWhere((ControlItem item) => item.id == id);
      if (i >= 0) {
        updated[i] = result;
        _items = List<ControlItem>.unmodifiable(updated);
      }
    } catch (e) {
      final List<ControlItem> rolled = List<ControlItem>.of(_items);
      final int i = rolled.indexWhere((ControlItem item) => item.id == id);
      if (i >= 0) {
        rolled[i] = previous;
        _items = List<ControlItem>.unmodifiable(rolled);
      }
      onError?.call(e);
    } finally {
      _notify();
    }
  }

  Future<void> setAll(bool enabled) async {
    final List<ControlItem> snapshot = List<ControlItem>.of(_items);
    for (final ControlItem item in snapshot) {
      if (item.enabled != enabled) {
        await toggle(item.id, enabled);
      }
    }
  }
}
