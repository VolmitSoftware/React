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
