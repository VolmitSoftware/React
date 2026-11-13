import 'package:react_web/model/alert_thresholds.dart';
import 'package:react_web/model/health.dart';
import 'package:react_web/model/reading.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:test/test.dart';

/// A sampler as the plugin sends it (suffix as written for the in-game
/// monitor; [SamplerSample.fromJson] strips the tag).
SamplerSample _plugin(
  String id,
  num value,
  String display,
  String suffix, {
  List<double> history = const <double>[],
}) => SamplerSample.fromJson(<String, dynamic>{
  'id': id,
  'name': id,
  'suffix': suffix,
  'value': value,
  'display': display,
}).withLiveHistory(history);

void main() {
  const AlertThresholds t = AlertThresholds.defaults;

  group('readingOf, real plugin shapes', () {
    test('sizes come from the bytes, not the in-game figure', () {
      final Reading used = readingOf(
        _plugin('memory-used', 3172391016, '3', 'GB'),
      );
      expect((used.value, used.unit), ('3.0', 'GB'));
      final Reading max = readingOf(
        _plugin('jvm-heap-max', 4294967296, '4', 'GB'),
      );
      expect(max.text, '4.0 GB');
      final Reading small = readingOf(
        _plugin('memory-free', 851443712, '812', 'MB'),
      );
      expect(small.text, '812 MB');
    });

    test('memory-pressure is an allocation rate in bytes per second', () {
      final Reading rate = readingOf(
        _plugin('memory-pressure', 83614412, '79.7', 'MB/s'),
        t,
      );
      expect(rate.text, '79.7 MB/s');
      // It has no line: a high rate is not a memory warning.
      expect(rate.level, HealthLevel.unknown);
    });

    test('a source that sends the figure in its unit scales up', () {
      expect(
        readingOf(_plugin('memory-used', 13357, '13357', 'MB')).text,
        '13.0 GB',
      );
    });

    test('in-game tags never show as units', () {
      expect(readingOf(_plugin('players', 14, '14', 'PLR')).text, '14');
      expect(
        readingOf(_plugin('entities', 2714, '2,714', 'ENT')).text,
        '2,714',
      );
      expect(
        readingOf(_plugin('processor-process-load', 2, '2%', 'pCPU')).text,
        '2%',
      );
      expect(
        readingOf(_plugin('gc-time-percent', 0.0115, '0.01', '% GC')).text,
        '0.01%',
      );
      expect(
        readingOf(_plugin('chunk-gen-listener-ms', 8.4, '8.4', 'ms CHGEN'))
            .text,
        '8.4 ms',
      );
      expect(
        readingOf(_plugin('chunks-generated', 12, '12', 'GEN/s')).text,
        '12 /s',
      );
    });

    test('heap use carries the memory state', () {
      expect(
        readingOf(_plugin('jvm-heap-utilization', 72.4, '72.4', '%'), t).level,
        HealthLevel.healthy,
      );
      expect(
        readingOf(_plugin('jvm-heap-utilization', 94.2, '94.2', '%'), t).level,
        HealthLevel.warning,
      );
    });

    test('the state compares the figure as shown', () {
      // 9.96 shows as 10.0: not under the critical line of 10.
      expect(
        readingOf(_plugin('ticks-per-second', 9.96, '10.0', 'TPS'), t).level,
        HealthLevel.warning,
      );
      expect(
        readingOf(_plugin('ticks-per-second', 9.94, '9.9', 'TPS'), t).level,
        HealthLevel.critical,
      );
    });

    test('no sample reads "--"', () {
      expect(readingOf(null).text, '--');
      expect(readingOf(null, t).level, HealthLevel.unknown);
    });
  });

  test('chart values put every size in the largest unit', () {
    const double gb = 1024.0 * 1024 * 1024;
    final ChartValues values = chartValuesOf(<SamplerSample?>[
      _plugin(
        'memory-used',
        3 * gb,
        '3',
        'GB',
        history: <double>[2 * gb, 3 * gb],
      ),
      _plugin('memory-free', 0.5 * gb, '512', 'MB', history: <double>[gb]),
      null,
    ]);
    expect(values.unit, 'GB');
    expect(values.series[0], <double>[2, 3]);
    expect(values.series[1], <double>[1]);
    expect(values.series[2], isEmpty);
  });

  test('chart values scale given values, such as a stored range', () {
    const double gb = 1024.0 * 1024 * 1024;
    final ChartValues values = chartValuesOf(
      <SamplerSample?>[
        _plugin('memory-used', 3 * gb, '3', 'GB', history: <double>[gb]),
        _plugin('memory-free', 0.5 * gb, '512', 'MB', history: <double>[gb]),
      ],
      values: <List<double>>[
        <double>[2 * gb, 4 * gb],
        <double>[gb / 2],
      ],
    );
    expect(values.unit, 'GB');
    expect(values.series[0], <double>[2, 4]);
    expect(values.series[1], <double>[0.5]);
  });

  test('chart values keep other units as sent', () {
    final ChartValues values = chartValuesOf(<SamplerSample?>[
      _plugin('tick-time', 23.4, '23.4', 'ms', history: <double>[20, 23.4]),
    ]);
    expect(values.unit, 'ms');
    expect(values.series.single, <double>[20, 23.4]);
  });
}
