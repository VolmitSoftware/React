/// The kinds of server state the plugin versions with a revision counter.
enum RevisionKind { features, tweaks, config, worlds, packs, actions }

/// Per-kind revision counters carried by every metrics frame and response.
/// Absent on old plugins, in which case nothing is refetched on change.
class ServerRevisions {
  final Map<RevisionKind, int> values;

  const ServerRevisions(this.values);

  /// Reads `revisions` from a metrics envelope: inside `data` or next to it.
  static ServerRevisions? fromEnvelope(Map<String, dynamic> json) {
    final Object? data = json['data'];
    if (data is Map<String, dynamic>) {
      final ServerRevisions? inner = fromJson(data['revisions']);
      if (inner != null) return inner;
    }
    return fromJson(json['revisions']);
  }

  static ServerRevisions? fromJson(Object? raw) {
    if (raw is! Map<String, dynamic>) return null;
    final Map<RevisionKind, int> values = <RevisionKind, int>{};
    for (final RevisionKind kind in RevisionKind.values) {
      final Object? value = raw[kind.name];
      if (value is num) values[kind] = value.toInt();
    }
    return values.isEmpty ? null : ServerRevisions(values);
  }

  int? operator [](RevisionKind kind) => values[kind];

  static bool same(ServerRevisions? a, ServerRevisions? b) {
    if (identical(a, b)) return true;
    if (a == null || b == null) return false;
    if (a.values.length != b.values.length) return false;
    for (final MapEntry<RevisionKind, int> entry in a.values.entries) {
      if (b.values[entry.key] != entry.value) return false;
    }
    return true;
  }

  /// Kinds whose counter differs from [previous]. A kind missing on either side
  /// is not reported, so an old plugin never triggers a refetch.
  Set<RevisionKind> changedSince(ServerRevisions? previous) {
    if (previous == null) return const <RevisionKind>{};
    final Set<RevisionKind> changed = <RevisionKind>{};
    for (final MapEntry<RevisionKind, int> entry in values.entries) {
      final int? before = previous.values[entry.key];
      if (before != null && before != entry.value) changed.add(entry.key);
    }
    return changed;
  }
}
