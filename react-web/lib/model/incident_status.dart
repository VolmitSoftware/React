class IncidentContributor {
  final String id;
  final String label;
  final bool available;
  final double weight;
  final double value;
  final String display;
  final double pressure;
  final double scorePoints;
  final double minimum;
  final double maximum;

  const IncidentContributor({
    required this.id,
    required this.label,
    required this.available,
    required this.weight,
    required this.value,
    required this.display,
    required this.pressure,
    required this.scorePoints,
    required this.minimum,
    required this.maximum,
  });

  factory IncidentContributor.fromJson(Map<String, dynamic> json) =>
      IncidentContributor(
        id: json['id'] as String? ?? '',
        label: json['label'] as String? ?? json['id'] as String? ?? '',
        available: json['available'] as bool? ?? false,
        weight: (json['weight'] as num?)?.toDouble() ?? 0,
        value: (json['value'] as num?)?.toDouble() ?? 0,
        display: json['display'] as String? ?? '',
        pressure: (json['pressure'] as num?)?.toDouble() ?? 0,
        scorePoints: (json['scorePoints'] as num?)?.toDouble() ?? 0,
        minimum: (json['minimum'] as num?)?.toDouble() ?? 0,
        maximum: (json['maximum'] as num?)?.toDouble() ?? 0,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'id': id,
    'label': label,
    'available': available,
    'weight': weight,
    'value': value,
    'display': display,
    'pressure': pressure,
    'scorePoints': scorePoints,
    'minimum': minimum,
    'maximum': maximum,
  };
}

class IncidentLocation {
  final String worldId;
  final String world;
  final int x;
  final int y;
  final int z;

  const IncidentLocation({
    required this.worldId,
    required this.world,
    required this.x,
    required this.y,
    required this.z,
  });

  factory IncidentLocation.fromJson(Map<String, dynamic> json) =>
      IncidentLocation(
        worldId: json['worldId'] as String? ?? '',
        world: json['world'] as String? ?? '',
        x: (json['x'] as num?)?.toInt() ?? 0,
        y: (json['y'] as num?)?.toInt() ?? 0,
        z: (json['z'] as num?)?.toInt() ?? 0,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'worldId': worldId,
    'world': world,
    'x': x,
    'y': y,
    'z': z,
  };
}

class IncidentAction {
  final String id;
  final String label;
  final String status;
  final String detail;
  final int occurredAtMs;

  const IncidentAction({
    required this.id,
    required this.label,
    required this.status,
    required this.detail,
    required this.occurredAtMs,
  });

  factory IncidentAction.fromJson(Map<String, dynamic> json) => IncidentAction(
    id: json['id'] as String? ?? '',
    label: json['label'] as String? ?? '',
    status: json['status'] as String? ?? '',
    detail: json['detail'] as String? ?? '',
    occurredAtMs: (json['occurredAtMs'] as num?)?.toInt() ?? 0,
  );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'id': id,
    'label': label,
    'status': status,
    'detail': detail,
    'occurredAtMs': occurredAtMs,
  };
}

class IncidentRecord {
  final String id;
  final String incidentId;
  final String kind;
  final String phase;
  final String severity;
  final int occurredAtMs;
  final int startedAtMs;
  final String source;
  final String title;
  final String summary;
  final String cause;
  final IncidentLocation? location;
  final List<IncidentContributor> evidence;
  final List<IncidentAction> actions;
  final Map<String, String> context;

  const IncidentRecord({
    required this.id,
    required this.incidentId,
    required this.kind,
    required this.phase,
    required this.severity,
    required this.occurredAtMs,
    required this.startedAtMs,
    required this.source,
    required this.title,
    required this.summary,
    required this.cause,
    required this.location,
    required this.evidence,
    required this.actions,
    required this.context,
  });

