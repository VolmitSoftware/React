library;

import 'package:jaspr/jaspr.dart';
import 'package:jaspr_test/jaspr_test.dart';
import 'package:reactor/chart/svg_fallback_chart.dart';
import 'package:reactor/chart/timeseries_chart.dart';

void main() {
  group('SvgFallbackChart', () {
    testComponents(
      'renders a polyline element for a 5-element series',
      (ComponentTester tester) async {
        tester.pumpComponent(
          SvgFallbackChart(
            series: [('y', [1.0, 2.0, 3.0, 2.0, 1.0])],
          ),
        );

        expect(find.tag('polyline'), findsOneComponent);

        final Finder fivePointPolyline = find.byComponentPredicate(
          (Component c) {
            if (c is! DomComponent || c.tag != 'polyline') return false;
            final String? pts = c.attributes?['points'];
            if (pts == null || pts.trim().isEmpty) return false;
            return pts.trim().split(RegExp(r'\s+')).length == 5;
          },
          description: 'polyline with 5 coordinate pairs',
        );
        expect(fivePointPolyline, findsOneComponent);
      },
    );
  });

  group('TimeseriesChart', () {
    testComponents(
      'renders a polyline via SvgFallbackChart when not on web (kIsWeb=false)',
      (ComponentTester tester) async {
        tester.pumpComponent(
          TimeseriesChart(
            series: [('cpu', [10.0, 20.0, 15.0])],
            height: 120,
          ),
        );

        expect(find.tag('polyline'), findsOneComponent);
      },
    );

    testComponents(
      'renders polylines for multiple series when not on web',
      (ComponentTester tester) async {
        tester.pumpComponent(
          TimeseriesChart(
            series: [
              ('cpu', [1.0, 2.0, 3.0]),
              ('mem', [4.0, 5.0, 6.0]),
            ],
          ),
        );

        expect(find.tag('polyline'), findsNComponents(2));
      },
    );
  });
}
