library;

class ChartTimeline {
  final List<double> positions;
  final Set<int> breakBefore;

  const ChartTimeline({required this.positions, required this.breakBefore});

  factory ChartTimeline.fromTimestamps(List<DateTime> timestamps) {
    if (timestamps.isEmpty) {
      return const ChartTimeline(positions: <double>[], breakBefore: <int>{});
    }
    if (timestamps.length == 1) {
      return const ChartTimeline(
        positions: <double>[0.5],
        breakBefore: <int>{},
      );
    }

    final int first = timestamps.first.millisecondsSinceEpoch;
    final int last = timestamps.last.millisecondsSinceEpoch;
    final int span = last - first;
    final List<double> positions = span <= 0
        ? List<double>.generate(
            timestamps.length,
            (int index) => index / (timestamps.length - 1),
            growable: false,
          )
        : timestamps
              .map(
                (DateTime timestamp) =>
                    (timestamp.millisecondsSinceEpoch - first) / span,
              )
              .toList(growable: false);

    final List<int> intervals = <int>[];
    for (int index = 1; index < timestamps.length; index++) {
      final int interval =
          timestamps[index].millisecondsSinceEpoch -
          timestamps[index - 1].millisecondsSinceEpoch;
      if (interval > 0) intervals.add(interval);
    }
    intervals.sort();
    if (intervals.isEmpty) {
      return ChartTimeline(positions: positions, breakBefore: const <int>{});
    }

    final int typicalInterval = intervals[intervals.length ~/ 2];
    final Set<int> breakBefore = <int>{};
    for (int index = 1; index < timestamps.length; index++) {
      final int interval =
          timestamps[index].millisecondsSinceEpoch -
          timestamps[index - 1].millisecondsSinceEpoch;
      if (interval > typicalInterval * 5 ~/ 2) breakBefore.add(index);
    }
    return ChartTimeline(positions: positions, breakBefore: breakBefore);
  }
}

String _two(int value) => value.toString().padLeft(2, '0');

/// [time] on a time axis spanning [span]: seconds for a few minutes, the
/// date for more than a day.
String axisTime(DateTime time, Duration span) {
  final DateTime local = time.toLocal();
  final String clock = '${_two(local.hour)}:${_two(local.minute)}';
  if (span > const Duration(days: 1)) {
    return '${_two(local.month)}-${_two(local.day)} $clock';
  }
  if (span <= const Duration(minutes: 10)) {
    return '$clock:${_two(local.second)}';
  }
  return clock;
}

/// The indexes of [count] samples the time axis names: the first, the last
/// and one in the middle (two for four labels).
List<int> axisSamples(int count, {int labels = 3}) {
  if (count <= 0) return const <int>[];
  if (count == 1) return const <int>[0];
  final int shown = labels.clamp(2, count);
  return <int>[
    for (int index = 0; index < shown; index++)
      (index * (count - 1) / (shown - 1)).round(),
  ];
}
