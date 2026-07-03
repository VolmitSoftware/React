import 'package:reactor/model/knob.dart';

class ConfigSection {
  final String name;
  final List<Knob> nodes;

  const ConfigSection({
    required this.name,
    required this.nodes,
  });

  factory ConfigSection.fromJson(Map<String, dynamic> j) {
    return ConfigSection(
      name: j['name'] as String,
      nodes: (j['nodes'] as List<dynamic>?)
              ?.map((dynamic e) => Knob.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const <Knob>[],
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'name': name,
        'nodes': nodes.map((Knob k) => k.toJson()).toList(),
      };
}

class ConfigTree {
  final List<ConfigSection> sections;

  const ConfigTree({
    this.sections = const <ConfigSection>[],
  });

  factory ConfigTree.fromJson(Map<String, dynamic> j) {
    return ConfigTree(
      sections: (j['sections'] as List<dynamic>?)
              ?.map((dynamic e) =>
                  ConfigSection.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const <ConfigSection>[],
    );
  }

  Map<String, Object?> flatten() {
    final Map<String, Object?> result = <String, Object?>{};
    for (final ConfigSection section in sections) {
      for (final Knob node in section.nodes) {
        result[node.key] = node.value;
      }
    }
    return result;
  }

  Knob? node(String key) {
    for (final ConfigSection section in sections) {
      for (final Knob k in section.nodes) {
        if (k.key == key) {
          return k;
        }
      }
    }
    return null;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'sections': sections.map((ConfigSection s) => s.toJson()).toList(),
      };
}
