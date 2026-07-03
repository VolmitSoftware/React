import 'package:reactor/model/knob.dart';

class ControlItem {
  final String id;
  final String name;
  final String category;
  final bool enabled;
  final String description;
  final List<Knob> knobs;

  const ControlItem({
    required this.id,
    required this.name,
    required this.category,
    required this.enabled,
    required this.description,
    this.knobs = const <Knob>[],
  });

  factory ControlItem.fromJson(Map<String, dynamic> j) {
    return ControlItem(
      id: j['id'] as String,
      name: j['name'] as String,
      category: j['category'] as String,
      enabled: j['enabled'] as bool,
      description: (j['description'] as String?) ?? '',
      knobs: (j['knobs'] as List<dynamic>?)
              ?.map((dynamic e) => Knob.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const <Knob>[],
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'category': category,
        'enabled': enabled,
        'description': description,
        'knobs': knobs.map((Knob k) => k.toJson()).toList(),
      };

  ControlItem copyWith({bool? enabled, List<Knob>? knobs}) {
    return ControlItem(
      id: id,
      name: name,
      category: category,
      enabled: enabled ?? this.enabled,
      description: description,
      knobs: knobs ?? this.knobs,
    );
  }

  ControlItem withKnobValue(String key, Object? value) {
    return copyWith(
      knobs: knobs.map((Knob k) {
        if (k.key == key) {
          return k.copyWith(value: value);
        }
        return k;
      }).toList(),
    );
  }
}
