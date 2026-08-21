import '../model/config_tree.dart';
import '../service/react_client.dart';

class ConfigController {
  final IConfigClient _client;
  void Function()? onChange;
  void Function(Object error)? onError;

  ConfigTree _tree = const ConfigTree();
  Map<String, Object?> _pending = <String, Object?>{};
  bool _loading = false;
  bool _saving = false;
  Object? _error;

  ConfigController(this._client, {this.onChange, this.onError});

  ConfigTree get tree => _tree;
  Map<String, Object?> get pending =>
      Map<String, Object?>.unmodifiable(_pending);
  bool get loading => _loading;
  bool get saving => _saving;
  Object? get error => _error;
  bool get dirty => _pending.isNotEmpty;

  void _notify() => onChange?.call();

  Future<void> load() async {
    _loading = true;
    _notify();
    try {
      _tree = await _client.config();
      _pending = <String, Object?>{};
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  void edit(String path, Object? value) {
    _pending[path] = value;
    _notify();
  }

  Future<void> apply() async {
    if (_pending.isEmpty) return;
    _saving = true;
    _notify();
    try {
      final ConfigTree result = await _client.applyConfig(
        Map<String, Object?>.of(_pending),
      );
      _tree = result;
      _pending = <String, Object?>{};
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _saving = false;
      _notify();
    }
  }

  Future<void> applyPreset(String name) async {
    _saving = true;
    _notify();
    try {
      final ConfigTree result = await _client.applyPreset(name);
      _tree = result;
      _pending = <String, Object?>{};
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _saving = false;
      _notify();
    }
  }
}
