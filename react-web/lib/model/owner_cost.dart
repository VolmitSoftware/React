/// `GET /owners/cost`: what each plugin or mod costs on the server thread,
/// split by kind of work (events, tasks, entities, block entities, world
/// generation, chunk loaders, network).
library;

double _double(Object? value) => value is num ? value.toDouble() : 0;

String _string(Object? value) => value is String ? value : '';

/// One kind of work over every owner.
class OwnerCostKind {
  const OwnerCostKind({
    required this.kind,
    required this.label,
    required this.msPerSecond,
    required this.share,
  });

  final String kind;

  /// The server's name for it, in the server's language.
  final String label;
  final double msPerSecond;

  /// Its part of the total, 0 to 1.
  final double share;

  factory OwnerCostKind.fromJson(Map<String, dynamic> json) {
    final String kind = _string(json['kind']);
    final String label = _string(json['label']);
    return OwnerCostKind(
      kind: kind,
      label: label.isEmpty ? kind : label,
      msPerSecond: _double(json['msPerSecond']),
      share: _double(json['share']),
    );
  }
}

/// One owner's cost and its split by kind (kind id to ms/s).
class OwnerCostEntry {
  const OwnerCostEntry({
    required this.id,
    required this.name,
    required this.msPerSecond,
    required this.share,
    this.kinds = const <String, double>{},
  });

  final String id;
  final String name;
  final double msPerSecond;

  /// Its part of the total, 0 to 1.
  final double share;
  final Map<String, double> kinds;

  factory OwnerCostEntry.fromJson(Map<String, dynamic> json) {
    final String id = _string(json['id']);
    final String name = _string(json['name']);
    final Object? kinds = json['kinds'];
    return OwnerCostEntry(
      id: id,
      name: name.isEmpty ? id : name,
      msPerSecond: _double(json['msPerSecond']),
      share: _double(json['share']),
      kinds: <String, double>{
        if (kinds is Map<String, dynamic>)
          for (final MapEntry<String, dynamic> entry in kinds.entries)
            if (entry.value is num && (entry.value as num) > 0)
              entry.key: (entry.value as num).toDouble(),
      },
    );
  }
}

class OwnerCostBreakdown {
  const OwnerCostBreakdown({
    required this.totalMsPerSecond,
    required this.kinds,
    required this.owners,
    this.measuring = true,
  });

  static const OwnerCostBreakdown empty = OwnerCostBreakdown(
    totalMsPerSecond: 0,
    kinds: <OwnerCostKind>[],
    owners: <OwnerCostEntry>[],
  );

  /// Whether the last window is fresh.
  final bool measuring;
  final double totalMsPerSecond;
  final List<OwnerCostKind> kinds;

  /// Costliest first.
  final List<OwnerCostEntry> owners;

  factory OwnerCostBreakdown.fromJson(Map<String, dynamic> json) {
    List<Map<String, dynamic>> objects(Object? value) => <Map<String, dynamic>>[
      if (value is List<dynamic>)
        for (final Object? entry in value)
          if (entry is Map<String, dynamic>) entry,
    ];
    return OwnerCostBreakdown(
      measuring: json['measuring'] != false,
      totalMsPerSecond: _double(json['totalMsPerSecond']),
      kinds: <OwnerCostKind>[
        for (final Map<String, dynamic> entry in objects(json['kinds']))
          OwnerCostKind.fromJson(entry),
      ],
      owners:
          <OwnerCostEntry>[
            for (final Map<String, dynamic> entry in objects(json['owners']))
              OwnerCostEntry.fromJson(entry),
          ]..sort(
            (OwnerCostEntry a, OwnerCostEntry b) =>
                b.msPerSecond.compareTo(a.msPerSecond),
          ),
    );
  }

  OwnerCostEntry? byId(String id) {
    for (final OwnerCostEntry owner in owners) {
      if (owner.id == id) return owner;
    }
    return null;
  }

  /// The label of kind [kind], its id when the server sent none.
  String labelOf(String kind) {
    for (final OwnerCostKind each in kinds) {
      if (each.kind == kind) return each.label;
    }
    return kind;
  }
}
