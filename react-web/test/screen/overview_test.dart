library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/overview.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/chart_history.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_scope.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_session.dart';
import 'package:react_web/state/server_session_scope.dart';
import 'package:react_web/state/role_scope.dart';
import 'package:react_web/state/server_tags_store.dart';

import '../state/support/fake_react_client.dart';
import 'monitor_support.dart';

ServerSnapshot _healthy() => snapshotOf(<SamplerSample>[
  sample('ticks-per-second', 19.9),
  sample('tick-time', 2.5, suffix: 'ms'),
  sample('players', 42),
  sample('memory-used', 4096, suffix: 'MB'),
  sample('jvm-heap-utilization', 40, suffix: '%'),
  sample('memory-pressure', 83614412, suffix: 'MB/s'),
  sample('incident-score', 12),
  sample('entities', 350),
  sample('chunks', 1200),
  sample('gc-time-percent', 3.2, suffix: '%'),
]);

ServerSnapshot _struggling() => snapshotOf(<SamplerSample>[
  sample('ticks-per-second', 9.1),
  sample('tick-time', 56.6, suffix: 'ms'),
  sample('players', 40),
  sample('memory-used', 13248, suffix: 'MB'),
  sample('jvm-heap-utilization', 94, suffix: '%'),
  sample('incident-score', 75),
]);

class _Fleet implements FleetController {
  final AlertStore _alerts = AlertStore(InMemoryFleetStorage());

  @override
  AlertStore get alertStore => _alerts;

  @override
  String? labelFor(String id) => 'Skyblock';

  @override
  FleetManager get fleetManager => throw UnimplementedError();

  @override
  ServerTagsStore get tagsStore => throw UnimplementedError();

  @override
  ConnectionManager? managerFor(String id) => null;

  @override
  void trackPaired(String id) {}

  @override
  void removeServer(String id) {}

  @override
  void clearFleet() {}

  @override
  void importFleet(List<ServerCredential> creds) {}
}

List<ActionDescriptor> _serverActions() => <ActionDescriptor>[
  const ActionDescriptor(
    id: 'purge-entities',
    name: 'Purge Entities',
    description: '',
    destructive: true,
  ),
  const ActionDescriptor(
    id: 'collect-garbage',
    name: 'Collect Garbage',
    description: '',
    destructive: false,
  ),
];

Widget _overview({
  ServerSnapshot? snapshot,
  FleetController? fleet,
  String? serverId = 's1',
  RoleInfo? role,
  ConnState state = ConnState.live,
  ServerSession? session,
  ChartHistory? charts,
}) {
  Widget child = OverviewScreen(serverId: serverId);
  if (session != null) {
    child = ServerSessionScope(session: session, child: child);
  }
  child = RoleScope(role: role, child: child);
  if (fleet != null) {
    child = FleetScope(controller: fleet, revision: 0, child: child);
  }
  return onServer(child, snapshot: snapshot, state: state, charts: charts);
}

/// A Folia server: its catalog says tick time is not available.
class _FoliaClient extends FakeReactClient implements IHistoryClient {
  _FoliaClient() : super(fingerprint: 'fp-s1');

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      <MetricHistoryDescriptor>[
        MetricHistoryDescriptor.fromJson(<String, dynamic>{
          'id': 'tick-time',
          'name': 'Tick Time',
          'suffix': 'ms',
          'firstTimestampMs': 0,
          'lastTimestampMs': 0,
          'active': true,
          'available': false,
          'unavailableReason': 'source-unavailable',
        }),
      ];

  @override
  Future<RangeHistory> metricRange(ChartRange range, {List<String>? ids}) =>
      throw UnimplementedError();

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 0,
    int pageSize = 0,
    String? cursor,
  }) => throw UnimplementedError();
}

const RoleInfo _viewer = RoleInfo(role: 'viewer', scopes: <String>['read']);
const RoleInfo _operator = RoleInfo(
  role: 'operator',
  scopes: <String>['read', 'op:execute'],
);

