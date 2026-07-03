import 'dart:collection';

class EnvironmentInfo {
  final Map<String, Map<String, Object?>> sections;

  const EnvironmentInfo({required this.sections});

  factory EnvironmentInfo.fromJson(Map<String, dynamic> j) {
    final LinkedHashMap<String, Map<String, Object?>> result =
        LinkedHashMap<String, Map<String, Object?>>();
    for (final MapEntry<String, dynamic> entry in j.entries) {
      if (entry.value is Map) {
        result[entry.key] =
            Map<String, Object?>.from(entry.value as Map<dynamic, dynamic>);
      }
    }
    return EnvironmentInfo(sections: result);
  }

  Map<String, Object?> get cpu => sections['cpu'] ?? const <String, Object?>{};
  Map<String, Object?> get memory =>
      sections['memory'] ?? const <String, Object?>{};
  Map<String, Object?> get jvm => sections['jvm'] ?? const <String, Object?>{};
  Map<String, Object?> get server =>
      sections['server'] ?? const <String, Object?>{};

  List<MapEntry<String, Object?>> entriesOf(String section) =>
      (sections[section] ?? const <String, Object?>{}).entries.toList();

  List<String> get sectionNames => sections.keys.toList();

  Map<String, Map<String, Object?>> toJson() => sections;
}