  factory IncidentRecord.fromJson(Map<String, dynamic> json) {
    final Object? rawLocation = json['location'];
    final Object? rawContext = json['context'];
    return IncidentRecord(
      id: json['id'] as String? ?? '',
      incidentId: json['incidentId'] as String? ?? '',
      kind: json['kind'] as String? ?? '',
      phase: json['phase'] as String? ?? '',
      severity: json['severity'] as String? ?? '',
      occurredAtMs: (json['occurredAtMs'] as num?)?.toInt() ?? 0,
      startedAtMs: (json['startedAtMs'] as num?)?.toInt() ?? 0,
      source: json['source'] as String? ?? '',
      title: json['title'] as String? ?? '',
      summary: json['summary'] as String? ?? '',
      cause: json['cause'] as String? ?? '',
      location: rawLocation is Map<String, dynamic>
          ? IncidentLocation.fromJson(rawLocation)
          : null,
      evidence: _objectList(
        json['evidence'],
      ).map(IncidentContributor.fromJson).toList(growable: false),
      actions: _objectList(
        json['actions'],
      ).map(IncidentAction.fromJson).toList(growable: false),
      context: rawContext is Map
          ? rawContext.map<String, String>(
              (dynamic key, dynamic value) =>
                  MapEntry<String, String>(key.toString(), value.toString()),
            )
          : const <String, String>{},
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'id': id,
    'incidentId': incidentId,
    'kind': kind,
    'phase': phase,
    'severity': severity,
    'occurredAtMs': occurredAtMs,
    'startedAtMs': startedAtMs,
    'source': source,
    'title': title,
    'summary': summary,
    'cause': cause,
    'location': location?.toJson(),
    'evidence': evidence
        .map((IncidentContributor item) => item.toJson())
        .toList(growable: false),
    'actions': actions
        .map((IncidentAction item) => item.toJson())
        .toList(growable: false),
    'context': context,
  };
}

class IncidentStatus {
  final double score;
  final bool scoreAvailable;
  final int sampledAtMs;
  final String state;
  final List<IncidentContributor> contributors;
  final List<IncidentRecord> incidents;

  const IncidentStatus({
    required this.score,
    required this.scoreAvailable,
    required this.sampledAtMs,
    required this.state,
    this.contributors = const <IncidentContributor>[],
    this.incidents = const <IncidentRecord>[],
  });

  factory IncidentStatus.fromJson(Map<String, dynamic> json) => IncidentStatus(
    score: (json['score'] as num?)?.toDouble() ?? 0,
    scoreAvailable: json['scoreAvailable'] as bool? ?? false,
    sampledAtMs: (json['sampledAtMs'] as num?)?.toInt() ?? 0,
    state: json['state'] as String? ?? 'UNKNOWN',
    contributors: _objectList(
      json['contributors'],
    ).map(IncidentContributor.fromJson).toList(growable: false),
    incidents: _objectList(
      json['incidents'],
    ).map(IncidentRecord.fromJson).toList(growable: false),
  );

  IncidentContributor? get primaryContributor {
    IncidentContributor? primary;
    for (final IncidentContributor contributor in contributors) {
      if (!contributor.available) continue;
      if (primary == null || contributor.scorePoints > primary.scorePoints) {
        primary = contributor;
      }
    }
    return primary;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'score': score,
    'scoreAvailable': scoreAvailable,
    'sampledAtMs': sampledAtMs,
    'state': state,
    'contributors': contributors
        .map((IncidentContributor item) => item.toJson())
        .toList(growable: false),
    'incidents': incidents
        .map((IncidentRecord item) => item.toJson())
        .toList(growable: false),
  };
}

List<Map<String, dynamic>> _objectList(Object? value) {
  if (value is! List) return const <Map<String, dynamic>>[];
  return value
      .whereType<Map>()
      .map(
        (Map<dynamic, dynamic> item) => item.map<String, dynamic>(
          (dynamic key, dynamic value) =>
              MapEntry<String, dynamic>(key.toString(), value),
        ),
      )
      .toList(growable: false);
}
