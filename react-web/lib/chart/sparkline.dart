/// A small trend line under a number: no axes, no labels, decorative (the
/// number beside it says the value). Scales to its box width. Like the
/// charts, a small wobble far from zero keeps a minimum span
/// ([kMinimumSpanShare]), so it does not read as a swing.
library;

import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import 'chart_scale.dart';

class Sparkline extends StatelessWidget {
  const Sparkline({
    required this.values,
    this.color = 'var(--muted-foreground)',
    this.height = 28,
    super.key,
  });

  final List<double> values;

  /// A CSS colour, a token (`var(--success)`).
  final String color;
  final int height;

  /// The SVG path of [values] in a 100 x 100 box with 6 units of padding.
  static String pathOf(List<double> values) {
    double lo = values.first;
    double hi = values.first;
    for (final double value in values) {
      if (value < lo) lo = value;
      if (value > hi) hi = value;
    }
    final double size = math.max(lo.abs(), hi.abs());
    final double span = math.max(
      hi - lo,
      size < 1e-9 ? 1.0 : size * kMinimumSpanShare,
    );
    final double floor = (hi + lo) / 2 - span / 2;
    const double pad = 6.0;
    final double step = 100.0 / (values.length - 1);
    final StringBuffer line = StringBuffer();
    for (int i = 0; i < values.length; i++) {
      final double y =
          100.0 - pad - (values[i] - floor) / span * (100.0 - pad * 2);
      line.write(i == 0 ? 'M ' : 'L ');
      line.write('${(i * step).toStringAsFixed(2)} ${y.toStringAsFixed(2)} ');
    }
    return line.toString().trim();
  }

  @override
  Widget build(BuildContext context) {
    return Component.element(
      tag: 'svg',
      classes: 'reactor-sparkline',
      attributes: <String, String>{
        'width': '100%',
        'height': '$height',
        'viewBox': '0 0 100 100',
        'preserveAspectRatio': 'none',
        'aria-hidden': 'true',
      },
      children: <Component>[
        // Fewer than two values hold the space without a line.
        if (values.length >= 2)
          Component.element(
            tag: 'path',
            attributes: <String, String>{
              'd': pathOf(values),
              'fill': 'none',
              'stroke': color,
              'stroke-width': '1.5',
              'stroke-linejoin': 'round',
              'vector-effect': 'non-scaling-stroke',
            },
          ),
      ],
    );
  }
}
