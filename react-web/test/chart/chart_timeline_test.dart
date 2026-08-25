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
}
