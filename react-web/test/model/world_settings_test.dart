library;

import 'package:test/test.dart';
import 'package:react_web/model/world_settings.dart';

void main() {
  group('PressureMode.fromWire', () {
    test('NORMAL maps to normal', () {
      expect(PressureMode.fromWire('NORMAL'), equals(PressureMode.normal));
    });

    test('PRESSURE maps to pressure', () {
      expect(PressureMode.fromWire('PRESSURE'), equals(PressureMode.pressure));
    });

    test('PANIC maps to panic', () {
      expect(PressureMode.fromWire('PANIC'), equals(PressureMode.panic));
    });

    test('unknown maps to normal', () {
      expect(PressureMode.fromWire('UNKNOWN'), equals(PressureMode.normal));
    });
  });

  group('PressureMode.wire', () {
    test('normal wire is NORMAL', () {
      expect(PressureMode.normal.wire, equals('NORMAL'));
    });

    test('pressure wire is PRESSURE', () {
      expect(PressureMode.pressure.wire, equals('PRESSURE'));
    });

    test('panic wire is PANIC', () {
      expect(PressureMode.panic.wire, equals('PANIC'));
    });
  });

  group('WorldSettings.fromJson', () {
    test('parses all fields correctly', () {
      final Map<String, dynamic> json = {
        'key': 'minecraft:world',
        'name': 'world',
        'pressureMode': 'PANIC',
        'budgetMs': 50.0,
        'panicMs': 55.0,
        'releaseMs': 45.0,
      };
      final WorldSettings ws = WorldSettings.fromJson(json);
      expect(ws.key, equals('minecraft:world'));
      expect(ws.name, equals('world'));
      expect(ws.pressureMode, equals(PressureMode.panic));
      expect(ws.budgetMs, equals(50.0));
      expect(ws.panicMs, equals(55.0));
      expect(ws.releaseMs, equals(45.0));
    });

    test('integer json values coerce to double', () {
      final Map<String, dynamic> json = {
        'key': 'minecraft:w2',
        'name': 'w2',
        'pressureMode': 'NORMAL',
        'budgetMs': 20,
        'panicMs': 25,
        'releaseMs': 18,
      };
      final WorldSettings ws = WorldSettings.fromJson(json);
      expect(ws.budgetMs, equals(20.0));
      expect(ws.panicMs, equals(25.0));
      expect(ws.releaseMs, equals(18.0));
    });
  });

  group('WorldSettings.copyWith', () {
    test('copyWith(budgetMs:) changes only budgetMs', () {
      final Map<String, dynamic> json = {
        'key': 'minecraft:world',
        'name': 'world',
        'pressureMode': 'NORMAL',
        'budgetMs': 50.0,
        'panicMs': 55.0,
        'releaseMs': 45.0,
      };
      final WorldSettings ws = WorldSettings.fromJson(json);
      final WorldSettings copy = ws.copyWith(budgetMs: 30.0);
      expect(copy.budgetMs, equals(30.0));
      expect(copy.key, equals('minecraft:world'));
      expect(copy.name, equals('world'));
      expect(copy.pressureMode, equals(PressureMode.normal));
      expect(copy.panicMs, equals(55.0));
      expect(copy.releaseMs, equals(45.0));
    });
  });

  group('WorldSettings.toJson round-trip', () {
    test('toJson encodes pressureMode as wire string', () {
      final Map<String, dynamic> json = {
        'key': 'minecraft:world',
        'name': 'world',
        'pressureMode': 'PRESSURE',
        'budgetMs': 40.0,
        'panicMs': 45.0,
        'releaseMs': 35.0,
      };
      final WorldSettings ws = WorldSettings.fromJson(json);
      final Map<String, dynamic> out = ws.toJson();
      expect(out['pressureMode'], equals('PRESSURE'));
      expect(out['key'], equals('minecraft:world'));
      expect(out['name'], equals('world'));
      expect(out['budgetMs'], equals(40.0));
    });
  });
}
