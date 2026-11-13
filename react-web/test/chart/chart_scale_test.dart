library;

import 'package:test/test.dart';

import 'package:react_web/chart/chart_scale.dart';

void main() {
  group('chartScale', () {
    test('values near zero start the axis at zero with nice ticks', () {
      final ChartScale scale = chartScale(<double>[3.2, 7.9, 12.4]);
      expect(scale.min, 0);
      expect(scale.max, greaterThanOrEqualTo(12.4));
      expect(scale.ticks.first, 0);
      expect(scale.ticks.last, scale.max);
      expect(scale.ticks.length, inInclusiveRange(3, 6));
    });

    test('a small wobble far from zero keeps a minimum span', () {
      // 66.48 to 67.06 ms must not fill the plot as a steep climb.
      final ChartScale scale = chartScale(<double>[66.48, 66.7, 67.06]);
      expect(scale.max - scale.min, greaterThanOrEqualTo(67.06 * 0.2));
      expect(scale.min, lessThanOrEqualTo(66.48));
      expect(scale.max, greaterThanOrEqualTo(67.06));
      final double drawn = (67.06 - 66.48) / (scale.max - scale.min);
      expect(drawn, lessThan(0.1));
    });

    test('values never below zero never get a negative axis', () {
      final ChartScale scale = chartScale(<double>[0.4, 0.5, 0.45]);
      expect(scale.min, greaterThanOrEqualTo(0));
    });

    test('a constant series gets a span around its value', () {
      final ChartScale scale = chartScale(<double>[20, 20, 20]);
      expect(scale.min, lessThan(20));
      expect(scale.max, greaterThan(20));
    });

    test('ticks are evenly spaced', () {
      final ChartScale scale = chartScale(<double>[40.57, 45.26]);
      final List<double> gaps = <double>[
        for (int i = 1; i < scale.ticks.length; i++)
          scale.ticks[i] - scale.ticks[i - 1],
      ];
      for (final double gap in gaps) {
        expect(gap, closeTo(gaps.first, 1e-9));
      }
    });

    test('labels use one precision for every tick', () {
      final ChartScale scale = chartScale(<double>[0, 0.37]);
      final List<String> labels = scale.ticks.map(scale.label).toList();
      final Set<int> decimals = <int>{
        for (final String label in labels)
          label.contains('.') ? label.split('.').last.length : 0,
      };
      expect(decimals, hasLength(1));
    });
  });

  group('isFlat', () {
    test('short or constant series are flat', () {
      expect(isFlat(<double>[]), isTrue);
      expect(isFlat(<double>[4]), isTrue);
      expect(isFlat(<double>[40, 40, 40]), isTrue);
      expect(isFlat(<double>[40, 40.5]), isFalse);
    });
  });
}
