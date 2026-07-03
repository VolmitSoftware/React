enum KnobType {
  boolType,
  intType,
  doubleType,
  stringType,
  enumType;

  static KnobType fromWire(String w) {
    switch (w) {
      case 'bool':
        return KnobType.boolType;
      case 'int':
        return KnobType.intType;
      case 'double':
        return KnobType.doubleType;
      case 'string':
        return KnobType.stringType;
      case 'enum':
        return KnobType.enumType;
      default:
        return KnobType.stringType;
    }
  }

  String get wire {
    switch (this) {
      case KnobType.boolType:
        return 'bool';
      case KnobType.intType:
        return 'int';
      case KnobType.doubleType:
        return 'double';
      case KnobType.stringType:
        return 'string';
      case KnobType.enumType:
        return 'enum';
    }
  }
}

class Knob {
  final String key;
  final String label;
  final KnobType type;
  final Object? value;
  final List<String> options;
  final String doc;

  const Knob({
    required this.key,
    required this.label,
    required this.type,
    required this.value,
    this.options = const <String>[],
    this.doc = '',
  });

  factory Knob.fromJson(Map<String, dynamic> j) {
    return Knob(
      key: j['key'] as String,
      label: j['label'] as String,
      type: KnobType.fromWire(j['type'] as String),
      value: j['value'] as Object?,
      options: (j['options'] as List<dynamic>?)
              ?.map((dynamic e) => e as String)
              .toList() ??
          const <String>[],
      doc: (j['doc'] as String?) ?? '',
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'key': key,
        'label': label,
        'type': type.wire,
        'value': value,
        'options': options,
        'doc': doc,
      };

  Knob copyWith({Object? value}) {
    return Knob(
      key: key,
      label: label,
      type: type,
      value: value,
      options: options,
      doc: doc,
    );
  }

  bool get boolValue => value == true;

  int get intValue =>
      value is num ? (value as num).toInt() : int.tryParse('$value') ?? 0;

  double get doubleValue => value is num
      ? (value as num).toDouble()
      : double.tryParse('$value') ?? 0.0;

  String get stringValue => value?.toString() ?? '';
}
