library;

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

class SvgFallbackChart extends StatelessComponent {
  const SvgFallbackChart({required this.series, this.height = 160, super.key});

  final List<(String, List<double>)> series;
  final int height;

  @override
  Component build(BuildContext context) {
    final (double, double) bounds = _globalBounds();
    final String accessibleLabel = series.isEmpty
        ? 'No time series data'
        : 'Time series chart: ${series.map(((String, List<double>) item) => item.$1).join(', ')}';
    return div(classes: 'reactor-chart-plot', <Component>[
      span(classes: 'reactor-chart-scale is-max', <Component>[
        Component.text(_formatBound(bounds.$2)),
      ]),
      svg(
        <Component>[
          for (int index = 0; index < 5; index++)
            line(
              const <Component>[],
              x1: '0',
              y1: '${6 + index * 22}',
              x2: '100',
              y2: '${6 + index * 22}',
              classes: 'reactor-chart-grid-line',
              attributes: const <String, String>{
                'vector-effect': 'non-scaling-stroke',
              },
            ),
          for (int index = 0; index < series.length; index++)
            _polylineFor(series[index].$2, bounds, index),
        ],
        viewBox: '0 0 100 100',
        classes: 'reactor-chart-svg',
        styles: Styles(
          raw: <String, String>{
            'width': '100%',
            'height': '${height}px',
            'display': 'block',
          },
        ),
        attributes: <String, String>{
          'preserveAspectRatio': 'none',
          'role': 'img',
          'aria-label': accessibleLabel,
        },
      ),
      span(classes: 'reactor-chart-scale is-min', <Component>[
        Component.text(_formatBound(bounds.$1)),
      ]),
    ]);
  }

  (double, double) _globalBounds() {
    double? minimum;
    double? maximum;
    for (final (String _, List<double> values) in series) {
      for (final double value in values) {
        minimum = minimum == null || value < minimum ? value : minimum;
        maximum = maximum == null || value > maximum ? value : maximum;
      }
    }
    if (minimum == null || maximum == null) return (0.0, 1.0);
    if ((maximum - minimum).abs() < 1e-9) {
      final double padding = maximum.abs() < 1.0 ? 1.0 : maximum.abs() * 0.05;
      return (minimum - padding, maximum + padding);
    }
    final double padding = (maximum - minimum) * 0.05;
    return (minimum - padding, maximum + padding);
  }

  Component _polylineFor(
    List<double> values,
    (double, double) bounds,
    int index,
  ) {
    if (values.isEmpty) {
      return polyline(
        const <Component>[],
        points: '',
        classes: 'reactor-chart-series reactor-chart-series-$index',
        attributes: <String, String>{
          'fill': 'none',
          'stroke': 'var(--reactor-chart-${(index % 6) + 1})',
        },
      );
    }
    final double minimum = bounds.$1;
    final double range = bounds.$2 - bounds.$1;
    final int count = values.length;
    final StringBuffer points = StringBuffer();
    for (int pointIndex = 0; pointIndex < count; pointIndex++) {
      final double x = count == 1
          ? 50.0
          : 2.0 + (pointIndex / (count - 1)) * 96.0;
      final double y = 94.0 - ((values[pointIndex] - minimum) / range) * 88.0;
      if (pointIndex > 0) points.write(' ');
      points.write('${x.toStringAsFixed(2)},${y.toStringAsFixed(2)}');
    }
    return polyline(
      const <Component>[],
      points: points.toString(),
      classes: 'reactor-chart-series reactor-chart-series-$index',
      attributes: <String, String>{
        'fill': 'none',
        'stroke': 'var(--reactor-chart-${(index % 6) + 1})',
        'stroke-width': '1.5',
        'stroke-linecap': 'square',
        'stroke-linejoin': 'miter',
        'vector-effect': 'non-scaling-stroke',
      },
    );
  }

  String _formatBound(double value) {
    if (value.abs() >= 1000.0) return value.toStringAsFixed(0);
    if (value.abs() >= 100.0) return value.toStringAsFixed(1);
    return value.toStringAsFixed(2);
  }
}
