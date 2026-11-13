library;

import 'package:test/test.dart';

import 'package:react_web/model/metric_polarity.dart';

void main() {
  test('lower-is-better metrics rank the smallest value first', () {
    for (final String id in <String>[
      'tick-time',
      'memory-used',
      'incident-score',
      'gc-time-percent',
      'player-ping-p95',
    ]) {
      final List<double> values = <double>[30, 10, 20]
        ..sort(
          (double a, double b) => MetricPolarity.compareBestFirst(id, a, b),
        );
      expect(values, <double>[10, 20, 30], reason: id);
    }
  });

  test('TPS ranks the largest first; counts are neutral, largest first', () {
    final List<double> tps = <double>[18, 20, 12]
      ..sort(
        (double a, double b) =>
            MetricPolarity.compareBestFirst('ticks-per-second', a, b),
      );
    expect(tps, <double>[20, 18, 12]);
    expect(MetricPolarity.of('players'), MetricPolarity.neutral);
  });
}
