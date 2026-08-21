library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/screen/chunks.dart';
import 'package:react_web/screen/entities.dart';
import 'package:react_web/screen/events.dart';
import 'package:react_web/screen/heatmaps.dart';
import 'package:react_web/screen/incidents.dart';
import 'package:react_web/screen/integrations.dart';
import 'package:react_web/screen/internals.dart';
import 'package:react_web/screen/mechanics.dart';
import 'package:react_web/screen/memory.dart';
import 'package:react_web/screen/metrics_explorer.dart';
import 'package:react_web/screen/overview.dart';
import 'package:react_web/screen/performance.dart';
import 'package:react_web/screen/worlds.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child, ConnState state) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ServerScope(snapshot: null, state: state, child: child),
);

const List<({String name, Widget screen})> _samplerScreens =
    <({String name, Widget screen})>[
      (name: 'overview', screen: OverviewScreen()),
      (name: 'performance', screen: PerformanceScreen()),
      (name: 'metrics', screen: MetricsExplorerScreen()),
      (name: 'memory', screen: MemoryScreen()),
      (name: 'entities', screen: EntitiesScreen()),
      (name: 'chunks', screen: ChunksScreen()),
      (name: 'mechanics', screen: MechanicsScreen()),
      (name: 'events', screen: EventsScreen()),
      (name: 'heatmaps', screen: HeatmapsScreen()),
      (name: 'internals', screen: InternalsScreen()),
      (name: 'incidents', screen: IncidentsScreen()),
      (name: 'worlds', screen: WorldsScreen()),
      (name: 'integrations', screen: IntegrationsScreen()),
    ];

void main() {
  group('sampler screen missing snapshot states', () {
    for (final ({String name, Widget screen}) entry in _samplerScreens) {
      testServer('${entry.name} is terminal while offline', (
        ServerTester tester,
      ) async {
        tester.pumpComponent(_wrap(entry.screen, ConnState.offline));

        final DocumentResponse response = await tester.request('/');
        expect(response.statusCode, equals(200));
        expect(response.body, contains('No telemetry snapshot'));
        expect(response.body, contains('after React reconnects'));
        expect(response.body, isNot(contains('is-loading')));
      });

      testServer('${entry.name} is terminal while degraded', (
        ServerTester tester,
      ) async {
        tester.pumpComponent(_wrap(entry.screen, ConnState.degraded));

        final DocumentResponse response = await tester.request('/');
        expect(response.statusCode, equals(200));
        expect(response.body, contains('No telemetry snapshot'));
        expect(response.body, contains('after the connection recovers'));
        expect(response.body, isNot(contains('is-loading')));
      });
    }

    testServer('connecting remains an active telemetry wait', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const OverviewScreen(), ConnState.connecting));

      final DocumentResponse response = await tester.request('/');
      expect(response.statusCode, equals(200));
      expect(response.body, contains('Waiting for telemetry'));
      expect(response.body, contains('is-loading'));
    });

    testServer('live waits specifically for its first snapshot', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const OverviewScreen(), ConnState.live));

      final DocumentResponse response = await tester.request('/');
      expect(response.statusCode, equals(200));
      expect(
        response.body,
        contains('Waiting for the first telemetry snapshot'),
      );
      expect(response.body, contains('is-loading'));
    });
  });
}
