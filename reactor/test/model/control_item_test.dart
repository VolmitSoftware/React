library;

import 'package:test/test.dart';
import 'package:reactor/model/control_item.dart';
import 'package:reactor/model/knob.dart';

void main() {
  final Map<String, dynamic> baseJson = {
    'id': 'feature1',
    'name': 'Feature One',
    'category': 'performance',
    'enabled': true,
    'description': 'A test feature',
    'knobs': [
      {
        'key': 'k1',
        'label': 'Knob 1',
        'type': 'int',
        'value': 10,
      },
      {
        'key': 'k2',
        'label': 'Knob 2',
        'type': 'bool',
        'value': false,
      },
    ],
  };

  group('ControlItem.fromJson', () {
    test('parses all fields correctly', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      expect(item.id, equals('feature1'));
      expect(item.name, equals('Feature One'));
      expect(item.category, equals('performance'));
      expect(item.enabled, isTrue);
      expect(item.description, equals('A test feature'));
    });

    test('parses 2 knobs from knobs array', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      expect(item.knobs.length, equals(2));
      expect(item.knobs[0].key, equals('k1'));
      expect(item.knobs[1].key, equals('k2'));
    });

    test('missing knobs key yields empty list', () {
      final Map<String, dynamic> json = {
        'id': 'f2',
        'name': 'F2',
        'category': 'cat',
        'enabled': false,
      };
      final ControlItem item = ControlItem.fromJson(json);
      expect(item.knobs, isEmpty);
    });

    test('missing description defaults to empty string', () {
      final Map<String, dynamic> json = {
        'id': 'f2',
        'name': 'F2',
        'category': 'cat',
        'enabled': false,
      };
      final ControlItem item = ControlItem.fromJson(json);
      expect(item.description, equals(''));
    });
  });

  group('ControlItem.copyWith', () {
    test('copyWith(enabled:false) flips only enabled', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      final ControlItem copy = item.copyWith(enabled: false);
      expect(copy.enabled, isFalse);
      expect(copy.id, equals('feature1'));
      expect(copy.name, equals('Feature One'));
      expect(copy.category, equals('performance'));
      expect(copy.knobs.length, equals(2));
    });
  });

  group('ControlItem.withKnobValue', () {
    test('withKnobValue replaces value of matching knob', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      final ControlItem updated = item.withKnobValue('k1', 5);
      expect(updated.knobs[0].value, equals(5));
    });

    test('withKnobValue leaves other knobs untouched', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      final ControlItem updated = item.withKnobValue('k1', 5);
      expect(updated.knobs[1].key, equals('k2'));
      expect(updated.knobs[1].value, equals(false));
    });
  });

  group('ControlItem.toJson round-trip', () {
    test('toJson produces correct keys and fromJson round-trips', () {
      final ControlItem item = ControlItem.fromJson(baseJson);
      final Map<String, dynamic> json = item.toJson();
      expect(json['id'], equals('feature1'));
      expect(json['enabled'], isTrue);
      final List<dynamic> knobsJson = json['knobs'] as List<dynamic>;
      expect(knobsJson.length, equals(2));
      final ControlItem roundTripped =
          ControlItem.fromJson(json as Map<String, dynamic>);
      expect(roundTripped.id, equals(item.id));
      expect(roundTripped.knobs.length, equals(2));
    });
  });
}
