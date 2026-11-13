import 'dart:async';

import '../model/config_tree.dart';
import '../model/knob.dart';
import '../model/server_revisions.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import 'live_controller.dart';

/// The react.toml tree of one server with an explicit apply model: [edit]
/// stages, [apply] sends. A server-side change under a staged path is flagged
/// by [changedOnServer], never overwritten.
class ConfigController extends LiveController {
  final IConfigClient _client;

  ConfigTree _tree = const ConfigTree();
  Map<String, Object?> _pending = <String, Object?>{};
  final Map<String, Object?> _base = <String, Object?>{};
  final Set<String> _changedOnServer = <String>{};
  bool _saving = false;

  ConfigController(
    IConfigClient client, {
    super.serverId,
    super.revisions,
    super.refetchDelay,
  }) : _client = client,
       super(kind: RevisionKind.config);

  ConfigTree get tree => _tree;
  Map<String, Object?> get pending =>
      Map<String, Object?>.unmodifiable(_pending);
  bool get saving => _saving;
  bool get dirty => _pending.isNotEmpty;
  bool get hasPendingEdits => _pending.isNotEmpty;

  bool changedOnServer(String path) => _changedOnServer.contains(path);

  Object? _serverValue(String path) {
    final Knob? node = _tree.node(path);
    return node?.value;
  }

  @override
  Future<void> fetch() async {
    final ConfigTree fresh = await _client.config();
    if (disposed) return;
    _tree = fresh;
    for (final String path in _pending.keys) {
      if (_serverValue(path) != _base[path]) _changedOnServer.add(path);
    }
  }

  void edit(String path, Object? value) {
    if (!_base.containsKey(path)) _base[path] = _serverValue(path);
    _pending[path] = value;
    notify();
  }

  void discard() {
    _pending = <String, Object?>{};
    _base.clear();
    _changedOnServer.clear();
    notify();
  }

  void _clearPending() {
    _pending = <String, Object?>{};
    _base.clear();
    _changedOnServer.clear();
  }

  Future<Object?> apply() async {
    if (_pending.isEmpty) return null;
    return _save(() => _client.applyConfig(Map<String, Object?>.of(_pending)));
  }

  /// Applies a preset. With unsaved edits it refuses ([ReactPendingEdits])
  /// unless [discardPending] is set, so edits are never dropped silently.
  Future<Object?> applyPreset(String name, {bool discardPending = false}) {
    if (_pending.isNotEmpty && !discardPending) {
      final ReactPendingEdits refusal = ReactPendingEdits(_pending.length);
      setError(refusal);
      notify();
      return Future<Object?>.value(refusal);
    }
    return _save(() => _client.applyPreset(name));
  }

  Future<Object?> _save(Future<ConfigTree> Function() send) async {
    _saving = true;
    setError(null);
    notify();
    try {
      ensureOpen();
      _tree = await send();
      _clearPending();
      return null;
    } on Object catch (e) {
      setError(e);
      return e;
    } finally {
      _saving = false;
      notify();
    }
  }
}
