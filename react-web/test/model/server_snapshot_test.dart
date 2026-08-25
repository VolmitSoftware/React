library;

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:test/test.dart';

void main() {
  final Map<String, dynamic> payload = <String, dynamic>{
    'data': <String, dynamic>{
      'sequence': 42,
      'capturedAtMs': 1750000000123,
      'samplers': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 'cpu',
          'name': 'CPU Usage',
          'value': 45.0,
          'suffix': '%',
          'display': '45%',
          'available': true,
        },
        <String, dynamic>{
          'id': 'mem',
          'name': 'Memory',
          'value': 70.0,
          'suffix': 'MB',
          'display': '70 MB',
          'available': false,
        },
      ],
    },
  };

  test('decodes the timestamped scalar snapshot contract', () {
    final ServerSnapshot snapshot = ServerSnapshot.fromJson(payload);

    expect(snapshot.seq, equals(42));
    expect(snapshot.at.millisecondsSinceEpoch, equals(1750000000123));
    expect(snapshot.byId.keys, containsAll(<String>['cpu', 'mem']));
    expect(snapshot.sampler('cpu')!.display, equals('45%'));
    expect(snapshot.sampler('mem'), isNull);
    expect(snapshot.byId['mem']!.available, isFalse);
    expect(snapshot.sampler('unknown'), isNull);
  });

  test('scalar samples start with a one-value range and no wire history', () {
    final Map<String, dynamic> data = payload['data'] as Map<String, dynamic>;
    final List<dynamic> samplers = data['samplers'] as List<dynamic>;
    final SamplerSample sample = SamplerSample.fromJson(
      samplers.first as Map<String, dynamic>,
    );

    expect(sample.min, equals(45.0));
    expect(sample.max, equals(45.0));
    expect(sample.history, isEmpty);
  });
}
