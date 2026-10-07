import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component, DomComponent, StatefulBuilder;
import 'package:jaspr_test/jaspr_test.dart';
import 'package:react_web/chart/timeseries_chart.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/metrics_explorer.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

class _HistoryClient implements IHistoryClient {
  int requests = 0;

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      <MetricHistoryDescriptor>[
        MetricHistoryDescriptor(
          id: 'tps',
          name: 'TPS',
          suffix: '',
          firstAt: DateTime(2026),
          lastAt: DateTime.now(),
          active: true,
        ),
      ];

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) async {
    requests++;
    final DateTime at = DateTime.now();
    return MetricHistoryPage(
      requestedFrom: at,
      requestedTo: at,
      pageFrom: at,
      pageTo: at,
      resolution: const Duration(seconds: 1),
      throughSequence: 1,
      throughAt: at,
      nextCursor: null,
      series: <MetricHistorySeries>[
        MetricHistorySeries(
          id: 'tps',
          name: 'TPS',
          suffix: '',
          points: <MetricHistoryPoint>[
            MetricHistoryPoint(
              at: at,
              average: 20,
              minimum: 19,
              maximum: 20,
              last: 20,
              count: 1,
            ),
          ],
        ),
      ],
    );
  }
}

Finder _range(String text) => find.ancestor(
  of: find.text(text),
  matching: find.byComponentPredicate(
    (Component component) =>
        component is DomComponent && component.tag == 'button',
  ),
);

void main() {
  testComponents(
    'reuses recent ranges and preserves history rendering during live updates',
    (ComponentTester tester) async {
      _HistoryClient client = _HistoryClient();
      int sequence = 1;
      late void Function(void Function()) rebuild;
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: const ShadcnStylesheet(theme: ShadcnTheme.midnight),
          child: StatefulBuilder(
            builder:
                (
                  BuildContext context,
                  void Function(void Function()) setState,
                ) {
                  rebuild = setState;
                  return ServerScope(
                    snapshot: ServerSnapshot(
                      byId: const <String, SamplerSample>{},
                      at: DateTime.now(),
                      seq: sequence,
                    ),
                    state: ConnState.live,
                    historyClient: client,
                    child: const MetricsExplorerScreen(),
                  );
                },
          ),
        ),
      );
      await tester.pump();
      expect(client.requests, 1);
      final Component chart = find
          .byType(TimeseriesChart)
          .evaluate()
          .single
          .component;
      rebuild(() => sequence++);
      await tester.pump();
      expect(
        identical(
          find.byType(TimeseriesChart).evaluate().single.component,
          chart,
        ),
        isTrue,
      );
      await tester.click(_range('1h'));
      expect(client.requests, 2);
      await tester.click(_range('24h'));
      expect(client.requests, 2);
      rebuild(() => client = _HistoryClient());
      await tester.pump();
      expect(client.requests, 1);
    },
  );
}
