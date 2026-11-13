library;

import 'package:react_web/model/range_history.dart';
import 'package:test/test.dart';

const int _step = 10000;
const int _to = 1763000000000 ~/ _step * _step;
const int _from = _to - 360 * _step;

Map<String, dynamic> _response(Map<String, List<double?>> series) =>
    <String, dynamic>{
      'range': '1h',
      'stepMs': _step,
      'fromMs': _from,
      'toMs': _to,
      'series': <Map<String, dynamic>>[
        for (final MapEntry<String, List<double?>> entry in series.entries)
          <String, dynamic>{'id': entry.key, 'values': entry.value},
      ],
    };

List<double?> _filled(double? Function(int index) value) =>
    List<double?>.generate(360, value);

DateTime _at(int ms) => DateTime.fromMillisecondsSinceEpoch(ms);

void main() {
  group('ChartRange', () {
    test('has the four ranges with their buckets', () {
      expect(ChartRange.values.map((ChartRange range) => range.id), <String>[
        'live',
        '1h',
        '6h',
        '24h',
      ]);
      expect(ChartRange.hour.step, const Duration(seconds: 10));
      expect(ChartRange.sixHours.span, const Duration(hours: 6));
      expect(ChartRange.day.span, const Duration(hours: 24));
      expect(ChartRange.live.stored, isFalse);
      expect(ChartRange.byId('6h'), ChartRange.sixHours);
      expect(ChartRange.byId('7d'), isNull);
    });
  });

  group('RangeHistory', () {
    test('reads the server range: start, step and every bucket', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{
          'tick-time': _filled((int i) => i.isEven ? 10.0 + i : null),
        }),
      );
      expect(history.range, ChartRange.hour);
      expect(history.from, _at(_from));
      expect(history.to, _at(_to));
      final RangeView view = history.view(const <String>['tick-time']);
      expect(view.times, hasLength(180));
      expect(view.times.first, _at(_from));
      expect(view.times[1], _at(_from + 2 * _step));
      expect(view.values['tick-time']!.take(2), <double>[10, 12]);
    });

    test('live frames build the open bucket after the range', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      history.add(_at(_to + 1000), <String, double>{'tick-time': 20});
      history.add(_at(_to + 2000), <String, double>{'tick-time': 30});
      final RangeView view = history.view(const <String>['tick-time']);
      expect(view.times, hasLength(361));
      expect(view.times.last, _at(_to));
      expect(view.values['tick-time']!.last, 25);
    });

    test('a closed bucket keeps the window at 360 buckets', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{
          'tick-time': _filled((int i) => i.toDouble()),
        }),
      );
      history.add(_at(_to + 1000), <String, double>{'tick-time': 900});
      history.add(_at(_to + _step + 1000), <String, double>{'tick-time': 901});
      expect(history.from, _at(_from + _step));
      expect(history.to, _at(_to + _step));
      final List<double> values = history.view(const <String>[
        'tick-time',
      ]).values['tick-time']!;
      expect(values, hasLength(361));
      expect(values.first, 1);
      expect(values[359], 900);
      expect(values.last, 901);
      expect(history.gapped, isFalse);
    });

    test('frames from before the open bucket are left to the server', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      history.add(_at(_to - 1000), <String, double>{'tick-time': 99});
      expect(
        history.view(const <String>['tick-time']).values['tick-time']!,
        everyElement(5),
      );
    });

    test('a pause of two buckets or more is a gap the server can fill', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      history.add(_at(_to + 1000), <String, double>{'tick-time': 7});
      history.add(_at(_to + 3 * _step), <String, double>{'tick-time': 8});
      expect(history.gapped, isTrue);
      final RangeView view = history.view(const <String>['tick-time']);
      // 357 kept, the closed 7, two empty buckets, then the open 8.
      expect(view.times, hasLength(359));
      expect(view.values['tick-time']!.sublist(356), <double>[5, 7, 8]);
      expect(view.times.last, _at(_to + 3 * _step));
    });

    test('a long pause empties the window', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      history.add(_at(_to + 500 * _step), <String, double>{'tick-time': 8});
      expect(history.to, _at(_to + 500 * _step));
      final RangeView view = history.view(const <String>['tick-time']);
      expect(view.values['tick-time'], <double>[8]);
    });

    test('a chart shows the series that have data, where all of them do', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{
          'memory-used': _filled((int i) => 100.0 + i),
          'memory-free': _filled((int i) => i == 3 ? null : 50.0),
          'memory-used-after-gc': _filled((_) => null),
        }),
      );
      final RangeView view = history.view(const <String>[
        'memory-used',
        'memory-free',
        'memory-used-after-gc',
      ]);
      expect(view.values.keys, <String>['memory-used', 'memory-free']);
      expect(view.times, hasLength(359));
      expect(view.values['memory-used']![3], 104);
    });

    test('an unknown series from a live frame starts empty', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      history.add(_at(_to + 1000), <String, double>{'tick-ms-p95': 9});
      final RangeView view = history.view(const <String>['tick-ms-p95']);
      expect(view.times, <DateTime>[_at(_to)]);
      expect(view.values['tick-ms-p95'], <double>[9]);
    });

    test('nothing stored is an empty view', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => null)}),
      );
      expect(history.view(const <String>['tick-time']).isEmpty, isTrue);
    });

    test('the same view is returned until the history changes', () {
      final RangeHistory history = RangeHistory.fromJson(
        _response(<String, List<double?>>{'tick-time': _filled((_) => 5)}),
      );
      final RangeView first = history.view(const <String>['tick-time']);
      expect(history.view(const <String>['tick-time']), same(first));
      history.add(_at(_to + 1000), <String, double>{'tick-time': 6});
      expect(history.view(const <String>['tick-time']), isNot(same(first)));
    });
  });
}
