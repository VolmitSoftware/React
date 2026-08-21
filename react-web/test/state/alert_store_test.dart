library;

import 'package:test/test.dart';

import 'package:react_web/model/alert.dart';
import 'package:react_web/model/alert_thresholds.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

FleetAlert _alert(
  String serverId,
  String metricId, {
  AlertSeverity severity = AlertSeverity.warning,
  DateTime? firstSeen,
}) {
  final DateTime ts = firstSeen ?? DateTime(2026, 6, 28, 12, 0, 0);
  return FleetAlert(
    serverId: serverId,
    serverName: serverId,
    metricId: metricId,
    severity: severity,
    title: 'Test',
    message: 'msg',
    value: 1.0,
    threshold: 0.5,
    firstSeen: ts,
    lastSeen: ts,
  );
}

void main() {
  group('AlertStore.ack persistence', () {
    test('ack persists across store instances', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      AlertStore(storage).ack('s1/x');
      expect(AlertStore(storage).isAcked('s1/x'), isTrue);
    });

    test('unack removes from persisted storage', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final AlertStore store = AlertStore(storage);
      store.ack('s1/x');
      store.unack('s1/x');
      expect(AlertStore(storage).isAcked('s1/x'), isFalse);
    });
  });

  group('AlertStore.thresholds persistence', () {
    test('empty storage returns AlertThresholds.defaults', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      expect(
        store.thresholds.tpsWarn,
        equals(AlertThresholds.defaults.tpsWarn),
      );
    });

    test('thresholds setter persists across store instances', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      AlertStore(storage).thresholds = AlertThresholds.defaults.copyWith(
        tpsWarn: 19.0,
      );
      expect(AlertStore(storage).thresholds.tpsWarn, equals(19.0));
    });
  });

  group('AlertStore.resolve / reconcile', () {
    test('resolved alert is excluded from open feed', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final AlertStore store = AlertStore(storage);
      store.resolve('s1/x');
      final List<FleetAlert> open = store.reconcile(<FleetAlert>[
        _alert('s1', 'x'),
        _alert('s1', 'y'),
      ]);
      expect(open.map((FleetAlert a) => a.key), isNot(contains('s1/x')));
      expect(open.map((FleetAlert a) => a.key), contains('s1/y'));
    });

    test('unresolve restores alert to open feed', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final AlertStore store = AlertStore(storage);
      store.resolve('s1/x');
      store.unresolve('s1/x');
      final List<FleetAlert> open = store.reconcile(<FleetAlert>[
        _alert('s1', 'x'),
        _alert('s1', 'y'),
      ]);
      expect(
        open.map((FleetAlert a) => a.key),
        containsAll(<String>['s1/x', 's1/y']),
      );
    });

    test('reconcile orders critical before warning', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final List<FleetAlert> live = <FleetAlert>[
        _alert('s1', 'mspt', severity: AlertSeverity.warning),
        _alert('s1', 'tps', severity: AlertSeverity.critical),
      ];
      final List<FleetAlert> open = store.reconcile(live);
      expect(open.first.severity, equals(AlertSeverity.critical));
    });
  });

  group('AlertStore.reconcile firstSeen preservation', () {
    test('earliest firstSeen is preserved across reconcile calls', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final DateTime t1 = DateTime(2026, 6, 28, 10, 0, 0);
      final DateTime t2 = DateTime(2026, 6, 28, 12, 0, 0);
      store.reconcile(<FleetAlert>[_alert('s1', 'x', firstSeen: t1)]);
      final List<FleetAlert> second = store.reconcile(<FleetAlert>[
        _alert('s1', 'x', firstSeen: t2),
      ]);
      expect(second.first.firstSeen, equals(t1));
    });
  });

  group('AlertStore.detectNewCritical', () {
    test('returns keys of newly seen critical alerts', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final FleetAlert critA = _alert(
        's1',
        'tps',
        severity: AlertSeverity.critical,
      );
      final FleetAlert warnB = _alert(
        's1',
        'mspt',
        severity: AlertSeverity.warning,
      );
      final Set<String> newCrit = store.detectNewCritical(<FleetAlert>[
        critA,
        warnB,
      ]);
      expect(newCrit, equals(<String>{critA.key}));
    });

    test('second call with same list returns empty set', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final FleetAlert critA = _alert(
        's1',
        'tps',
        severity: AlertSeverity.critical,
      );
      store.detectNewCritical(<FleetAlert>[critA]);
      final Set<String> second = store.detectNewCritical(<FleetAlert>[critA]);
      expect(second, isEmpty);
    });

    test('adding a new critical on third call returns only the new one', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final FleetAlert critA = _alert(
        's1',
        'tps',
        severity: AlertSeverity.critical,
      );
      final FleetAlert critC = _alert(
        's1',
        'gc',
        severity: AlertSeverity.critical,
      );
      store.detectNewCritical(<FleetAlert>[critA]);
      store.detectNewCritical(<FleetAlert>[critA]);
      final Set<String> third = store.detectNewCritical(<FleetAlert>[
        critA,
        critC,
      ]);
      expect(third, equals(<String>{critC.key}));
    });
  });

  group('AlertStore corrupt storage', () {
    test('corrupt acked value constructs without throwing', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write('reactor.alerts.acked', 'NOT_JSON{{{{');
      expect(() => AlertStore(storage), returnsNormally);
    });

    test('corrupt acked value results in empty acked set', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write('reactor.alerts.acked', 'NOT_JSON{{{{');
      final AlertStore store = AlertStore(storage);
      expect(store.isAcked('anything'), isFalse);
    });

    test('corrupt thresholds value results in defaults', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write('reactor.alerts.thresholds', 'NOT_JSON{{{{');
      final AlertStore store = AlertStore(storage);
      expect(
        store.thresholds.tpsWarn,
        equals(AlertThresholds.defaults.tpsWarn),
      );
    });
  });
}
