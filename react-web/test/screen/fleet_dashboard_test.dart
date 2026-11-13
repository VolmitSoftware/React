library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/screen/fleet_dashboard.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';

import 'fleet_test_support.dart';

final List<FleetServerLive> _pair = <FleetServerLive>[
  liveServer(
    'a',
    'Alpha',
    role: 'admin',
    transport: ServerTransport.direct,
    samplers: <String, double>{
      'ticks-per-second': 19.9,
      'tick-time': 12.0,
      'players': 10,
      'incident-score': 10,
    },
  ),
  liveServer(
    'b',
    'Beta',
    transport: ServerTransport.relay,
    samplers: <String, double>{
      'ticks-per-second': 9.0,
      'tick-time': 80.0,
      'players': 2,
      'incident-score': 55,
    },
  ),
];

TestFleet _fleet() => TestFleet(
  servers: <ServerCredential>[
    testCredential('a', 'Alpha'),
    testCredential(
      'b',
      'Beta',
      host: '',
      port: 0,
      relayUrl: 'wss://relay.example.com',
    ),
  ],
  client: (ServerCredential cred) => cred.id == 'a'
      ? StubClient(role: adminRole)
      : StubClient(roleError: const ReactForbidden()),
);

void main() {
  group('FleetDashboardScreen', () {
    testServer('shows the health tiles of the server group', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      for (final ReactorText label in <ReactorText>[
        ReactorText.commonHealthy,
        ReactorText.fleetWorstTps,
        ReactorText.fleetWorstMspt,
        ReactorText.commonPlayers,
      ]) {
        expect(body, contains(label.english));
      }
      expect(body, contains('1 / 2'), reason: 'Alpha healthy, Beta not');
      expect(body, contains('>9.0<'), reason: 'worst TPS');
      expect(body, contains('>80.0<'), reason: 'worst tick time');
      expect(body, contains('>ms<'));
      expect(body, contains('>12<'), reason: 'players of both servers');
      expect(body, contains('ui-metric-grid'));
    });

    testServer('links every row to the server Overview', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('href="/server/a"'));
      expect(body, contains('href="/server/b"'));
      expect(body, contains('role="table"'));
      expect(body, isNot(contains('Remove')), reason: 'removal is in Settings');
    });

    testServer('names the transport of each server in Advanced', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          ModeScope(
            mode: ReactorMode.advanced,
            onChanged: (ReactorMode _) {},
            child: const FleetDashboardScreen(),
          ),
          fleet: _fleet(),
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.fleetDirect.english));
      expect(body, contains(ReactorText.pairRelay.english));
    });

    testServer('one state per server from the shared verdict', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.commonHealthy.english));
      expect(body, contains(ReactorText.statusCritical.english));
      expect(body, isNot(contains(ReactorText.statusElevated.english)));
    });

    testComponents(
      'shows each role in Advanced; an old entry asks the server',
      (ComponentTester tester) async {
        tester.pumpComponent(
          fleetHarness(
            ModeScope(
              mode: ReactorMode.advanced,
              onChanged: (ReactorMode _) {},
              child: const FleetDashboardScreen(),
            ),
            fleet: _fleet(),
            servers: _pair,
          ),
        );
        await tester.pump();

        expect(find.text(ReactorText.roleAdmin.english), findsOneComponent);
        expect(
          find.text(ReactorText.settingsNoAccess.english),
          findsOneComponent,
        );
      },
    );

    testServer('links servers that need attention to their Incidents', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.fleetNeedsAttention.english));
      expect(body, contains('href="/server/b/incidents"'));
      expect(body, isNot(contains('href="/server/a/incidents"')));
    });

    testServer('shows no Needs attention list when none needs attention', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: <FleetServerLive>[_pair.first],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains(ReactorText.fleetNeedsAttention.english)));
    });

    testServer('marks an offline server', (ServerTester tester) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: <FleetServerLive>[
            liveServer('a', 'Alpha', state: ConnState.offline),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.startNotReachable.english));
      expect(body, contains('href="/server/a/incidents"'));
    });

    testServer('offers Pair again and Remove for a revoked entry', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: _fleet(),
          servers: <FleetServerLive>[
            _pair.first,
            liveServer(
              'b',
              'Beta',
              state: ConnState.offline,
              access: ConnAccess.revoked,
            ),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.pairEndedState.english));
      expect(body, contains(ReactorText.fleetPairAgain.english));
      expect(
        body,
        contains(
          'aria-label="${reactorText(ReactorText.settingsRemoveServer, <String, Object?>{'server': 'Beta'})}"',
        ),
      );
    });

    testComponents('Remove on a revoked entry asks first', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: fleet,
          confirms: true,
          servers: <FleetServerLive>[
            liveServer(
              'b',
              'Beta',
              state: ConnState.offline,
              access: ConnAccess.revoked,
            ),
          ],
        ),
      );
      await tester.pump();

      // The entry is in the server table and in Needs attention.
      await tester.click(
        buttonNamed(
          reactorText(ReactorText.settingsRemoveServer, <String, Object?>{
            'server': 'Beta',
          }),
        ).first,
      );
      await tester.pump();
      expect(fleet.removed, isEmpty);
      await tester.click(confirmButton());
      await tester.pump();
      expect(fleet.removed, <String>['b']);
    });

    testServer('offers the tag filter only when servers have tags', (
      ServerTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      fleet.tagsStore.setTags('a', <String>['eu']);
      tester.pumpComponent(
        fleetHarness(
          const FleetDashboardScreen(),
          fleet: fleet,
          servers: _pair,
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('id="fleet-tag"'));
      expect(body, contains('>eu<'));
    });

    testServer('leads to pairing when nothing is paired', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const FleetDashboardScreen(), fleet: TestFleet()),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.fleetNoServersPaired.english));
      expect(body, contains(ReactorText.pairTitle.english));
      expect(body, contains('ui-empty'));
    });
  });
}
