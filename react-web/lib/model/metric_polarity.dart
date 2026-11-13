/// Which direction is better for a sampler, so rankings put the healthiest
/// server first: high TPS is good, high tick time is bad.
enum MetricPolarity {
  higherIsBetter,
  lowerIsBetter,
  neutral;

  static const Set<String> _higher = <String>{
    'ticks-per-second',
    'memory-free',
  };

  static const Set<String> _neutral = <String>{
    'players',
    'plugins',
    'chunks',
    'chunks-loaded',
    'entities',
  };

  /// Unknown samplers default to lower is better: nearly every sampler React
  /// reports is a cost (time, memory, pressure, queue, latency).
  static MetricPolarity of(String samplerId) {
    if (_higher.contains(samplerId)) return MetricPolarity.higherIsBetter;
    if (_neutral.contains(samplerId)) return MetricPolarity.neutral;
    return MetricPolarity.lowerIsBetter;
  }

  /// Sort comparator: best value first. Neutral metrics rank highest first.
  static int compareBestFirst(String samplerId, double a, double b) =>
      switch (of(samplerId)) {
        MetricPolarity.lowerIsBetter => a.compareTo(b),
        MetricPolarity.higherIsBetter ||
        MetricPolarity.neutral => b.compareTo(a),
      };
}
