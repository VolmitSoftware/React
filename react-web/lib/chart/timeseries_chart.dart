library;

import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart';

import 'svg_fallback_chart.dart';

class TimeseriesChart extends StatefulComponent {
  const TimeseriesChart({required this.series, this.height = 160, super.key});

  final List<(String, List<double>)> series;
  final int height;

  @override
  State<TimeseriesChart> createState() => _TimeseriesChartState();
}

class _TimeseriesChartState extends State<TimeseriesChart> {
  @override
  Component build(BuildContext context) {
    final String hostHeight = '${component.height}px';
    return dom.div(
      styles: dom.Styles(
        raw: <String, String>{'width': '100%', 'height': hostHeight},
      ),
      <Component>[
        SvgFallbackChart(series: component.series, height: component.height),
      ],
    );
  }
}
