library;

import 'package:jaspr/jaspr.dart' show Component, DomComponent;
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/metrics_explorer.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

/// The plugin's catalog entry for a sampler that never stored anything.
MetricHistoryDescriptor _empty(String id) => MetricHistoryDescriptor(
  id: id,
  name: id,
  suffix: '',
  firstAt: DateTime.fromMillisecondsSinceEpoch(0),
  lastAt: DateTime.fromMillisecondsSinceEpoch(0),
  active: false,
  available: false,
);

MetricHistoryDescriptor _stored(String id, String name) =>
    MetricHistoryDescriptor(
      id: id,
      name: name,
      suffix: '',
      firstAt: DateTime(2026, 11),
      lastAt: DateTime(2026, 11, 12),
      active: true,
    );

class _History implements IHistoryClient {
  _History(this.catalog);

  final List<MetricHistoryDescriptor> catalog;
  final List<String> asked = <String>[];

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      List<MetricHistoryDescriptor>.of(catalog);

  @override
  Future<RangeHistory> metricRange(ChartRange range, {List<String>? ids}) =>
      throw UnimplementedError();

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) async {
    asked.addAll(ids ?? const <String>[]);
    final DateTime at = DateTime(2026, 11, 12, 12);
    return MetricHistoryPage(
      requestedFrom: at,
      requestedTo: at,
      pageFrom: at,
      pageTo: at,
      resolution: const Duration(seconds: 10),
      throughSequence: 1,
      throughAt: at,
      nextCursor: null,
      series: <MetricHistorySeries>[
        MetricHistorySeries(
          id: ids!.first,
          name: ids.first,
          suffix: '',
          points: <MetricHistoryPoint>[
            for (int i = 0; i < 3; i++)
              MetricHistoryPoint(
                at: at.add(Duration(seconds: i * 10)),
                average: 19,
                minimum: 18,
                maximum: 20,
                last: 19,
                count: 10,
              ),
          ],
        ),
      ],
    );
  }
}

ServerSnapshot _live() => snapshotOf(<SamplerSample>[
  sample('ticks-per-second', 19.9, name: 'TPS'),
  sample('players', 12, name: 'Players'),
]);

final List<MetricHistoryDescriptor> _catalog = <MetricHistoryDescriptor>[
  _empty('adapt-skill-xp'),
  _stored('players', 'Players'),
  _stored('ticks-per-second', 'TPS'),
  _stored('wormholes-portals', 'Portals'),
];

void main() {
  group('MetricsExplorerScreen missing snapshot states', () {
    testServer('offline state reports reconnecting without another notice', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(const MetricsExplorerScreen(), state: ConnState.offline),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('No telemetry snapshot'));
      expect(body, contains('after React reconnects'));
      expect(body, isNot(contains('data-alert=')));
    });

    testServer('degraded state reports recovery without another notice', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(const MetricsExplorerScreen(), state: ConnState.degraded),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('No telemetry snapshot'));
      expect(body, contains('after the connection recovers'));
    });
  });

  group('MetricsExplorerScreen history', () {
    testComponents('opens on the first available sampler with samples', (
      ComponentTester tester,
    ) async {
      final _History history = _History(_catalog);
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(),
          snapshot: _live(),
          historyClient: history,
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(history.asked, <String>['players']);
      expect(find.textContaining('players ·'), findsOneComponent);
    });

    testComponents('opens the sampler the address names', (
      ComponentTester tester,
    ) async {
      final _History history = _History(_catalog);
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(metric: 'ticks-per-second'),
          snapshot: _live(),
          historyClient: history,
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(history.asked, <String>['ticks-per-second']);
    });

    testComponents('a sampler with nothing stored never shows a date', (
      ComponentTester tester,
    ) async {
      final _History history = _History(_catalog);
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(metric: 'adapt-skill-xp'),
          snapshot: _live(),
          historyClient: history,
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(history.asked, isEmpty);
      expect(
        find.text(ReactorText.metricsNoSamples.english),
        findsOneComponent,
      );
      expect(find.textContaining('1969'), findsNothing);
      expect(find.textContaining('1970'), findsNothing);
    });

    testComponents('the range is one toggle group, not loose buttons', (
      ComponentTester tester,
    ) async {
      final _History history = _History(_catalog);
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(),
          snapshot: _live(),
          historyClient: history,
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              (component.classes ?? '').contains('reactor-history-range'),
        ),
        findsNothing,
      );
      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.attributes?['role'] == 'group' &&
              component.attributes?['aria-label'] ==
                  ReactorText.metricsHistory.english,
        ),
        findsOneComponent,
      );
      expect(find.text(ReactorText.metricsRangeAll.english), findsOneComponent);
    });
  });

  group('MetricsExplorerScreen catalog', () {
    testComponents('a sampler missing from the frame reads "--"', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(),
          snapshot: _live(),
          historyClient: _History(_catalog),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.text('Portals'), findsOneComponent);
      expect(find.textContaining('Unavailable'), findsNothing);
      expect(find.textContaining('unavailable'), findsNothing);
    });

    testComponents('rows link to their own history', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MetricsExplorerScreen(serverId: 's1'),
          snapshot: _live(),
          historyClient: _History(_catalog),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(
        find.byComponentPredicate(
          (Component component) =>
              component is DomComponent &&
              component.tag == 'a' &&
              component.attributes?['href'] ==
                  '/server/s1/metrics?metric=wormholes-portals',
        ),
        findsOneComponent,
      );
    });

    testServer('without a catalog it lists the live samplers', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(const MetricsExplorerScreen(), snapshot: _live()),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('role="table"'));
      expect(body, contains('ticks-per-second'));
      expect(body, contains(ReactorText.metricsHistoryUnavailable.english));
    });
  });
}
