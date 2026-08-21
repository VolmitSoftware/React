library;

import 'package:test/test.dart';
import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/knob.dart';

void main() {
  final Map<String, dynamic> fullJson = <String, dynamic>{
    'id': 'fill',
    'name': 'Fill',
    'description': 'Fills an area',
    'destructive': true,
    'params': <dynamic>[
      <String, dynamic>{
        'key': 'count',
        'label': 'Count',
        'type': 'int',
        'required': true,
        'default': 64,
        'options': <dynamic>[],
      },
      <String, dynamic>{
        'key': 'mode',
        'label': 'Mode',
        'type': 'enum',
        'required': false,
        'default': 'fast',
        'options': <dynamic>['fast', 'safe'],
      },
    ],
  };

  group('ActionDescriptor.fromJson', () {
    test('parses id, name, description, destructive', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.id, equals('fill'));
      expect(d.name, equals('Fill'));
      expect(d.description, equals('Fills an area'));
      expect(d.destructive, isTrue);
    });

    test('parses params length', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params.length, equals(2));
    });

    test('first param type is intType', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params[0].type, equals(KnobType.intType));
    });

    test('first param required is true', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params[0].required, isTrue);
    });

    test('first param defaultValue is 64', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params[0].defaultValue, equals(64));
    });

    test('second param type is enumType', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params[1].type, equals(KnobType.enumType));
    });

    test('second param options equals fast and safe', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      expect(d.params[1].options, equals(<String>['fast', 'safe']));
    });

    test('missing destructive defaults to false', () {
      final Map<String, dynamic> j = <String, dynamic>{'id': 'x', 'name': 'X'};
      final ActionDescriptor d = ActionDescriptor.fromJson(j);
      expect(d.destructive, isFalse);
    });

    test('missing description defaults to empty string', () {
      final Map<String, dynamic> j = <String, dynamic>{'id': 'x', 'name': 'X'};
      final ActionDescriptor d = ActionDescriptor.fromJson(j);
      expect(d.description, equals(''));
    });

    test('missing params defaults to empty list', () {
      final Map<String, dynamic> j = <String, dynamic>{'id': 'x', 'name': 'X'};
      final ActionDescriptor d = ActionDescriptor.fromJson(j);
      expect(d.params, isEmpty);
    });
  });

  group('ActionParam.fromJson', () {
    test('missing required defaults to false', () {
      final Map<String, dynamic> j = <String, dynamic>{
        'key': 'k',
        'label': 'K',
        'type': 'string',
      };
      final ActionParam p = ActionParam.fromJson(j);
      expect(p.required, isFalse);
    });

    test('missing default yields null defaultValue', () {
      final Map<String, dynamic> j = <String, dynamic>{
        'key': 'k',
        'label': 'K',
        'type': 'string',
      };
      final ActionParam p = ActionParam.fromJson(j);
      expect(p.defaultValue, isNull);
    });

    test('missing options defaults to empty list', () {
      final Map<String, dynamic> j = <String, dynamic>{
        'key': 'k',
        'label': 'K',
        'type': 'string',
      };
      final ActionParam p = ActionParam.fromJson(j);
      expect(p.options, isEmpty);
    });
  });

  group('ActionDescriptor.toJson round-trip', () {
    test('toJson round-trips all fields', () {
      final ActionDescriptor d = ActionDescriptor.fromJson(fullJson);
      final Map<String, dynamic> json = d.toJson();
      expect(json['id'], equals('fill'));
      expect(json['destructive'], isTrue);
      final List<dynamic> params = json['params'] as List<dynamic>;
      expect(params.length, equals(2));
      final Map<String, dynamic> p0 = params[0] as Map<String, dynamic>;
      expect(p0['key'], equals('count'));
      expect(p0['default'], equals(64));
      final Map<String, dynamic> p1 = params[1] as Map<String, dynamic>;
      expect(p1['options'], equals(<String>['fast', 'safe']));
    });
  });

  group('ActionTicket.fromJson', () {
    test('parses ticketId and status', () {
      final ActionTicket t = ActionTicket.fromJson(<String, dynamic>{
        'ticketId': 't-1',
        'status': 'queued',
      });
      expect(t.ticketId, equals('t-1'));
      expect(t.status, equals('queued'));
    });

    test('toJson emits ticketId and status', () {
      final ActionTicket t = ActionTicket.fromJson(<String, dynamic>{
        'ticketId': 't-1',
        'status': 'queued',
      });
      final Map<String, dynamic> json = t.toJson();
      expect(json['ticketId'], equals('t-1'));
      expect(json['status'], equals('queued'));
    });
  });
}
