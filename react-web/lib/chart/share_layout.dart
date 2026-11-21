/// The geometry of the share charts: the biggest parts of a whole with the
/// rest as "Other", their donut arcs, and the segments of a stacked bar.
/// Pure, so the math is unit tested.
library;

import 'dart:math' as math;

/// How many series colours the charts have (`reactor-chart-series-N`); the
/// last one is the muted "Other".
const int kShareColors = 6;

/// One part of a whole.
class ShareItem {
  const ShareItem(this.id, this.label, this.value);

  final String id;
  final String label;
  final double value;
}

/// One slice: [start] and [end] are fractions of the turn (0 to 1), [color]
/// the series index. [id] is null for "Other".
class ShareSlice {
  const ShareSlice({
    required this.id,
    required this.label,
    required this.value,
    required this.start,
    required this.end,
    required this.color,
  });

  final String? id;
  final String label;
  final double value;
  final double start;
  final double end;
  final int color;

  double get share => end - start;
  bool get isOther => id == null;
}

int _hash(String id) {
  int hash = 0;
  for (final int unit in id.codeUnits) {
    hash = (hash * 31 + unit) & 0x7fffffff;
  }
  return hash;
}

/// The [top] biggest positive [items] as slices, biggest first, and the
/// rest summed into one [otherLabel] slice at the end. Each named slice
/// keeps the colour its id hashes to unless a bigger slice took it, so an
/// owner tends to keep its colour while the ranking moves.
List<ShareSlice> shareSlices(
  List<ShareItem> items, {
  int top = kShareColors - 1,
  required String otherLabel,
}) {
  final List<ShareItem> positive = <ShareItem>[
    for (final ShareItem item in items)
      if (item.value > 0 && item.value.isFinite) item,
  ]..sort((ShareItem a, ShareItem b) => b.value.compareTo(a.value));
  final double whole = positive.fold(
    0,
    (double sum, ShareItem item) => sum + item.value,
  );
  if (whole <= 0) return const <ShareSlice>[];
  final int named = math.min(
    positive.length <= top + 1 ? positive.length : top,
    kShareColors - 1,
  );
  final Set<int> taken = <int>{};
  final List<ShareSlice> slices = <ShareSlice>[];
  double cursor = 0;
  for (final ShareItem item in positive.take(named)) {
    int color = _hash(item.id) % (kShareColors - 1);
    while (taken.contains(color)) {
      color = (color + 1) % (kShareColors - 1);
    }
    taken.add(color);
    final double end = cursor + item.value / whole;
    slices.add(
      ShareSlice(
        id: item.id,
        label: item.label,
        value: item.value,
        start: cursor,
        end: end,
        color: color,
      ),
    );
    cursor = end;
  }
  final double rest = positive
      .skip(named)
      .fold(0, (double sum, ShareItem item) => sum + item.value);
  if (rest > 0) {
    slices.add(
      ShareSlice(
        id: null,
        label: otherLabel,
        value: rest,
        start: cursor,
        end: 1,
        color: kShareColors - 1,
      ),
    );
  }
  return slices;
}

String _point(double turn, double radius) {
  final double angle = turn * 2 * math.pi - math.pi / 2;
  final double x = 50 + radius * math.cos(angle);
  final double y = 50 + radius * math.sin(angle);
  return '${x.toStringAsFixed(3)} ${y.toStringAsFixed(3)}';
}

/// The SVG path of a ring segment from [start] to [end] (fractions of the
/// turn, clockwise from the top) in a 100 x 100 box: [outer] and [inner]
/// radii. A whole turn is drawn as two halves.
String donutArcPath(
  double start,
  double end, {
  double outer = 48,
  double inner = 30,
}) {
  final double span = (end - start).clamp(0, 1).toDouble();
  if (span <= 0) return '';
  if (span >= 0.9999) {
    return '${donutArcPath(0, 0.5, outer: outer, inner: inner)} '
        '${donutArcPath(0.5, 1, outer: outer, inner: inner)}';
  }
  final int large = span > 0.5 ? 1 : 0;
  return 'M ${_point(start, outer)} '
      'A $outer $outer 0 $large 1 ${_point(end, outer)} '
      'L ${_point(end, inner)} '
      'A $inner $inner 0 $large 0 ${_point(start, inner)} Z';
}

/// One segment of a stacked bar: [x] and [width] are fractions of the bar.
class BarSegment {
  const BarSegment(this.key, this.value, this.x, this.width);

  final String key;
  final double value;
  final double x;
  final double width;
}

/// [values] laid end to end in [order] (keys missing from it follow,
/// biggest first) on a bar where [scale] is the full width; zero and
/// negative values are left out.
List<BarSegment> stackSegments(
  Map<String, double> values, {
  required List<String> order,
  required double scale,
}) {
  if (scale <= 0) return const <BarSegment>[];
  final List<String> keys = <String>[
    for (final String key in order)
      if (values.containsKey(key)) key,
    ...(values.keys.where((String key) => !order.contains(key)).toList()
      ..sort((String a, String b) => values[b]!.compareTo(values[a]!))),
  ];
  final List<BarSegment> segments = <BarSegment>[];
  double cursor = 0;
  for (final String key in keys) {
    final double value = values[key]!;
    if (value <= 0 || !value.isFinite) continue;
    final double width = value / scale;
    segments.add(BarSegment(key, value, cursor, width));
    cursor += width;
  }
  return segments;
}

/// Every positive one of [items] as a slice, in their order, coloured by
/// position; for a few fixed parts (kinds of work) that each have a colour.
List<ShareSlice> orderedSlices(List<ShareItem> items) {
  final List<ShareItem> positive = <ShareItem>[
    for (final ShareItem item in items)
      if (item.value > 0 && item.value.isFinite) item,
  ];
  final double whole = positive.fold(
    0,
    (double sum, ShareItem item) => sum + item.value,
  );
  if (whole <= 0) return const <ShareSlice>[];
  final List<ShareSlice> slices = <ShareSlice>[];
  double cursor = 0;
  for (final (int index, ShareItem item) in positive.indexed) {
    final double end = index == positive.length - 1
        ? 1
        : cursor + item.value / whole;
    slices.add(
      ShareSlice(
        id: item.id,
        label: item.label,
        value: item.value,
        start: cursor,
        end: end,
        color: index % kShareColors,
      ),
    );
    cursor = end;
  }
  return slices;
}
