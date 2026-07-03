enum PressureMode {
  normal,
  pressure,
  panic;

  static PressureMode fromWire(String w) {
    switch (w) {
      case 'NORMAL':
        return PressureMode.normal;
      case 'PRESSURE':
        return PressureMode.pressure;
      case 'PANIC':
        return PressureMode.panic;
      default:
        return PressureMode.normal;
    }
  }

  String get wire {
    switch (this) {
      case PressureMode.normal:
        return 'NORMAL';
      case PressureMode.pressure:
        return 'PRESSURE';
      case PressureMode.panic:
        return 'PANIC';
    }
  }
}

class WorldSettings {
  final String name;
  final PressureMode pressureMode;
  final double budgetMs;
  final double panicMs;
  final double releaseMs;

  const WorldSettings({
    required this.name,
    required this.pressureMode,
    required this.budgetMs,
    required this.panicMs,
    required this.releaseMs,
  });

  factory WorldSettings.fromJson(Map<String, dynamic> j) {
    return WorldSettings(
      name: j['name'] as String,
      pressureMode: PressureMode.fromWire(j['pressureMode'] as String),
      budgetMs: (j['budgetMs'] as num).toDouble(),
      panicMs: (j['panicMs'] as num).toDouble(),
      releaseMs: (j['releaseMs'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'name': name,
        'pressureMode': pressureMode.wire,
        'budgetMs': budgetMs,
        'panicMs': panicMs,
        'releaseMs': releaseMs,
      };

  WorldSettings copyWith({
    PressureMode? pressureMode,
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) {
    return WorldSettings(
      name: name,
      pressureMode: pressureMode ?? this.pressureMode,
      budgetMs: budgetMs ?? this.budgetMs,
      panicMs: panicMs ?? this.panicMs,
      releaseMs: releaseMs ?? this.releaseMs,
    );
  }
}
