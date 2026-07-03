import 'package:reactor/model/knob.dart';

class ActionParam {
  final String key;
  final String label;
  final KnobType type;
  final bool required;
  final Object? defaultValue;
  final List<String> options;

  const ActionParam({
    required this.key,
    required this.label,
    required this.type,
    required this.required,
    required this.defaultValue,
    this.options = const <String>[],
  });

  factory ActionParam.fromJson(Map<String, dynamic> j) {
    return ActionParam(
      key: j['key'] as String,
      label: j['label'] as String,
      type: KnobType.fromWire(j['type'] as String),
      required: (j['required'] as bool?) ?? false,
      defaultValue: j['default'] as Object?,
      options: (j['options'] as List<dynamic>?)
              ?.map((dynamic e) => e as String)
              .toList() ??
          const <String>[],
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'key': key,
        'label': label,
        'type': type.wire,
        'required': required,
        'default': defaultValue,
        'options': options,
      };
}

class ActionDescriptor {
  final String id;
  final String name;
  final String description;
  final bool destructive;
  final List<ActionParam> params;

  const ActionDescriptor({
    required this.id,
    required this.name,
    required this.description,
    required this.destructive,
    this.params = const <ActionParam>[],
  });

  factory ActionDescriptor.fromJson(Map<String, dynamic> j) {
    return ActionDescriptor(
      id: j['id'] as String,
      name: j['name'] as String,
      description: (j['description'] as String?) ?? '',
      destructive: (j['destructive'] as bool?) ?? false,
      params: (j['params'] as List<dynamic>?)
              ?.map(
                  (dynamic e) => ActionParam.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const <ActionParam>[],
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'name': name,
        'description': description,
        'destructive': destructive,
        'params': params.map((ActionParam p) => p.toJson()).toList(),
      };
}

class ActionTicket {
  final String ticketId;
  final String status;

  const ActionTicket({
    required this.ticketId,
    required this.status,
  });

  factory ActionTicket.fromJson(Map<String, dynamic> j) {
    return ActionTicket(
      ticketId: j['ticketId'] as String,
      status: j['status'] as String,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'ticketId': ticketId,
        'status': status,
      };
}
