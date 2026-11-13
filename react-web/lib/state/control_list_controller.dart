import 'dart:async';

import '../model/control_item.dart';
import '../model/knob.dart';
import '../model/server_revisions.dart';
import '../service/react_client.dart';
import 'live_controller.dart';

/// A knob value typed by the user but not sent yet, with the server value it
/// was based on, so a server-side change underneath it can be flagged.
class StagedEdit {
  final Object? value;
  final Object? base;

  const StagedEdit(this.value, this.base);
}

/// Features or tweaks of one server. Toggles write at once; number and text
/// knobs are staged with [stageKnob] and sent by [commitKnobs] (on blur,
/// Enter or Save), never per keystroke.
class ControlListController extends LiveController {
  final IControlClient _client;
  final bool isTweaks;

  List<ControlItem> _server = const <ControlItem>[];
  List<ControlItem>? _view;
  final Map<String, Map<String, StagedEdit>> _staged =
      <String, Map<String, StagedEdit>>{};
  final Set<String> _changedOnServer = <String>{};
  final Map<String, int> _writesInFlight = <String, int>{};

  ControlListController(
    IControlClient client, {
    required this.isTweaks,
    super.serverId,
    super.revisions,
    super.refetchDelay,
  }) : _client = client,
       super(kind: isTweaks ? RevisionKind.tweaks : RevisionKind.features);

  /// Items as the user sees them: server values with staged edits on top.
  List<ControlItem> get items =>
      _view ??= List<ControlItem>.unmodifiable(_server.map(_withStaged));

  int get enabledCount => items.where((ControlItem i) => i.enabled).length;

  bool get hasPendingEdits => _staged.isNotEmpty;

  bool hasStaged(String id, [String? key]) {
    final Map<String, StagedEdit>? edits = _staged[id];
    if (edits == null) return false;
    return key == null || edits.containsKey(key);
  }

  /// True when the server changed a knob the user is still editing.
  bool changedOnServer(String id, String key) =>
      _changedOnServer.contains(_flag(id, key));

  static String _flag(String id, String key) => '$id\u0000$key';

  ControlItem _withStaged(ControlItem item) {
    final Map<String, StagedEdit>? edits = _staged[item.id];
    if (edits == null) return item;
    ControlItem result = item;
    for (final MapEntry<String, StagedEdit> edit in edits.entries) {
      result = result.withKnobValue(edit.key, edit.value.value);
    }
    return result;
  }

  void _setServer(List<ControlItem> next) {
    _server = List<ControlItem>.unmodifiable(next);
    _view = null;
  }

  @override
  Future<void> fetch() async {
    final List<ControlItem> fresh = isTweaks
        ? await _client.tweaks()
        : await _client.features();
    if (disposed) return;
    final Map<String, ControlItem> local = <String, ControlItem>{
      for (final ControlItem item in _server) item.id: item,
    };
    _setServer(<ControlItem>[
      for (final ControlItem item in fresh)
        (_writesInFlight[item.id] ?? 0) > 0 && local.containsKey(item.id)
            ? local[item.id]!
            : item,
    ]);
    _flagServerChanges();
  }

  void _flagServerChanges() {
    for (final MapEntry<String, Map<String, StagedEdit>> item
        in _staged.entries) {
      final ControlItem? current = _serverItem(item.key);
      for (final MapEntry<String, StagedEdit> edit in item.value.entries) {
        final Object? now = _knobValue(current, edit.key);
        if (now != edit.value.base) {
          _changedOnServer.add(_flag(item.key, edit.key));
        }
      }
    }
  }

  ControlItem? _serverItem(String id) {
    for (final ControlItem item in _server) {
      if (item.id == id) return item;
    }
    return null;
  }

  static Object? _knobValue(ControlItem? item, String key) {
    if (item == null) return null;
    for (final Knob knob in item.knobs) {
      if (knob.key == key) return knob.value;
    }
    return null;
  }

  void _replace(String id, ControlItem Function(ControlItem) change) {
    _setServer(<ControlItem>[
      for (final ControlItem item in _server)
        item.id == id ? change(item) : item,
    ]);
  }

  Future<T> _write<T>(String id, Future<T> Function() send) async {
    ensureOpen();
    _writesInFlight.update(id, (int n) => n + 1, ifAbsent: () => 1);
    try {
      return await send();
    } finally {
      final int left = (_writesInFlight[id] ?? 1) - 1;
      if (left <= 0) {
        _writesInFlight.remove(id);
      } else {
        _writesInFlight[id] = left;
      }
    }
  }

  /// Writes at once with an optimistic update; rolls back on failure. The
  /// returned future completes with the error, if any, after rollback.
  Future<Object?> toggle(String id, bool enabled) async {
    final ControlItem? previous = _serverItem(id);
    if (previous == null || disposed) return null;
    _replace(id, (ControlItem item) => item.copyWith(enabled: enabled));
    notify();
    try {
      final ControlItem result = await _write(
        id,
        () => isTweaks
            ? _client.setTweakEnabled(id, enabled)
            : _client.setFeatureEnabled(id, enabled),
      );
      _replace(id, (ControlItem _) => result);
      return null;
    } on Object catch (e) {
      _replace(
        id,
        (ControlItem item) => item.copyWith(enabled: previous.enabled),
      );
      return e;
    } finally {
      notify();
    }
  }

  /// Records a typed value without sending it.
  void stageKnob(String id, String key, Object? value) {
    final ControlItem? item = _serverItem(id);
    if (item == null || disposed) return;
    final Map<String, StagedEdit> edits = _staged.putIfAbsent(
      id,
      () => <String, StagedEdit>{},
    );
    final Object? base = edits[key]?.base ?? _knobValue(item, key);
    edits[key] = StagedEdit(value, base);
    _view = null;
    notify();
  }

  /// Sends every staged knob of [id] in one request.
  Future<Object?> commitKnobs(String id) async {
    final Map<String, StagedEdit>? edits = _staged.remove(id);
    if (edits == null || edits.isEmpty) return null;
    _changedOnServer.removeWhere((String flag) => flag.startsWith('$id\u0000'));
    _view = null;
    return _sendKnobs(id, <String, Object?>{
      for (final MapEntry<String, StagedEdit> edit in edits.entries)
        edit.key: edit.value.value,
    });
  }

  void discardKnobs(String id) {
    if (_staged.remove(id) == null) return;
    _changedOnServer.removeWhere((String flag) => flag.startsWith('$id\u0000'));
    _view = null;
    notify();
  }

  Future<Object?> _sendKnobs(String id, Map<String, Object?> knobs) async {
    final ControlItem? previous = _serverItem(id);
    if (previous == null || disposed) return null;
    _replace(id, (ControlItem item) {
      ControlItem next = item;
      for (final MapEntry<String, Object?> knob in knobs.entries) {
        next = next.withKnobValue(knob.key, knob.value);
      }
      return next;
    });
    notify();
    try {
      final ControlItem result = await _write(
        id,
        () => isTweaks
            ? _client.setTweakConfig(id, knobs)
            : _client.setFeatureConfig(id, knobs),
      );
      _replace(id, (ControlItem _) => result);
      return null;
    } on Object catch (e) {
      _replace(id, (ControlItem _) => previous);
      return e;
    } finally {
      notify();
    }
  }

  Future<void> setAll(bool enabled) async {
    final List<ControlItem> snapshot = List<ControlItem>.of(_server);
    for (final ControlItem item in snapshot) {
      if (item.enabled != enabled) {
        await toggle(item.id, enabled);
      }
    }
  }
}
