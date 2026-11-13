library;

import 'package:react_web/chart/chart_timeline.dart';
import 'package:test/test.dart';

void main() {
  test('positions samples by wall-clock time', () {
    final DateTime start = DateTime.utc(2026, 1, 1);
    final ChartTimeline timeline = ChartTimeline.fromTimestamps(<DateTime>[
      start,
      start.add(const Duration(minutes: 1)),
      start.add(const Duration(minutes: 4)),
    ]);

    expect(timeline.positions, <double>[0, 0.25, 1]);
  });

  test('breaks the line across missing time ranges', () {
    final DateTime start = DateTime.utc(2026, 1, 1);
    final ChartTimeline timeline = ChartTimeline.fromTimestamps(<DateTime>[
      start,
      start.add(const Duration(minutes: 1)),
      start.add(const Duration(minutes: 2)),
      start.add(const Duration(minutes: 20)),
    ]);

    expect(timeline.breakBefore, <int>{3});
  });

  test('keeps duplicate timestamps finite', () {
    final DateTime timestamp = DateTime.utc(2026, 1, 1);
    final ChartTimeline timeline = ChartTimeline.fromTimestamps(<DateTime>[
      timestamp,
      timestamp,
      timestamp,
    ]);

    expect(timeline.positions, <double>[0, 0.5, 1]);
    expect(timeline.breakBefore, isEmpty);
  });

  test('the time axis names the first, middle and last samples', () {
    expect(axisSamples(128), <int>[0, 64, 127]);
    expect(axisSamples(2), <int>[0, 1]);
    expect(axisSamples(1), <int>[0]);
  });

  test('axis times show seconds for minutes and the date past a day', () {
    final DateTime time = DateTime(2026, 11, 13, 14, 2, 9);
    expect(axisTime(time, const Duration(minutes: 2)), '14:02:09');
    expect(axisTime(time, const Duration(hours: 3)), '14:02');
    expect(axisTime(time, const Duration(days: 7)), '11-13 14:02');
  });
}
