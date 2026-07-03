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
  final String name;
  StreamSubscription<ServerSnapshot>? snapshotSub;
  StreamSubscription<ConnState>? stateSub;

  _ServerEntry({
    required this.id,
    required this.name,
    required this.state,
  });
}

class FleetLiveModel {
  final void Function()? onChange;

  final List<String> _order = <String>[];
  final Map<String, _ServerEntry> _entries = <String, _ServerEntry>{};
  bool _disposed = false;

  FleetLiveModel(List<FleetLiveSource> sources, {this.onChange}) {
    _subscribe(sources);
  }

  List<FleetServerLive> get servers => _order
      .where((String id) => _entries.containsKey(id))
      .map((String id) {
        final _ServerEntry e = _entries[id]!;
        return FleetServerLive(
          id: e.id,
          name: e.name,
          state: e.state,
          snapshot: e.snapshot,
          lastSeen: e.lastSeen,
        );
      })
      .toList();

  void update(List<FleetLiveSource> sources) {
    final Set<String> newIds = sources.map((FleetLiveSource s) => s.id).toSet();
    bool changed = false;

    final List<String> toRemove =
        _order.where((String id) => !newIds.contains(id)).toList();
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
      if (!_entries.containsKey(source.id)) {
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
      onChange?.call();
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
    entry.snapshotSub = source.snapshots.listen((ServerSnapshot snap) {
      if (_disposed) return;
      entry.snapshot = snap;
      entry.lastSeen = DateTime.now();
      onChange?.call();
    });

    entry.stateSub = source.stateChanges.listen((ConnState state) {
      if (_disposed) return;
      entry.state = state;
      onChange?.call();
    });
  }
}
