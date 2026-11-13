library;

import 'package:test/test.dart';

import 'package:react_web/model/metric_history.dart';

MetricHistoryDescriptor entry(
  String id, {
  bool available = true,
  int last = 5,
}) => MetricHistoryDescriptor.fromJson(<String, dynamic>{
  'id': id,
  'name': id,
  'suffix': '',
  'firstTimestampMs': last == 0 ? 0 : 1,
  'lastTimestampMs': last,
  'active': true,
  'available': available,
});

void main() {
  test('the default skips unavailable and never-sampled entries', () {
    final List<MetricHistoryDescriptor> catalog = <MetricHistoryDescriptor>[
      entry('adapt-ability-ops', available: false),
      entry('iris-pregen-queue', last: 0),
      entry('tick-time'),
    ];
    expect(MetricHistoryDescriptor.preferredDefault(catalog)!.id, 'tick-time');
    expect(catalog[1].hasSamples, isFalse);
  });

  test('falls back to the first entry, and to null when empty', () {
    expect(
      MetricHistoryDescriptor.preferredDefault(<MetricHistoryDescriptor>[
        entry('a', available: false),
      ])!.id,
      'a',
    );
    expect(
      MetricHistoryDescriptor.preferredDefault(<MetricHistoryDescriptor>[]),
      isNull,
    );
  });
}
