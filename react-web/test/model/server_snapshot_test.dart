library;

import 'package:test/test.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';

void main() {
  group('ServerSnapshot', () {
    final Map<String, dynamic> payload = {
      'data': [
        {
          'id': 'cpu',
          'name': 'CPU Usage',
          'value': 45.0,
          'suffix': '%',
          'min': 0.0,
          'max': 100.0,
          'history': [40.0, 42.0, 45.0],
        },
        {
          'id': 'mem',
          'name': 'Memory',
          'value': 70.0,
          'suffix': 'MB',
          'min': 0.0,
          'max': 8192.0,
          'history': [68.0, 69.0, 70.0],
        },
      ],
    };

    test('fromJson maps both samplers by id', () {
      final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);
      expect(snapshot.byId.length, equals(2));
      expect(snapshot.byId.containsKey('cpu'), isTrue);
      expect(snapshot.byId.containsKey('mem'), isTrue);
    });

    test('sampler() lookup by id returns correct sample', () {
      final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);
      final SamplerSample? cpu = snapshot.sampler('cpu');
      expect(cpu, isNotNull);
      expect(cpu!.name, equals('CPU Usage'));
      expect(cpu.value, equals(45.0));
      expect(cpu.suffix, equals('%'));
      expect(cpu.min, equals(0.0));
      expect(cpu.max, equals(100.0));
    });

    test('sampler() returns null for unknown id', () {
      final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);
      expect(snapshot.sampler('unknown'), isNull);
    });

    test('value and history decode correctly', () {
      final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);
      final SamplerSample? cpu = snapshot.sampler('cpu');
      expect(cpu!.history, equals([40.0, 42.0, 45.0]));
      final SamplerSample? mem = snapshot.sampler('mem');
      expect(mem!.value, equals(70.0));
      expect(mem.history, equals([68.0, 69.0, 70.0]));
    });

    test('at field is a valid DateTime', () {
      final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);
      expect(snapshot.at, isA<DateTime>());
    });
  });

  group('SamplerSample', () {
    test('fromJson decodes all fields', () {
      final Map<String, dynamic> json = {
        'id': 'tps',
        'name': 'TPS',
        'value': 19.5,
        'suffix': ' tps',
        'min': 0.0,
        'max': 20.0,
        'history': [18.0, 19.0, 19.5],
      };
      final SamplerSample s = SamplerSample.fromJson(json);
      expect(s.id, equals('tps'));
      expect(s.name, equals('TPS'));
      expect(s.value, equals(19.5));
      expect(s.suffix, equals(' tps'));
      expect(s.min, equals(0.0));
      expect(s.max, equals(20.0));
      expect(s.history, equals([18.0, 19.0, 19.5]));
    });
  });
}
