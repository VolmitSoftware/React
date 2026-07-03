library;

import 'package:test/test.dart';
import 'package:reactor/model/alert_thresholds.dart';

void main() {
  group('AlertThresholds defaults', () {
    test('tpsWarn defaults to 18.0', () {
      expect(const AlertThresholds().tpsWarn, equals(18.0));
    });

    test('tpsCrit defaults to 10.0', () {
      expect(const AlertThresholds().tpsCrit, equals(10.0));
    });

    test('msptWarn defaults to 50.0', () {
      expect(const AlertThresholds().msptWarn, equals(50.0));
    });

    test('incidentScoreWarn defaults to 50.0', () {
      expect(const AlertThresholds().incidentScoreWarn, equals(50.0));
    });

    test('gcPercentWarn defaults to 15.0', () {
      expect(const AlertThresholds().gcPercentWarn, equals(15.0));
    });

    test('pingP95Warn defaults to 200.0', () {
      expect(const AlertThresholds().pingP95Warn, equals(200.0));
    });

    test('memoryPressureWarn defaults to 90.0', () {
      expect(const AlertThresholds().memoryPressureWarn, equals(90.0));
    });

    test('AlertThresholds.defaults matches const default ctor', () {
      const AlertThresholds d = AlertThresholds.defaults;
      expect(d.tpsWarn, equals(18.0));
      expect(d.tpsCrit, equals(10.0));
      expect(d.msptWarn, equals(50.0));
      expect(d.incidentScoreWarn, equals(50.0));
      expect(d.gcPercentWarn, equals(15.0));
      expect(d.pingP95Warn, equals(200.0));
      expect(d.memoryPressureWarn, equals(90.0));
    });
  });

  group('AlertThresholds JSON round-trip', () {
    test('toJson then fromJson produces equal values', () {
      const AlertThresholds original = AlertThresholds();
      final Map<String, dynamic> json = original.toJson();
      final AlertThresholds restored = AlertThresholds.fromJson(json);
      expect(restored.tpsWarn, equals(original.tpsWarn));
      expect(restored.tpsCrit, equals(original.tpsCrit));
      expect(restored.msptWarn, equals(original.msptWarn));
      expect(restored.incidentScoreWarn, equals(original.incidentScoreWarn));
      expect(restored.gcPercentWarn, equals(original.gcPercentWarn));
      expect(restored.pingP95Warn, equals(original.pingP95Warn));
      expect(restored.memoryPressureWarn, equals(original.memoryPressureWarn));
    });

    test('fromJson with only tpsCrit sets tpsCrit and keeps other defaults', () {
      final AlertThresholds t = AlertThresholds.fromJson(<String, dynamic>{'tpsCrit': 5.0});
      expect(t.tpsCrit, equals(5.0));
      expect(t.tpsWarn, equals(18.0));
      expect(t.msptWarn, equals(50.0));
      expect(t.incidentScoreWarn, equals(50.0));
      expect(t.gcPercentWarn, equals(15.0));
      expect(t.pingP95Warn, equals(200.0));
      expect(t.memoryPressureWarn, equals(90.0));
    });

    test('fromJson with empty map uses all defaults', () {
      final AlertThresholds t = AlertThresholds.fromJson(<String, dynamic>{});
      expect(t.tpsWarn, equals(18.0));
      expect(t.tpsCrit, equals(10.0));
    });

    test('toJson emits all seven keys', () {
      final Map<String, dynamic> j = const AlertThresholds().toJson();
      expect(j.containsKey('tpsWarn'), isTrue);
      expect(j.containsKey('tpsCrit'), isTrue);
      expect(j.containsKey('msptWarn'), isTrue);
      expect(j.containsKey('incidentScoreWarn'), isTrue);
      expect(j.containsKey('gcPercentWarn'), isTrue);
      expect(j.containsKey('pingP95Warn'), isTrue);
      expect(j.containsKey('memoryPressureWarn'), isTrue);
    });
  });

  group('AlertThresholds.copyWith', () {
    test('copyWith tpsWarn changes only tpsWarn', () {
      const AlertThresholds original = AlertThresholds();
      final AlertThresholds copy = original.copyWith(tpsWarn: 19.0);
      expect(copy.tpsWarn, equals(19.0));
      expect(copy.tpsCrit, equals(original.tpsCrit));
      expect(copy.msptWarn, equals(original.msptWarn));
      expect(copy.incidentScoreWarn, equals(original.incidentScoreWarn));
      expect(copy.gcPercentWarn, equals(original.gcPercentWarn));
      expect(copy.pingP95Warn, equals(original.pingP95Warn));
      expect(copy.memoryPressureWarn, equals(original.memoryPressureWarn));
    });

    test('copyWith tpsCrit changes only tpsCrit', () {
      const AlertThresholds original = AlertThresholds();
      final AlertThresholds copy = original.copyWith(tpsCrit: 5.0);
      expect(copy.tpsCrit, equals(5.0));
      expect(copy.tpsWarn, equals(original.tpsWarn));
    });

    test('copyWith without args preserves all fields', () {
      const AlertThresholds original = AlertThresholds();
      final AlertThresholds copy = original.copyWith();
      expect(copy.tpsWarn, equals(original.tpsWarn));
      expect(copy.tpsCrit, equals(original.tpsCrit));
      expect(copy.msptWarn, equals(original.msptWarn));
      expect(copy.incidentScoreWarn, equals(original.incidentScoreWarn));
      expect(copy.gcPercentWarn, equals(original.gcPercentWarn));
      expect(copy.pingP95Warn, equals(original.pingP95Warn));
      expect(copy.memoryPressureWarn, equals(original.memoryPressureWarn));
    });
  });
}
