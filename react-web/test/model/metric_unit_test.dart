import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/metric_unit.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:test/test.dart';

void main() {
  group('metricUnit', () {
    test('keeps real units', () {
      for (final String unit in <String>[
        'ms',
        'GB',
        'MB',
        'TB',
        '%',
        'MB/s',
        'KB/s',
        'ms/s',
        '/s',
        '/min',
        'operations/s',
        'packets/s',
        'threads',
        'Bytes',
        'count',
      ]) {
        expect(metricUnit(unit), unit, reason: unit);
      }
    });

    test('drops the in-game tag after a unit', () {
      expect(metricUnit('ms CHGEN'), 'ms');
      expect(metricUnit('ms EVENT TIME'), 'ms');
      expect(metricUnit('ms GCP95'), 'ms');
      expect(metricUnit('% GC'), '%');
      expect(metricUnit(' tickets'), 'tickets');
    });

    test('drops a tag that only names the metric', () {
      for (final String tag in <String>[
        'TPS',
        'PLR',
        'ENT',
        'CHK',
        'INCIDENT',
        'AI',
        'LISTENERS',
        'pCPU',
        'xCPU',
        ' SAVE GAP',
        '',
      ]) {
        expect(metricUnit(tag), '', reason: tag);
      }
    });

    test('keeps the rate of a tagged rate', () {
      expect(metricUnit('GEN/s'), '/s');
      expect(metricUnit('SPAWN/s'), '/s');
      expect(metricUnit('EVENT/t'), '/t');
      expect(metricUnit('SPIKE/m'), '/m');
    });

    test('is stable when applied twice', () {
      for (final String suffix in <String>[
        'ms PWT',
        'GEN/s',
        'PLR',
        'MB/s',
        '% GC',
      ]) {
        expect(metricUnit(metricUnit(suffix)), metricUnit(suffix));
      }
    });
  });

  test('parsed samplers and history carry the unit', () {
    final SamplerSample players = SamplerSample.fromJson(<String, dynamic>{
      'id': 'players',
      'name': 'Players',
      'suffix': 'PLR',
      'value': 14,
      'display': '14',
    });
    expect(players.suffix, '');
    final MetricHistorySeries series = MetricHistorySeries.fromJson(
      <String, dynamic>{
        'id': 'chunk-gen-listener-ms',
        'name': 'Chunk gen',
        'suffix': 'ms CHGEN',
        'points': <dynamic>[],
      },
    );
    expect(series.suffix, 'ms');
  });
}
