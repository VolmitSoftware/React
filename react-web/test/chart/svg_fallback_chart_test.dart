library;

import 'package:jaspr/jaspr.dart';
import 'package:jaspr_test/jaspr_test.dart';
import 'package:react_web/chart/svg_fallback_chart.dart';
import 'package:react_web/chart/timeseries_chart.dart';

Finder _byClass(String name) => find.byComponentPredicate(
  (Component component) =>
      component is DomComponent &&
      (component.classes?.split(' ').contains(name) ?? false),
);

Finder _sampleTarget(int index) => find.byComponentPredicate(
  (Component component) =>
      component is DomComponent &&
      component.tag == 'rect' &&
      component.attributes?['data-sample'] == '$index',
);

void main() {
  group('SvgFallbackChart', () {
    testComponents(
      'hover preserves geometry and updated samples invalidate it',
      (ComponentTester tester) async {
        List<(String, List<double>)> series = <(String, List<double>)>[
          ('TPS', <double>[10, 15, 20]),
        ];
        late void Function(void Function()) rebuild;
        tester.pumpComponent(
          StatefulBuilder(
            builder:
                (
                  BuildContext context,
                  void Function(void Function()) setState,
                ) {
                  rebuild = setState;
                  return TimeseriesChart(series: series);
                },
          ),
        );
        final Component original = find
            .tag('polyline')
            .evaluate()
            .single
            .component;
        tester.dispatchEvent(_sampleTarget(1), 'mouseenter');
        await tester.pump();
        expect(
          identical(find.tag('polyline').evaluate().single.component, original),
          isTrue,
        );
        expect(find.text('15.0'), findsOneComponent);
        rebuild(
          () => series = <(String, List<double>)>[
            ('TPS', <double>[20, 10, 15]),
          ],
        );
        await tester.pump();
        expect(
          identical(find.tag('polyline').evaluate().single.component, original),
          isFalse,
        );
        expect(find.text('10.0'), findsOneComponent);
      },
    );

    testComponents('the plot is one tab stop; samples are not', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        SvgFallbackChart(
          series: <(String, List<double>)>[
            ('y', <double>[1.0, 2.0, 3.0, 2.0, 1.0]),
          ],
        ),
      );
      expect(
        find.byComponentPredicate(
          (Component c) =>
              c is DomComponent && c.attributes?['tabindex'] != null,
        ),
        findsOneComponent,
      );
      expect(
        find.byComponentPredicate(
          (Component c) =>
              c is DomComponent &&
              c.tag == 'svg' &&
              c.attributes?['role'] == 'img' &&
              c.attributes?['tabindex'] == '0',
        ),
        findsOneComponent,
      );
      expect(_byClass('reactor-chart-hit-target'), findsNComponents(5));
    });

    testComponents('focus reads out the newest sample', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        TimeseriesChart(
          series: <(String, List<double>)>[
            ('TPS', <double>[19.7, 18.2]),
          ],
        ),
      );
      tester.dispatchEvent(find.tag('svg'), 'focus');
      await tester.pump();
      expect(find.text('18.2'), findsOneComponent);
      tester.dispatchEvent(find.tag('svg'), 'blur');
      await tester.pump();
      expect(_byClass('reactor-chart-tooltip'), findsNothing);
    });

    testComponents('bounds hover targets for long series', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        SvgFallbackChart(
          series: <(String, List<double>)>[
            (
              'long',
              List<double>.generate(500, (int index) => index.toDouble()),
            ),
          ],
        ),
      );
      expect(
        _byClass('reactor-chart-hit-target'),
        findsNComponents(kChartHoverTargets),
      );
    });

    testComponents('ticks carry the unit and the read-out too', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        SvgFallbackChart(
          series: <(String, List<double>)>[
            ('Tick time', <double>[12, 30]),
          ],
          unit: 'ms',
          activeSample: 1,
        ),
      );
      expect(find.text('0 ms'), findsOneComponent);
      expect(find.text('30.0 ms'), findsOneComponent);
      expect(_byClass('reactor-chart-crosshair'), findsOneComponent);
    });

    testComponents('uses the supplied value formatter', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        SvgFallbackChart(
          series: <(String, List<double>)>[
            ('Read', <double>[1024, 2048]),
          ],
          activeSample: 0,
          valueFormatter: (double value) => 'rate:${value.toStringAsFixed(0)}',
        ),
      );
      expect(find.text('rate:1024'), findsOneComponent);
    });
  });

  group('TimeseriesChart', () {
    testComponents('uses wall-clock positions, splits gaps, labels time', (
      ComponentTester tester,
    ) async {
      final DateTime start = DateTime(2026, 1, 1, 10);
      tester.pumpComponent(
        TimeseriesChart(
          series: <(String, List<double>)>[
            ('TPS', <double>[20, 19, 20, 18]),
          ],
          sampleTimestamps: <DateTime>[
            start,
            start.add(const Duration(minutes: 1)),
            start.add(const Duration(minutes: 2)),
            start.add(const Duration(minutes: 20)),
          ],
        ),
      );
      expect(find.tag('polyline'), findsNComponents(2));
      expect(find.text('10:00'), findsOneComponent);
      expect(find.text('10:20'), findsOneComponent);
    });

    testComponents('a constant series says its value instead of a line', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        TimeseriesChart(
          series: <(String, List<double>)>[
            ('Players', <double>[40, 40, 40]),
          ],
        ),
      );
      expect(find.tag('svg'), findsNothing);
      expect(find.text('Steady at 40'), findsOneComponent);
    });

    testComponents('one sample is not a line either', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        TimeseriesChart(
          series: <(String, List<double>)>[
            ('Tick time', <double>[12]),
          ],
          unit: 'ms',
        ),
      );
      expect(find.tag('svg'), findsNothing);
      expect(find.text('Steady at 12 ms'), findsOneComponent);
    });

    testComponents('two series get a plain legend, no buttons', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        TimeseriesChart(
          series: <(String, List<double>)>[
            ('cpu', <double>[1.0, 2.0, 3.0]),
            ('mem', <double>[4.0, 5.0, 6.0]),
          ],
        ),
      );
      expect(find.tag('polyline'), findsNComponents(2));
      expect(find.tag('li'), findsNComponents(2));
      expect(find.tag('button'), findsNothing);
      expect(_byClass('reactor-chart-series-1'), findsNComponents(2));
    });
  });
}