void main() {
  group('OverviewScreen vitals', () {
    testServer('four vitals are top-level tiles, in sentence case', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _healthy()));
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'class="ui-metric"'), 4);
      expect(body, isNot(contains('class="ui-stat"')));
      for (final String label in <String>[
        'TPS',
        'Tick time',
        'Players',
        'Memory',
        'Incident score',
      ]) {
        expect(body, contains(label));
      }
      expect(body, isNot(contains('Tick Time')));
      expect(body, isNot(contains('Incident Score')));
      expect(body, contains('19.9'));
    });

    testServer('entities, chunks and GC stay off the Overview', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _healthy()));
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains(ReactorText.commonEntities.english)));
      expect(body, isNot(contains(ReactorText.commonChunks.english)));
      expect(body, isNot(contains(ReactorText.commonGcTime.english)));
    });

    testServer('a struggling server says so in colour and words', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _struggling()));
      final String body = (await tester.request('/')).body;

      expect(body, contains('data-tone="error"'));
      expect(body, contains('data-tone="warning"'));
      expect(body, contains(ReactorText.statusCritical.english));
    });

    testComponents('a server without tick time says so in the tile and in '
        'the chart card, which keeps its place', (
      ComponentTester tester,
    ) async {
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        's1': _FoliaClient(),
      });
      addTearDown(fleet.dispose);
      final ServerSnapshot folia = snapshotOf(<SamplerSample>[
        for (final SamplerSample each in _healthy().byId.values)
          if (each.id != 'tick-time') each,
      ]);
      tester.pumpComponent(
        _overview(snapshot: folia, session: fleet.sessionFor('s1')),
      );
      await tester.pump();
      await tester.pump();

      expect(
        find.text(ReactorText.commonNotReported.english),
        findsNComponents(2),
      );
      expect(find.text(ReactorText.chartAwaitingSamples.english), findsNothing);
    });

    testServer('the tick chart has the range toggle beside Details', (
      ServerTester tester,
    ) async {
      final ChartHistory charts = await chartsOn(
        ChartRange.hour,
        <String, double>{'tick-time': 3},
      );
      tester.pumpComponent(_overview(snapshot: _healthy(), charts: charts));
      final String body = (await tester.request('/')).body;

      final int toggle = body.indexOf(
        'aria-label="${ReactorText.chartRange.english}"',
      );
      expect(toggle, greaterThan(0));
      expect(
        body.indexOf('>${ReactorText.incidentsDetails.english}<', toggle),
        greaterThan(toggle),
      );
      expect(body, contains('aria-pressed="true" data-variant="outline">1h<'));
    });

    testComponents('a server without tick time gets no range toggle', (
      ComponentTester tester,
    ) async {
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        's1': _FoliaClient(),
      });
      addTearDown(fleet.dispose);
      final ServerSession session = fleet.sessionFor('s1')!;
      final ServerSnapshot folia = snapshotOf(<SamplerSample>[
        for (final SamplerSample each in _healthy().byId.values)
          if (each.id != 'tick-time') each,
      ]);
      tester.pumpComponent(
        _overview(snapshot: folia, session: session, charts: session.charts),
      );
      await tester.pump();
      await tester.pump();

      expect(
        find.text(ReactorText.commonNotReported.english),
        findsNComponents(2),
      );
      expect(find.text(ReactorText.chartRangeLive.english), findsNothing);
    });

    testServer('memory reads in the heap maximum\'s unit, of that maximum', (
      ServerTester tester,
    ) async {
      final ServerSnapshot snapshot = snapshotOf(<SamplerSample>[
        ..._healthy().byId.values.where(
          (SamplerSample each) => each.id != 'memory-used',
        ),
        sample('memory-used', 900, suffix: 'MB'),
        sample('jvm-heap-max', 16384, suffix: 'MB'),
      ]);
      tester.pumpComponent(_overview(snapshot: snapshot));
      final String body = (await tester.request('/')).body;

      expect(body, contains('0.9'));
      expect(body, contains('of 16.0 GB'));
      expect(body, isNot(contains('900')));
    });

    testServer('one tick time chart, in a chart card', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _healthy()));
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'data-chart-card'), 1);
      expect(body, contains('role="img"'));
    });
  });

  group('OverviewScreen incidents', () {
    testServer('the status line names the state and the score', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _struggling()));
      final String body = (await tester.request('/')).body;

      expect(body, contains('data-incident-status'));
      expect(body, contains('data-alert="error"'));
      expect(body, contains('Incident score 75.0'));
      expect(body, contains(ReactorText.overviewViewIncidents.english));
    });

    testServer('lists this server\'s open alerts', (ServerTester tester) async {
      tester.pumpComponent(_overview(snapshot: _struggling(), fleet: _Fleet()));
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.alertLowTps.english));
      expect(body, isNot(contains(ReactorText.overviewNoAlerts.english)));
      expect(
        occurrences(body, ReactorText.alertElevatedIncidentScore.english),
        0,
      );
    });

    testServer('says once, in one vocabulary, when nothing is open', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_overview(snapshot: _healthy(), fleet: _Fleet()));
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, ReactorText.overviewNoAlerts.english), 1);
      expect(occurrences(body, ReactorText.commonHealthy.english), 1);
      expect(body, isNot(contains('>Normal<')));
    });

    testServer('resolved alerts stay hidden', (ServerTester tester) async {
      final _Fleet fleet = _Fleet();
      for (final String metric in <String>[
        'ticks-per-second',
        'tick-time',
        'incident-score',
        'jvm-heap-utilization',
      ]) {
        fleet.alertStore.resolve('s1/$metric');
      }
      tester.pumpComponent(_overview(snapshot: _struggling(), fleet: fleet));
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains(ReactorText.alertLowTps.english)));
      expect(body, isNot(contains('data-alert="warning"><span')));
    });
  });

  group('OverviewScreen quick actions', () {
    testComponents('operators get shortcuts, safe actions first', (
      ComponentTester tester,
    ) async {
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        's1': FakeReactClient(fingerprint: 'fp-s1', actions: _serverActions()),
      });
      addTearDown(fleet.dispose);
      tester.pumpComponent(
        _overview(
          snapshot: _healthy(),
          role: _operator,
          session: fleet.sessionFor('s1'),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(
        find.text(ReactorText.overviewQuickActions.english),
        findsOneComponent,
      );
      expect(find.text('Collect Garbage'), findsOneComponent);
      expect(
        find.text(ReactorText.overviewAllActions.english),
        findsOneComponent,
      );
    });

    testComponents('viewers get none', (ComponentTester tester) async {
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        's1': FakeReactClient(fingerprint: 'fp-s1', actions: _serverActions()),
      });
      addTearDown(fleet.dispose);
      tester.pumpComponent(
        _overview(
          snapshot: _healthy(),
          role: _viewer,
          session: fleet.sessionFor('s1'),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.text(ReactorText.overviewQuickActions.english), findsNothing);
      expect(find.text('Collect Garbage'), findsNothing);
    });
  });

  group('OverviewScreen without a snapshot', () {
    testServer('loads while connecting', (ServerTester tester) async {
      tester.pumpComponent(_overview(state: ConnState.connecting));
      final String body = (await tester.request('/')).body;

      expect(body, contains('ui-loading'));
      expect(body, isNot(contains('class="ui-metric"')));
    });

    testServer('is empty while offline', (ServerTester tester) async {
      tester.pumpComponent(_overview(state: ConnState.offline));
      final String body = (await tester.request('/')).body;

      expect(body, contains('ui-empty'));
    });
  });
}
