class IncidentContributor {
  final String name;
  final double weight;
  final double value;

  const IncidentContributor({
    required this.name,
    required this.weight,
    required this.value,
  });

  factory IncidentContributor.fromJson(Map<String, dynamic> j) =>
      IncidentContributor(
        name: j['name'] as String,
        weight: (j['weight'] as num).toDouble(),
        value: (j['value'] as num).toDouble(),
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'name': name,
    'weight': weight,
    'value': value,
  };
}

class IncidentStatus {
  final double score;
  final String state;
  final List<String> timeline;
  final List<IncidentContributor> contributors;

  const IncidentStatus({
    required this.score,
    required this.state,
    this.timeline = const <String>[],
    this.contributors = const <IncidentContributor>[],
  });

  factory IncidentStatus.fromJson(Map<String, dynamic> j) => IncidentStatus(
    score: (j['score'] as num).toDouble(),
    state: j['state'] as String,
    timeline:
        (j['timeline'] as List<dynamic>?)
            ?.map((dynamic e) => e as String)
            .toList() ??
        const <String>[],
    contributors:
        (j['contributors'] as List<dynamic>?)
            ?.map(
              (dynamic e) =>
                  IncidentContributor.fromJson(e as Map<String, dynamic>),
            )
            .toList() ??
        const <IncidentContributor>[],
  );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'score': score,
    'state': state,
    'timeline': timeline,
    'contributors': contributors
        .map((IncidentContributor c) => c.toJson())
        .toList(),
  };
}
