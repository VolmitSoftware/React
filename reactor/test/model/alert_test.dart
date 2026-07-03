library;

import 'package:test/test.dart';
import 'package:reactor/model/alert.dart';

FleetAlert _makeAlert({
  String serverId = 's1',
  String serverName = 'Server One',
  String metricId = 'ticks-per-second',
  AlertSeverity severity = AlertSeverity.warning,
  String title = 'Low TPS',
  String message = 'TPS dropped below threshold',
  double value = 15.0,
  double threshold = 18.0,
}) =>
    FleetAlert(
      serverId: serverId,
      serverName: serverName,
      metricId: metricId,
      severity: severity,
      title: title,
      message: message,
      value: value,
      threshold: threshold,
      firstSeen: DateTime(2026, 1, 1),
      lastSeen: DateTime(2026, 1, 2),
    );

void main() {
  group('FleetAlert.key', () {
    test('key is serverId/metricId', () {
      final FleetAlert alert = _makeAlert(serverId: 's1', metricId: 'ticks-per-second');
      expect(alert.key, equals('s1/ticks-per-second'));
    });

    test('key uses provided serverId and metricId', () {
      final FleetAlert alert = _makeAlert(serverId: 'prod-02', metricId: 'memory-pressure');
      expect(alert.key, equals('prod-02/memory-pressure'));
    });
  });

  group('FleetAlert equality', () {
    test('two alerts with same serverId+metricId but different value are equal', () {
      final FleetAlert a = _makeAlert(serverId: 's1', metricId: 'ticks-per-second', value: 15.0);
      final FleetAlert b = _makeAlert(serverId: 's1', metricId: 'ticks-per-second', value: 9.0);
      expect(a, equals(b));
    });

    test('two alerts with same serverId+metricId collapse in a Set to length 1', () {
      final FleetAlert a = _makeAlert(serverId: 's1', metricId: 'ticks-per-second', value: 15.0);
      final FleetAlert b = _makeAlert(serverId: 's1', metricId: 'ticks-per-second', value: 9.0);
      final Set<FleetAlert> set = <FleetAlert>{a, b};
      expect(set.length, equals(1));
    });

    test('alerts with different serverId are not equal', () {
      final FleetAlert a = _makeAlert(serverId: 's1', metricId: 'ticks-per-second');
      final FleetAlert b = _makeAlert(serverId: 's2', metricId: 'ticks-per-second');
      expect(a, isNot(equals(b)));
    });

    test('alerts with different metricId are not equal', () {
      final FleetAlert a = _makeAlert(serverId: 's1', metricId: 'ticks-per-second');
      final FleetAlert b = _makeAlert(serverId: 's1', metricId: 'memory-pressure');
      expect(a, isNot(equals(b)));
    });
  });

  group('alertSeverityRank', () {
    test('critical has highest rank', () {
      expect(alertSeverityRank(AlertSeverity.critical), greaterThan(alertSeverityRank(AlertSeverity.warning)));
    });

    test('warning is higher than info', () {
      expect(alertSeverityRank(AlertSeverity.warning), greaterThan(alertSeverityRank(AlertSeverity.info)));
    });

    test('critical rank is 2', () {
      expect(alertSeverityRank(AlertSeverity.critical), equals(2));
    });

    test('warning rank is 1', () {
      expect(alertSeverityRank(AlertSeverity.warning), equals(1));
    });

    test('info rank is 0', () {
      expect(alertSeverityRank(AlertSeverity.info), equals(0));
    });
  });

  group('FleetAlert.copyWith', () {
    test('copyWith severity preserves key', () {
      final FleetAlert original = _makeAlert(serverId: 's1', metricId: 'ticks-per-second', severity: AlertSeverity.warning);
      final FleetAlert copy = original.copyWith(severity: AlertSeverity.critical);
      expect(copy.key, equals(original.key));
    });

    test('copyWith severity changes severity', () {
      final FleetAlert original = _makeAlert(severity: AlertSeverity.warning);
      final FleetAlert copy = original.copyWith(severity: AlertSeverity.critical);
      expect(copy.severity, equals(AlertSeverity.critical));
    });

    test('copyWith message changes message', () {
      final FleetAlert original = _makeAlert(message: 'old message');
      final FleetAlert copy = original.copyWith(message: 'new message');
      expect(copy.message, equals('new message'));
    });

    test('copyWith value changes value', () {
      final FleetAlert original = _makeAlert(value: 15.0);
      final FleetAlert copy = original.copyWith(value: 8.0);
      expect(copy.value, equals(8.0));
    });

    test('copyWith threshold changes threshold', () {
      final FleetAlert original = _makeAlert(threshold: 18.0);
      final FleetAlert copy = original.copyWith(threshold: 10.0);
      expect(copy.threshold, equals(10.0));
    });

    test('copyWith lastSeen changes lastSeen', () {
      final FleetAlert original = _makeAlert();
      final DateTime newLastSeen = DateTime(2026, 6, 1);
      final FleetAlert copy = original.copyWith(lastSeen: newLastSeen);
      expect(copy.lastSeen, equals(newLastSeen));
    });

    test('copyWith firstSeen changes firstSeen', () {
      final FleetAlert original = _makeAlert();
      final DateTime newFirstSeen = DateTime(2026, 5, 1);
      final FleetAlert copy = original.copyWith(firstSeen: newFirstSeen);
      expect(copy.firstSeen, equals(newFirstSeen));
    });

    test('copyWith without args preserves all fields', () {
      final FleetAlert original = _makeAlert();
      final FleetAlert copy = original.copyWith();
      expect(copy.serverId, equals(original.serverId));
      expect(copy.serverName, equals(original.serverName));
      expect(copy.metricId, equals(original.metricId));
      expect(copy.severity, equals(original.severity));
      expect(copy.title, equals(original.title));
      expect(copy.message, equals(original.message));
      expect(copy.value, equals(original.value));
      expect(copy.threshold, equals(original.threshold));
      expect(copy.firstSeen, equals(original.firstSeen));
      expect(copy.lastSeen, equals(original.lastSeen));
    });
  });
}
