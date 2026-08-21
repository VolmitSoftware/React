library;

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

class SvgFallbackChart extends StatelessComponent {
  const SvgFallbackChart({required this.series, this.height = 160, super.key});

  final List<(String, List<double>)> series;
  final int height;

  @override
  Component build(BuildContext context) {
    return svg(
      [
        for (final (String _, List<double> values) in series)
          _polylineFor(values),
      ],
      viewBox: '0 0 100 100',
      styles: const Styles(
        raw: <String, String>{
          'width': '100%',
          'height': '100%',
          'display': 'block',
        },
      ),
      attributes: const <String, String>{'preserveAspectRatio': 'none'},
    );
  }

  Component _polylineFor(List<double> values) {
    if (values.isEmpty) {
      return polyline(const [], points: '');
    }

    double minVal = values[0];
    double maxVal = values[0];
    for (final double v in values) {
      if (v < minVal) minVal = v;
      if (v > maxVal) maxVal = v;
    }
    final double range = maxVal - minVal;
    final int n = values.length;

    final StringBuffer buf = StringBuffer();
    for (int i = 0; i < n; i++) {
      final double x = n == 1 ? 50.0 : (i / (n - 1)) * 100.0;
      final double y = range == 0.0
          ? 50.0
          : (1.0 - (values[i] - minVal) / range) * 100.0;
      if (i > 0) buf.write(' ');
      buf.write('${x.toStringAsFixed(2)},${y.toStringAsFixed(2)}');
    }

    return polyline(const [], points: buf.toString());
  }
}
