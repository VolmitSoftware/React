class AlertThresholds {
  final double tpsWarn;
  final double tpsCrit;
  final double msptWarn;
  final double incidentScoreWarn;
  final double gcPercentWarn;
  final double pingP95Warn;
  final double memoryPressureWarn;

  const AlertThresholds({
    this.tpsWarn = 18.0,
    this.tpsCrit = 10.0,
    this.msptWarn = 50.0,
    this.incidentScoreWarn = 50.0,
    this.gcPercentWarn = 15.0,
    this.pingP95Warn = 200.0,
    this.memoryPressureWarn = 90.0,
  });

  static const AlertThresholds defaults = AlertThresholds();

  factory AlertThresholds.fromJson(Map<String, dynamic> j) => AlertThresholds(
        tpsWarn: j.containsKey('tpsWarn') ? (j['tpsWarn'] as num).toDouble() : 18.0,
        tpsCrit: j.containsKey('tpsCrit') ? (j['tpsCrit'] as num).toDouble() : 10.0,
        msptWarn: j.containsKey('msptWarn') ? (j['msptWarn'] as num).toDouble() : 50.0,
        incidentScoreWarn: j.containsKey('incidentScoreWarn') ? (j['incidentScoreWarn'] as num).toDouble() : 50.0,
        gcPercentWarn: j.containsKey('gcPercentWarn') ? (j['gcPercentWarn'] as num).toDouble() : 15.0,
        pingP95Warn: j.containsKey('pingP95Warn') ? (j['pingP95Warn'] as num).toDouble() : 200.0,
        memoryPressureWarn: j.containsKey('memoryPressureWarn') ? (j['memoryPressureWarn'] as num).toDouble() : 90.0,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'tpsWarn': tpsWarn,
        'tpsCrit': tpsCrit,
        'msptWarn': msptWarn,
        'incidentScoreWarn': incidentScoreWarn,
        'gcPercentWarn': gcPercentWarn,
        'pingP95Warn': pingP95Warn,
        'memoryPressureWarn': memoryPressureWarn,
      };

  AlertThresholds copyWith({
    double? tpsWarn,
    double? tpsCrit,
    double? msptWarn,
    double? incidentScoreWarn,
    double? gcPercentWarn,
    double? pingP95Warn,
    double? memoryPressureWarn,
  }) =>
      AlertThresholds(
        tpsWarn: tpsWarn ?? this.tpsWarn,
        tpsCrit: tpsCrit ?? this.tpsCrit,
        msptWarn: msptWarn ?? this.msptWarn,
        incidentScoreWarn: incidentScoreWarn ?? this.incidentScoreWarn,
        gcPercentWarn: gcPercentWarn ?? this.gcPercentWarn,
        pingP95Warn: pingP95Warn ?? this.pingP95Warn,
        memoryPressureWarn: memoryPressureWarn ?? this.memoryPressureWarn,
      );
}
