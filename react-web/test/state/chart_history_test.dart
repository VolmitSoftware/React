library;

import 'dart:async';

import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/chart_history.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:test/test.dart';

const int _to = 1763000000000 ~/ 10000 * 10000;

RangeHistory _range(ChartRange range, double value) =>
    RangeHistory.fromJson(<String, dynamic>{
      'range': range.id,
      'stepMs': range.step.inMilliseconds,
      'fromMs': _to - ChartRange.buckets * range.step.inMilliseconds,
      'toMs': _to,
      'series': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 'tick-time',
          'values': List<double>.filled(ChartRange.buckets, value),
        },
      ],
    });

class _Client implements IHistoryClient {
  final List<ChartRange> asked = <ChartRange>[];
  final List<List<String>?> idsAsked = <List<String>?>[];
  Object? failWith;
  double value = 5;
  Completer<void>? gate;

  @override
  Future<RangeHistory> metricRange(
    ChartRange range, {
    List<String>? ids,
  }) async {
    asked.add(range);
    idsAsked.add(ids);
    if (gate != null) await gate!.future;
    final Object? failure = failWith;
    if (failure != null) throw failure;
    return _range(range, value);
  }

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      const <MetricHistoryDescriptor>[];

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) => throw UnimplementedError();
}

ServerSnapshot _frame(int atMs, double tickTime) => ServerSnapshot(
  byId: <String, SamplerSample>{
    'tick-time': SamplerSample(
      id: 'tick-time',
      name: 'Tick Time',
      suffix: 'ms',
      value: tickTime,
      display: '$tickTime',
      min: tickTime,
      max: tickTime,
      history: const <double>[],
    ),
  },
  at: DateTime.fromMillisecondsSinceEpoch(atMs),
);

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  late _Client client;
  late StreamController<ServerSnapshot> frames;
  late ChartHistory charts;

  setUp(() {
    client = _Client();
    frames = StreamController<ServerSnapshot>.broadcast(sync: true);
    charts = ChartHistory(client, frames.stream);
  });

  tearDown(() async {
    charts.dispose();
    await frames.close();
  });

  test('starts live and asks for nothing', () async {
    await _settle();
    expect(charts.range, ChartRange.live);
    expect(charts.current, isNull);
    expect(client.asked, isEmpty);
  });

  test('a stored range loads once, in one request for every chart', () async {
    int notified = 0;
    charts.addListener(() => notified++);
    charts.select(ChartRange.hour);
    expect(charts.loading, isTrue);
    await _settle();
    expect(charts.loading, isFalse);
    expect(charts.current!.range, ChartRange.hour);
    expect(client.asked, <ChartRange>[ChartRange.hour]);
    expect(client.idsAsked.single, kChartSamplerIds);
    expect(notified, greaterThanOrEqualTo(2));

    charts.select(ChartRange.live);
    charts.select(ChartRange.hour);
    await _settle();
    expect(client.asked, <ChartRange>[ChartRange.hour]);
  });

  test('live frames extend every loaded range', () async {
    charts.select(ChartRange.hour);
    await _settle();
    charts.select(ChartRange.day);
    await _settle();
    frames.add(_frame(_to + 1000, 40));
    expect(
      charts.current!
          .view(const <String>['tick-time'])
          .values['tick-time']!
          .last,
      40,
    );
    charts.select(ChartRange.hour);
    expect(
      charts.current!
          .view(const <String>['tick-time'])
          .values['tick-time']!
          .last,
      40,
    );
    expect(client.asked, <ChartRange>[ChartRange.hour, ChartRange.day]);
  });

  test('a failed load shows the error until a retry works', () async {
    client.failWith = StateError('offline');
    charts.select(ChartRange.sixHours);
    await _settle();
    expect(charts.error, isA<StateError>());
    expect(charts.loading, isFalse);
    expect(charts.current, isNull);

    client.failWith = null;
    charts.retry();
    expect(charts.loading, isTrue);
    await _settle();
    expect(charts.error, isNull);
    expect(charts.current!.range, ChartRange.sixHours);
  });

  test('a gap in the live frames reloads the range on show', () async {
    charts.select(ChartRange.hour);
    await _settle();
    client.value = 9;
    frames.add(_frame(_to + 1000, 7));
    frames.add(_frame(_to + 60000, 8));
    await _settle();
    expect(client.asked, <ChartRange>[ChartRange.hour, ChartRange.hour]);
    expect(
      charts.current!
          .view(const <String>['tick-time'])
          .values['tick-time']!
          .first,
      9,
    );
  });

  test('a gap in a hidden range drops it until it is shown again', () async {
    charts.select(ChartRange.hour);
    await _settle();
    charts.select(ChartRange.live);
    frames.add(_frame(_to + 1000, 7));
    frames.add(_frame(_to + 60000, 8));
    await _settle();
    expect(client.asked, <ChartRange>[ChartRange.hour]);
    charts.select(ChartRange.hour);
    await _settle();
    expect(client.asked, <ChartRange>[ChartRange.hour, ChartRange.hour]);
  });

  test(
    'a reply for an earlier pick does not replace the current one',
    () async {
      client.gate = Completer<void>();
      charts.select(ChartRange.hour);
      charts.select(ChartRange.day);
      client.gate!.complete();
      await _settle();
      expect(charts.range, ChartRange.day);
      expect(charts.current!.range, ChartRange.day);
    },
  );

  test('a pick is remembered and the next session opens on it', () async {
    final InMemoryFleetStorage storage = InMemoryFleetStorage();
    final ChartHistory first = ChartHistory(
      client,
      frames.stream,
      storage: storage,
    );
    first.select(ChartRange.sixHours);
    await _settle();
    first.dispose();
    expect(storage.read(chartRangeStorageKey), '6h');

    final ChartHistory next = ChartHistory(
      client,
      frames.stream,
      storage: storage,
    );
    addTearDown(next.dispose);
    expect(next.range, ChartRange.sixHours);
    expect(next.loading, isTrue);
    await _settle();
    expect(next.current!.range, ChartRange.sixHours);
  });

  test('an unknown remembered range opens on Live', () {
    final InMemoryFleetStorage storage = InMemoryFleetStorage()
      ..write(chartRangeStorageKey, '7d');
    final ChartHistory other = ChartHistory(
      client,
      frames.stream,
      storage: storage,
    );
    addTearDown(other.dispose);
    expect(other.range, ChartRange.live);
  });

  test('nothing is asked after dispose', () async {
    final ChartHistory other = ChartHistory(client, frames.stream);
    other.dispose();
    frames.add(_frame(_to + 1000, 7));
    await _settle();
    expect(client.asked, isEmpty);
  });
}
