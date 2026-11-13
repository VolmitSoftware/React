library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/alert.dart';
import 'package:react_web/screen/alerts_inbox.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';

import 'fleet_test_support.dart';

final FleetServerLive _critical = liveServer(
  'b',
  'Beta',
  samplers: <String, double>{'ticks-per-second': 5.0},
);
final FleetServerLive _warning = liveServer(
  'c',
  'Gamma',
  samplers: <String, double>{'tick-time': 80.0},
);
final FleetServerLive _healthy = liveServer(
  'a',
  'Alpha',
  samplers: <String, double>{'ticks-per-second': 20.0},
);

void main() {
  group('openFleetAlerts', () {
    test('raises alerts from live servers only', () {
      final List<FleetAlert> open = openFleetAlerts(<FleetServerLive>[
        _healthy,
        _critical,
        liveServer(
          'd',
          'Cached',
          state: ConnState.offline,
          samplers: <String, double>{'ticks-per-second': 5.0},
        ),
      ], TestFleet().alertStore);

      expect(open, hasLength(1));
      expect(open.single.serverId, 'b');
      expect(open.single.severity, AlertSeverity.critical);
    });

    test('keeps a degraded server with its last snapshot', () {
      final List<FleetAlert> open = openFleetAlerts(<FleetServerLive>[
        liveServer(
          'b',
          'Beta',
          state: ConnState.degraded,
          samplers: <String, double>{'ticks-per-second': 5.0},
        ),
      ], TestFleet().alertStore);

      expect(open, hasLength(1));
    });

    test('leaves out cleared alerts', () {
      final TestFleet fleet = TestFleet();
      fleet.alertStore.resolve('b/ticks-per-second');

      expect(
        openFleetAlerts(<FleetServerLive>[_critical], fleet.alertStore),
        isEmpty,
      );
    });
  });

  group('AlertsInboxScreen', () {
    testServer('lists each alert with its server, linked to Incidents', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: TestFleet(),
          servers: <FleetServerLive>[_healthy, _critical, _warning],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.alertLowTps.english));
      expect(body, contains(ReactorText.alertHighMspt.english));
      expect(body, contains('>Beta<'));
      expect(body, contains('>Gamma<'));
      expect(body, isNot(contains('href="/server/a/incidents"')));
      expect(body, contains('href="/server/b/incidents"'));
      expect(body, contains('href="/server/c/incidents"'));
      expect(body, contains(ReactorText.alertsSeverityCritical.english));
      expect(body, contains(ReactorText.alertsSeverityWarning.english));
    });

    testServer('puts the filters in the page header', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: TestFleet(),
          servers: <FleetServerLive>[_critical],
        ),
      );
      final String body = (await tester.request('/')).body;
      final int header = body.indexOf('ui-page-header');
      final int list = body.indexOf('role="table"');

      expect(header, greaterThanOrEqualTo(0));
      expect(
        body.indexOf('id="alerts-severity"'),
        inInclusiveRange(header, list),
      );
      expect(
        body.indexOf('id="alerts-server"'),
        inInclusiveRange(header, list),
      );
    });

    testServer('says when nothing is open', (ServerTester tester) async {
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: TestFleet(),
          servers: <FleetServerLive>[_healthy],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.alertsNoneOpen.english));
      expect(body, contains('<p class="ui-caption">'));
    });

    testServer('says no server reports when every server is offline', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: TestFleet(),
          servers: <FleetServerLive>[
            liveServer('d', 'Delta', state: ConnState.offline),
          ],
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.snapshotNone.english));
      expect(body, isNot(contains(ReactorText.alertsNoneOpen.english)));
    });

    testComponents('Acknowledge marks one alert and Clear removes it', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = TestFleet();
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: fleet,
          confirms: true,
          servers: <FleetServerLive>[_critical],
        ),
      );
      await tester.pump();

      // Row buttons are named by what they act on, like Edit and Remove.
      const String row = 'Low TPS, Beta';
      await tester.click(buttonNamed('${ReactorText.alertsAck.english}: $row'));
      expect(fleet.alertStore.isAcked('b/ticks-per-second'), isTrue);
      expect(
        buttonNamed('${ReactorText.alertsAcked.english}: $row'),
        findsOneComponent,
      );

      await tester.click(
        buttonNamed('${ReactorText.alertsResolve.english}: $row'),
      );
      expect(fleet.alertStore.isResolved('b/ticks-per-second'), isTrue);
      expect(find.text(ReactorText.alertsNoneOpen.english), findsOneComponent);
    });

    testComponents('Acknowledge all asks first', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = TestFleet();
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: fleet,
          confirms: true,
          servers: <FleetServerLive>[_critical, _warning],
        ),
      );
      await tester.pump();

      await tester.click(buttonLabeled(ReactorText.alertsAckAll.english));
      await tester.pump();
      expect(
        find.text(
          reactorText(ReactorText.alertsAckAllTitle, <String, Object?>{
            'count': 2,
          }),
        ),
        findsOneComponent,
      );
      expect(fleet.alertStore.isAcked('b/ticks-per-second'), isFalse);

      await tester.click(confirmButton());
      await tester.pump();
      expect(fleet.alertStore.isAcked('b/ticks-per-second'), isTrue);
      expect(fleet.alertStore.isAcked('c/tick-time'), isTrue);
    });

    testComponents('Clear all asks first and Cancel keeps the alerts', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = TestFleet();
      tester.pumpComponent(
        fleetHarness(
          const AlertsInboxScreen(),
          fleet: fleet,
          confirms: true,
          servers: <FleetServerLive>[_critical, _warning],
        ),
      );
      await tester.pump();

      await tester.click(buttonLabeled(ReactorText.alertsClearAll.english));
      await tester.pump();
      await tester.click(questionButton(ReactorText.commonCancel.english));
      await tester.pump();
      expect(fleet.alertStore.isResolved('b/ticks-per-second'), isFalse);
      expect(find.text(ReactorText.alertLowTps.english), findsOneComponent);

      await tester.click(buttonLabeled(ReactorText.alertsClearAll.english));
      await tester.pump();
      await tester.click(confirmButton());
      await tester.pump();
      expect(fleet.alertStore.isResolved('b/ticks-per-second'), isTrue);
      expect(fleet.alertStore.isResolved('c/tick-time'), isTrue);
      expect(find.text(ReactorText.alertsNoneOpen.english), findsOneComponent);
    });
  });
}
