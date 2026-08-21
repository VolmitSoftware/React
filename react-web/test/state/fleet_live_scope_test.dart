library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_live_model.dart';
import 'package:react_web/state/fleet_live_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

class _Probe extends StatelessWidget {
  const _Probe();

  @override
  Widget build(BuildContext context) {
    final FleetLiveScope? scope = FleetLiveScope.of(context);
    final int count = scope?.servers.length ?? 0;
    return Text('servers:$count');
  }
}

class _NullProbe extends StatelessWidget {
  const _NullProbe();

  @override
  Widget build(BuildContext context) {
    final FleetLiveScope? scope = FleetLiveScope.of(context);
    return Text(scope == null ? 'scope:null' : 'scope:present');
  }
}

void main() {
  group('FleetLiveScope + FleetLiveObserver', () {
    testServer(
      'FleetLiveObserver provides FleetLiveScope with correct server count',
      (ServerTester tester) async {
        final StreamController<ServerSnapshot> snapA =
            StreamController<ServerSnapshot>.broadcast();
        final StreamController<ConnState> stateA =
            StreamController<ConnState>.broadcast();
        final StreamController<ServerSnapshot> snapB =
            StreamController<ServerSnapshot>.broadcast();
        final StreamController<ConnState> stateB =
            StreamController<ConnState>.broadcast();

        final List<FleetLiveSource> sources = <FleetLiveSource>[
          FleetLiveSource(
            id: 'a',
            name: 'Server A',
            initialState: ConnState.connecting,
            snapshots: snapA.stream,
            stateChanges: stateA.stream,
          ),
          FleetLiveSource(
            id: 'b',
            name: 'Server B',
            initialState: ConnState.connecting,
            snapshots: snapB.stream,
            stateChanges: stateB.stream,
          ),
        ];

        tester.pumpComponent(
          _wrap(FleetLiveObserver(sources: sources, child: const _Probe())),
        );

        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('servers:2'),
          isTrue,
          reason: 'FleetLiveScope must expose the two sources as servers',
        );

        await snapA.close();
        await snapB.close();
        await stateA.close();
        await stateB.close();
      },
    );

    testServer('FleetLiveScope.of returns null when not in tree', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const _NullProbe()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
      expect(
        res.body.contains('scope:null'),
        isTrue,
        reason:
            'FleetLiveScope.of must return null when no FleetLiveObserver is in the tree',
      );
    });
  });
}
