library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/chart/chart_timeline.dart';
import 'package:react_web/chart/range_chart.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/chart_history.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

import '../screen/monitor_support.dart';

const int _step = 10000;
const int _to = 1763000000000 ~/ _step * _step;
const int _from = _to - 360 * _step;

class _Client implements IHistoryClient {
  Object? failWith;
  Completer<void>? gate;
  double? Function(int index) values = (int index) => 10.0 + index % 7;

  @override
  Future<RangeHistory> metricRange(
    ChartRange range, {
    List<String>? ids,
  }) async {
    if (gate != null) await gate!.future;
    final Object? failure = failWith;
    if (failure != null) throw failure;
    return RangeHistory.fromJson(<String, dynamic>{
      'range': range.id,
      'stepMs': _step,
      'fromMs': _from,
      'toMs': _to,
      'series': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 'tick-time',
          'values': List<double?>.generate(360, values),
        },
      ],
    });
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

final SamplerSample _tick = sample(
  'tick-time',
  12,
  suffix: 'ms',
  history: <double>[11, 12, 14, 12],
);

Widget _card(ChartHistory? charts) => onServer(
  ServerScope(
    snapshot: snapshotOf(<SamplerSample>[_tick]),
    state: ConnState.live,
    charts: charts,
    child: RangeChartCard(
      title: 'Tick time',
      unit: 'ms',
      series: <RangeSeries>[RangeSeries('Tick time', 'tick-time', _tick)],
    ),
  ),
);

Future<ChartHistory> _picked(_Client client, ChartRange range) async {
  final ChartHistory charts = ChartHistory(
    client,
    const Stream<ServerSnapshot>.empty(),
  );
  addTearDown(charts.dispose);
  charts.select(range);
  await Future<void>.delayed(Duration.zero);
  return charts;
}

void main() {
  group('RangeChartCard', () {
    testServer('without stored history it is the live chart, no toggle', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_card(null));
      final String body = (await tester.request('/')).body;
      expect(body, contains('data-chart-card'));
      expect(body, contains('reactor-chart-svg'));
      expect(body, isNot(contains(ReactorText.chartRange.english)));
    });

    testServer('the toggle offers Live, 1h, 6h and 24h, Live first', (
      ServerTester tester,
    ) async {
      final ChartHistory charts = ChartHistory(
        _Client(),
        const Stream<ServerSnapshot>.empty(),
      );
      addTearDown(charts.dispose);
      tester.pumpComponent(_card(charts));
      final String body = (await tester.request('/')).body;
      expect(body, contains('aria-label="${ReactorText.chartRange.english}"'));
      final int live = body.indexOf('>${ReactorText.chartRangeLive.english}<');
      final int hour = body.indexOf('>1h<');
      final int six = body.indexOf('>6h<');
      final int day = body.indexOf('>24h<');
      expect(live, greaterThan(0));
      expect(<int>[
        live,
        hour,
        six,
        day,
      ], orderedEquals(<int>[live, hour, six, day]..sort()));
      expect(body, contains('reactor-chart-svg'));
    });

    testServer('a stored range draws its buckets on the time axis', (
      ServerTester tester,
    ) async {
      final ChartHistory charts = await _picked(_Client(), ChartRange.hour);
      tester.pumpComponent(_card(charts));
      final String body = (await tester.request('/')).body;
      final DateTime from = DateTime.fromMillisecondsSinceEpoch(_from);
      final DateTime last = DateTime.fromMillisecondsSinceEpoch(_to - _step);
      final Duration span = last.difference(from);
      expect(body, contains('reactor-chart-svg'));
      expect(body, contains('>${axisTime(from, span)}<'));
      expect(body, contains('>${axisTime(last, span)}<'));
    });

    testServer('loading keeps the chart height', (ServerTester tester) async {
      final _Client client = _Client()..gate = Completer<void>();
      final ChartHistory charts = await _picked(client, ChartRange.day);
      tester.pumpComponent(_card(charts));
      final String body = (await tester.request('/')).body;
      expect(body, contains(ReactorText.metricsLoadingHistory.english));
      expect(body, contains('160px'));
    });

    testServer('a failed range says why and offers a retry', (
      ServerTester tester,
    ) async {
      final _Client client = _Client()..failWith = StateError('down');
      final ChartHistory charts = await _picked(client, ChartRange.sixHours);
      tester.pumpComponent(_card(charts));
      final String body = (await tester.request('/')).body;
      expect(body, contains('data-alert="error"'));
      expect(body, contains('>${ReactorText.commonRetry.english}<'));
    });

    testServer('a range with nothing stored says so', (
      ServerTester tester,
    ) async {
      final _Client client = _Client()..values = (int index) => null;
      final ChartHistory charts = await _picked(client, ChartRange.hour);
      tester.pumpComponent(_card(charts));
      final String body = (await tester.request('/')).body;
      expect(body, contains(ReactorText.metricsNoSamples.english));
      expect(body, isNot(contains('reactor-chart-svg')));
    });
  });
}
