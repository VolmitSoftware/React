import 'dart:async';

import '../model/server_revisions.dart';
import '../model/server_snapshot.dart';

/// The latest per-kind revision counters of one server, read from its
/// metrics snapshots. Emits a kind once each time its counter moves.
class RevisionFeed {
  final StreamController<RevisionKind> _changes =
      StreamController<RevisionKind>.broadcast(sync: true);
  StreamSubscription<ServerSnapshot>? _sub;
  ServerRevisions? _current;

  RevisionFeed(Stream<ServerSnapshot> snapshots, {ServerRevisions? initial})
    : _current = initial {
    _sub = snapshots.listen((ServerSnapshot snapshot) {
      final ServerRevisions? next = snapshot.revisions;
      if (next != null) update(next);
    });
  }

  /// A feed that never changes, for controllers built without a server.
  RevisionFeed.none() : _current = null;

  ServerRevisions? get current => _current;

  int? operator [](RevisionKind kind) => _current?[kind];

  Stream<RevisionKind> get changes => _changes.stream;

  void update(ServerRevisions next) {
    final ServerRevisions? previous = _current;
    _current = next;
    if (_changes.isClosed) return;
    for (final RevisionKind kind in next.changedSince(previous)) {
      _changes.add(kind);
    }
  }

  void dispose() {
    _sub?.cancel();
    _sub = null;
    _changes.close();
  }
}
