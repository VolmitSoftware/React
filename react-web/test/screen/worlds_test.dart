library;

import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/chart/timeseries_chart.dart';
import 'package:react_web/screen/worlds.dart';
import 'package:react_web/state/connection_manager.dart';

import 'support/operate_fakes.dart';

void main() {
  group('WorldsScreen', () {
    testServer('draws world tick time as a chart in its card', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            snapshot: snapshotWith(<String, double>{
              'top-world-mspt': 18.2,
              'per-world-tick-time': 9.5,
            }),
          ),
          const WorldsScreen(),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(res.body, contains(ReactorText.commonTopWorldMspt.english));
      expect(res.body, contains('data-chart-card'));
      expect(res.body, contains('role="img"'));
      expect(res.body, contains('Top world MSPT'));
      expect(res.body, contains('ms'));
    });

    testServer('waits for the first snapshot while connecting', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(),
          const WorldsScreen(),
          state: ConnState.connecting,
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('ui-loading'));
    });

    testComponents('shows the new server after a switch', (
      ComponentTester tester,
    ) async {
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[
            FakeServer(
              snapshot: snapshotWith(<String, double>{'top-world-mspt': 11}),
            ),
            FakeServer(
              snapshot: snapshotWith(<String, double>{
                'top-world-mspt': 77,
              }, seq: 2),
            ),
          ],
          child: const WorldsScreen(),
        ),
      );
      await tester.pump();
      TimeseriesChart chart() =>
          find.byType(TimeseriesChart).evaluate().single.component
              as TimeseriesChart;
      expect(chart().series.first.$2.last, 11);
      key.currentState!.show(1);
      await tester.pump();
      expect(chart().series.first.$2.last, 77);
    });
  });
}
