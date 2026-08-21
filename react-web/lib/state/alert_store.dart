import 'dart:convert';
import 'dart:developer' as developer;

import '../model/alert.dart';
import '../model/alert_thresholds.dart';
import 'fleet_manager.dart';

class AlertStore {
  static const String _ackedKey = 'reactor.alerts.acked';
  static const String _resolvedKey = 'reactor.alerts.resolved';
  static const String _thresholdsKey = 'reactor.alerts.thresholds';

  final FleetStorage storage;

  late Set<String> _acked;
  late Set<String> _resolved;
  late AlertThresholds _thresholds;

  final Map<String, DateTime> _firstSeenByKey = <String, DateTime>{};
  Set<String> _previousCriticalKeys = <String>{};

  AlertStore(this.storage) {
    _acked = _loadStringSet(_ackedKey);
    _resolved = _loadStringSet(_resolvedKey);
    _thresholds = _loadThresholds();
  }

  AlertThresholds get thresholds => _thresholds;

  set thresholds(AlertThresholds t) {
    _thresholds = t;
    storage.write(_thresholdsKey, jsonEncode(t.toJson()));
  }

  bool isAcked(String key) => _acked.contains(key);

  void ack(String key) {
    _acked.add(key);
    _persistSet(_ackedKey, _acked);
  }

  void unack(String key) {
    _acked.remove(key);
    _persistSet(_ackedKey, _acked);
  }

  bool isResolved(String key) => _resolved.contains(key);

  void resolve(String key) {
    _resolved.add(key);
    _persistSet(_resolvedKey, _resolved);
  }

  void unresolve(String key) {
    _resolved.remove(key);
    _persistSet(_resolvedKey, _resolved);
  }

  List<FleetAlert> reconcile(List<FleetAlert> live) {
    final Set<String> liveKeys = <String>{};
    for (final FleetAlert a in live) {
      liveKeys.add(a.key);
    }

    final Set<String> staleKeys = _firstSeenByKey.keys
        .where((String k) => !liveKeys.contains(k))
        .toSet();
    for (final String k in staleKeys) {
      _firstSeenByKey.remove(k);
    }

    final List<FleetAlert> open = <FleetAlert>[];
    for (final FleetAlert a in live) {
      if (_resolved.contains(a.key)) continue;
      final DateTime earliest = _firstSeenByKey.putIfAbsent(
        a.key,
        () => a.firstSeen,
      );
      open.add(a.copyWith(firstSeen: earliest));
    }

    open.sort((FleetAlert a, FleetAlert b) {
      final int rankCmp = alertSeverityRank(
        b.severity,
      ).compareTo(alertSeverityRank(a.severity));
      if (rankCmp != 0) return rankCmp;
      final int nameCmp = a.serverName.compareTo(b.serverName);
      if (nameCmp != 0) return nameCmp;
      return a.metricId.compareTo(b.metricId);
    });

    return open;
  }

  Set<String> detectNewCritical(List<FleetAlert> live) {
    final Set<String> currentCritical = <String>{};
    for (final FleetAlert a in live) {
      if (a.severity == AlertSeverity.critical) {
        currentCritical.add(a.key);
      }
    }
    final Set<String> newCritical = currentCritical.difference(
      _previousCriticalKeys,
    );
    _previousCriticalKeys = currentCritical;
    return newCritical;
  }

  Set<String> _loadStringSet(String key) {
    final String? raw = storage.read(key);
    if (raw == null) return <String>{};
    try {
      final List<dynamic> list = jsonDecode(raw) as List<dynamic>;
      return list.map((dynamic e) => e as String).toSet();
    } on Object catch (e) {
      developer.log(
        'corrupt alert storage at $key, starting fresh: $e',
        name: 'AlertStore',
        level: 500,
      );
      return <String>{};
    }
  }

  AlertThresholds _loadThresholds() {
    final String? raw = storage.read(_thresholdsKey);
    if (raw == null) return AlertThresholds.defaults;
    try {
      final Map<String, dynamic> json = jsonDecode(raw) as Map<String, dynamic>;
      return AlertThresholds.fromJson(json);
    } on Object catch (e) {
      developer.log(
        'corrupt thresholds storage, using defaults: $e',
        name: 'AlertStore',
        level: 500,
      );
      return AlertThresholds.defaults;
    }
  }

  void _persistSet(String key, Set<String> set) {
    storage.write(key, jsonEncode(set.toList()));
  }
}
