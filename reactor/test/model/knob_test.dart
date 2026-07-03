library;

import 'package:test/test.dart';
import 'package:reactor/model/knob.dart';

void main() {
  group('KnobType.fromWire', () {
    test('bool maps to boolType', () {
      expect(KnobType.fromWire('bool'), equals(KnobType.boolType));
    });

    test('int maps to intType', () {
      expect(KnobType.fromWire('int'), equals(KnobType.intType));
    });

    test('double maps to doubleType', () {
      expect(KnobType.fromWire('double'), equals(KnobType.doubleType));
    });

    test('string maps to stringType', () {
      expect(KnobType.fromWire('string'), equals(KnobType.stringType));
    });

    test('enum maps to enumType', () {
      expect(KnobType.fromWire('enum'), equals(KnobType.enumType));
    });

    test('unknown maps to stringType', () {
      expect(KnobType.fromWire('garbage'), equals(KnobType.stringType));
    });
  });

  group('KnobType.wire', () {
    test('boolType wire is bool', () {
      expect(KnobType.boolType.wire, equals('bool'));
    });

    test('intType wire is int', () {
      expect(KnobType.intType.wire, equals('int'));
    });

    test('doubleType wire is double', () {
      expect(KnobType.doubleType.wire, equals('double'));
    });

    test('stringType wire is string', () {
      expect(KnobType.stringType.wire, equals('string'));
    });

    test('enumType wire is enum', () {
      expect(KnobType.enumType.wire, equals('enum'));
    });
  });

  group('Knob.fromJson', () {
    test('enum type with options parses correctly', () {
      final Map<String, dynamic> json = {
        'key': 'mode',
        'label': 'Mode',
        'type': 'enum',
        'value': 'A',
        'options': ['A', 'B'],
        'doc': 'pick one',
      };
      final Knob k = Knob.fromJson(json);
      expect(k.key, equals('mode'));
      expect(k.label, equals('Mode'));
      expect(k.type, equals(KnobType.enumType));
      expect(k.value, equals('A'));
      expect(k.options, equals(['A', 'B']));
      expect(k.doc, equals('pick one'));
    });

    test('missing options defaults to empty list', () {
      final Map<String, dynamic> json = {
        'key': 'x',
        'label': 'X',
        'type': 'int',
        'value': 1,
      };
      final Knob k = Knob.fromJson(json);
      expect(k.options, isEmpty);
    });

    test('missing doc defaults to empty string', () {
      final Map<String, dynamic> json = {
        'key': 'x',
        'label': 'X',
        'type': 'int',
        'value': 1,
      };
      final Knob k = Knob.fromJson(json);
      expect(k.doc, equals(''));
    });
  });

  group('Knob coercion getters', () {
    test('numeric value 3 -> intValue 3', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'int',
        'value': 3,
      });
      expect(k.intValue, equals(3));
    });

    test('numeric value 3 -> doubleValue 3.0', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'double',
        'value': 3,
      });
      expect(k.doubleValue, equals(3.0));
    });

    test('numeric value 3 -> stringValue 3', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'string',
        'value': 3,
      });
      expect(k.stringValue, equals('3'));
    });

    test('bool true -> boolValue true', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'bool',
        'value': true,
      });
      expect(k.boolValue, isTrue);
    });

    test('bool false -> boolValue false', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'bool',
        'value': false,
      });
      expect(k.boolValue, isFalse);
    });
  });

  group('Knob toJson round-trip', () {
    test('toJson(fromJson(x)) round-trips type as wire double', () {
      final Map<String, dynamic> original = {
        'key': 'speed',
        'label': 'Speed',
        'type': 'double',
        'value': 1.5,
        'options': <String>[],
        'doc': '',
      };
      final Knob k = Knob.fromJson(original);
      final Map<String, dynamic> roundTripped = k.toJson();
      expect(roundTripped['type'], equals('double'));
      expect(roundTripped['value'], equals(1.5));
      expect(roundTripped['key'], equals('speed'));
    });
  });

  group('Knob.copyWith', () {
    test('copyWith(value:) replaces only value', () {
      final Knob k = Knob.fromJson({
        'key': 'k',
        'label': 'K',
        'type': 'int',
        'value': 1,
        'options': <String>[],
        'doc': 'doc',
      });
      final Knob copy = k.copyWith(value: 99);
      expect(copy.value, equals(99));
      expect(copy.key, equals('k'));
      expect(copy.label, equals('K'));
      expect(copy.type, equals(KnobType.intType));
      expect(copy.doc, equals('doc'));
    });
  });
}
