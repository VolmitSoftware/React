import 'dart:async';

import '../model/server_snapshot.dart';
import 'connection_manager.dart';
import 'fleet_rollup.dart';

class FleetLiveSource {
  final String id;
  final String name;
  final ConnState initialState;
  final Stream<ServerSnapshot> snapshots;
  final Stream<ConnState> stateChanges;

  const FleetLiveSource({
    required this.id,
    required this.name,
    required this.initialState,
    required this.snapshots,
    required this.stateChanges,
  });
}

class _ServerEntry {
  ConnState state;
  ServerSnapshot? snapshot;
  DateTime? lastSeen;
  final String id;
  String name;
  late FleetLiveSource source;
  FleetServerLive? cached;
  StreamSubscription<ServerSnapshot>? snapshotSub;
  StreamSubscription<ConnState>? stateSub;

  _ServerEntry({required this.id, required this.name, required this.state});
}

class FleetLiveModel {
  final void Function()? onChange;

  final List<String> _order = <String>[];
  final Map<String, _ServerEntry> _entries = <String, _ServerEntry>{};
  bool _disposed = false;
  List<FleetServerLive>? _cachedServers;
  final StreamController<String?> _changes =
      StreamController<String?>.broadcast(sync: true);

  Stream<String?> get changes => _changes.stream;

  FleetServerLive? server(String id) {
    final _ServerEntry? entry = _entries[id];
    if (entry == null) return null;
    return entry.cached ??= FleetServerLive(
      id: entry.id,
      name: entry.name,
      state: entry.state,
      snapshot: entry.snapshot,
      lastSeen: entry.lastSeen,
    );
  }

  void _changed(String? id) {
    _cachedServers = null;
    if (id != null) _entries[id]?.cached = null;
    if (!_disposed) {
      _changes.add(id);
      onChange?.call();
    }
  }

  FleetLiveModel(List<FleetLiveSource> sources, {this.onChange}) {
    _subscribe(sources);
  }

  List<FleetServerLive> get servers =>
      _cachedServers ??= List<FleetServerLive>.unmodifiable(<FleetServerLive>[
        for (final String id in _order)
          if (_entries.containsKey(id)) server(id)!,
      ]);

  void update(List<FleetLiveSource> sources) {
    if (_disposed) return;
    final Set<String> newIds = sources.map((FleetLiveSource s) => s.id).toSet();
    bool changed = false;

    final List<String> toRemove = _order
        .where((String id) => !newIds.contains(id))
        .toList();
    if (toRemove.isNotEmpty) {
      changed = true;
      for (final String id in toRemove) {
        final _ServerEntry? entry = _entries[id];
        if (entry != null) {
          entry.snapshotSub?.cancel();
          entry.stateSub?.cancel();
          _entries.remove(id);
        }
      }
      _order.removeWhere((String id) => !newIds.contains(id));
    }

    for (final FleetLiveSource source in sources) {
      final _ServerEntry? existing = _entries[source.id];
      if (existing != null) {
        if (existing.name != source.name) {
          existing.name = source.name;
          existing.cached = null;
          changed = true;
        }
        if (existing.source.snapshots != source.snapshots ||
            existing.source.stateChanges != source.stateChanges) {
          existing.snapshotSub?.cancel();
          existing.stateSub?.cancel();
          existing.state = source.initialState;
          existing.snapshot = null;
          existing.lastSeen = null;
          existing.cached = null;
          _attachSource(source, existing);
          changed = true;
        }
      }
      if (existing == null) {
        changed = true;
        final _ServerEntry entry = _ServerEntry(
          id: source.id,
          name: source.name,
          state: source.initialState,
        );
        _entries[source.id] = entry;
        _order.add(source.id);
        _attachSource(source, entry);
      }
    }

    if (!_disposed && changed) {
      _changed(null);
    }
  }

  void dispose() {
    _disposed = true;
    for (final _ServerEntry entry in _entries.values) {
      entry.snapshotSub?.cancel();
      entry.stateSub?.cancel();
    }
    _entries.clear();
    _order.clear();
    _cachedServers = null;
    _changes.close();
  }

  void _subscribe(List<FleetLiveSource> sources) {
    for (final FleetLiveSource source in sources) {
      final _ServerEntry entry = _ServerEntry(
        id: source.id,
        name: source.name,
        state: source.initialState,
      );
      _entries[source.id] = entry;
      _order.add(source.id);
      _attachSource(source, entry);
    }
  }

  void _attachSource(FleetLiveSource source, _ServerEntry entry) {
    entry.source = source;
    entry.snapshotSub = source.snapshots.listen((ServerSnapshot snap) {
      if (_disposed ||
          !identical(_entries[source.id], entry) ||
          !identical(entry.source, source)) {
        return;
      }
      entry.snapshot = snap;
      entry.lastSeen = DateTime.now();
      _changed(source.id);
    });

    entry.stateSub = source.stateChanges.listen((ConnState state) {
      if (_disposed ||
          !identical(_entries[source.id], entry) ||
          !identical(entry.source, source)) {
        return;
      }
      entry.state = state;
      _changed(source.id);
    });
  }
}
