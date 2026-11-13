/// The value axis of a chart: from zero when the readings sit near zero,
/// else padded round bounds with a minimum span, so a small wobble far from
/// zero (66.5 to 67 ms) draws as the near-flat line it is, not as a climb.
library;

import 'dart:math' as math;

/// The span a series far from zero gets at least, as a share of its size.
const double kMinimumSpanShare = 0.2;

class ChartScale {
  const ChartScale({
    required this.min,
    required this.max,
    required this.ticks,
    required this.decimals,
  });

  final double min;
  final double max;

  /// Evenly spaced values from [min] to [max], both included.
  final List<double> ticks;

  /// Digits after the point every tick label uses.
  final int decimals;

  /// [value] with this axis's precision.
  String label(double value) => value.toStringAsFixed(decimals);

  /// Where [value] sits, 0 at [min] to 1 at [max].
  double fraction(double value) =>
      max == min ? 0.5 : ((value - min) / (max - min)).clamp(0.0, 1.0);
}

/// Fewer than two readings, or all the same: nothing to draw a line for.
bool isFlat(List<double> values) {
  if (values.length < 2) return true;
  final double first = values.first;
  for (final double value in values) {
    if ((value - first).abs() > 1e-9) return false;
  }
  return true;
}

/// A round step at least [raw]: 1, 2, 2.5 or 5 times a power of ten.
double _niceStep(double raw) {
  if (raw <= 0 || !raw.isFinite) return 1;
  final double power = math
      .pow(10, (math.log(raw) / math.ln10).floor())
      .toDouble();
  for (final double factor in const <double>[1, 2, 2.5, 5, 10]) {
    if (factor * power >= raw - 1e-12) return factor * power;
  }
  return 10 * power;
}

ChartScale chartScale(Iterable<double> values) {
  double? lo;
  double? hi;
  for (final double value in values) {
    if (!value.isFinite) continue;
    lo = lo == null || value < lo ? value : lo;
    hi = hi == null || value > hi ? value : hi;
  }
  if (lo == null || hi == null) {
    return const ChartScale(
      min: 0,
      max: 1,
      ticks: <double>[0, 0.5, 1],
      decimals: 1,
    );
  }
  double from;
  double to;
  if (lo >= 0 && hi > 0 && lo <= hi * 0.5) {
    from = 0;
    to = hi;
  } else {
    final double size = math.max(lo.abs(), hi.abs());
    final double span = math.max(
      hi - lo,
      size == 0 ? 1 : size * kMinimumSpanShare,
    );
    final double middle = (hi + lo) / 2;
    from = middle - span / 2;
    to = middle + span / 2;
    if (lo >= 0 && from < 0) {
      to -= from;
      from = 0;
    }
  }
  final double step = _niceStep((to - from) / 4);
  final double min = (from / step).floorToDouble() * step;
  final double max = math.max((to / step).ceilToDouble() * step, min + step);
  final int count = ((max - min) / step).round();
  final List<double> ticks = <double>[
    for (int index = 0; index <= count; index++) min + index * step,
  ];
  return ChartScale(
    min: min,
    max: max,
    ticks: ticks,
    decimals: _decimalsOf(step),
  );
}

/// The fewest digits that write [step] exactly (2.5 needs 1, 5 needs 0).
int _decimalsOf(double step) {
  const int most = 3;
  for (int digits = 0; digits <= most; digits++) {
    if ((double.parse(step.toStringAsFixed(digits)) - step).abs() < 1e-9) {
      return digits;
    }
  }
  return most;
}
