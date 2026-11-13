library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component, DomComponent;
import 'package:jaspr_test/jaspr_test.dart';
import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_revisions.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/screen/heatmaps.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/role_scope.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/server_scope.dart';
import 'package:react_web/state/server_session.dart';
import 'package:react_web/state/server_session_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/mode.dart';

import '../state/support/fake_react_client.dart';
import 'monitor_support.dart' show fleetOf;

const ReactorStylesheet _sheet = ReactorStylesheet();

const RoleInfo _admin = RoleInfo(
  role: 'admin',
  scopes: <String>['read', 'op:execute', 'admin'],
);
const RoleInfo _viewer = RoleInfo(role: 'viewer', scopes: <String>['read']);

SamplerSample _sample(String id, double value) => SamplerSample(
  id: id,
  name: id,
  value: value,
  display: value.toString(),
  suffix: '%',
  min: 0,
  max: 1,
  history: <double>[value, value],
);

ServerSnapshot _snapshot() => ServerSnapshot(
  byId: <String, SamplerSample>{
    'entity-pressure-heatmap': _sample('entity-pressure-heatmap', 0.7),
  },
  at: DateTime(2026, 11, 12),
  seq: 1,
);

HeatmapGrid _grid(String id) => HeatmapGrid(
  id: id,
  label: 'Entity Pressure',
  world: 'world',
  centerChunkX: 0,
  centerChunkZ: 0,
  radius: 2,
  originChunkX: -2,
  originChunkZ: -2,
  width: 5,
  height: 5,
  cellSizeChunks: 1,
  capturedAtMs: 1750000000000,
  spawnChunkX: 0,
  spawnChunkZ: 0,
  min: 0,
  max: 10,
  cells: const <HeatmapCell>[
    HeatmapCell(
      x: 0,
      z: 0,
      sizeChunks: 1,
      score: 7,
      averageScore: 4.5,
      samples: 12,
    ),
  ],
);

const List<WorldSettings> _worlds = <WorldSettings>[
  WorldSettings(
    key: 'world',
    name: 'world',
    pressureMode: PressureMode.normal,
    budgetMs: 50,
    panicMs: 45,
    releaseMs: 30,
  ),
];

/// A server with heatmaps, worlds and players.
class _MapClient extends FakeReactClient
    implements IHeatmapClient, IPlayerClient {
  _MapClient({this.fail}) : super(fingerprint: 'fp-a', worlds: _worlds);

  final Object? fail;
  final List<(String, String?, int?, int?, int?)> requests =
      <(String, String?, int?, int?, int?)>[];

  @override
  Future<List<HeatmapSummary>> heatmaps() async => const <HeatmapSummary>[
    HeatmapSummary(id: 'entity-pressure-heatmap', label: 'Entity Pressure'),
    HeatmapSummary(id: 'redstone-activity-heatmap', label: 'Redstone'),
  ];

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) async {
    requests.add((id, world, centerChunkX, centerChunkZ, radius));
    final Object? error = fail;
    if (error != null) throw error;
    return _grid(id);
  }

  @override
  Future<List<OnlinePlayerInfo>> players() async => const <OnlinePlayerInfo>[
    OnlinePlayerInfo(id: 'p1', name: 'Steve'),
  ];

  @override
  Future<PlayerTeleportResult> teleportPlayer(
    String playerId, {
    required String worldKey,
    required int blockX,
    required int blockZ,
  }) async => PlayerTeleportResult(
    playerId: playerId,
    playerName: 'Steve',
    status: 'queued',
    worldKey: worldKey,
    blockX: blockX,
    blockZ: blockZ,
  );
}

/// The map on a server whose session talks to [client] (none: no session).
Future<Component> _wrap(
  ComponentTester tester, {
  _MapClient? client,
  RoleInfo role = _viewer,
  ReactorMode mode = ReactorMode.simple,
  void Function(FleetManager fleet)? onFleet,
}) async {
  Component screen = const HeatmapsScreen();
  if (client != null) {
    final FleetManager fleet = await fleetOf(<String, IReactClient>{
      'a': client,
    });
    addTearDown(fleet.dispose);
    onFleet?.call(fleet);
    screen = ServerSessionScope(session: fleet.sessionFor('a')!, child: screen);
  }
  return ArcaneThemeProvider(
    stylesheet: _sheet,
    child: ModeScope(
      mode: mode,
      onChanged: (ReactorMode _) {},
      child: RoleScope(
        role: role,
        child: ServerScope(
          snapshot: _snapshot(),
          state: ConnState.live,
          child: screen,
        ),
      ),
    ),
  );
}

Finder _dom(String tag, String attribute, String value) =>
    find.byComponentPredicate(
      (Component component) =>
          component is DomComponent &&
          component.tag == tag &&
          component.attributes?[attribute] == value,
    );

Finder _button(String label) => find.byComponentPredicate(
  (Component component) =>
      component is DomComponent &&
      component.tag == 'button' &&
      component.attributes?['aria-label'] == label,
);

void main() {
  testComponents('asks for a live connection without heatmap clients', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(await _wrap(tester));
    await tester.pump();
    expect(
      find.text('Grid heatmaps require a live connection.'),
      findsOneComponent,
    );
    expect(find.text('Spatial metrics'), findsNothing);
  });

  testComponents('one card: selects in the header, the plane, the cell below', (
    ComponentTester tester,
  ) async {
    final _MapClient heatmaps = _MapClient();
    tester.pumpComponent(await _wrap(tester, client: heatmaps));
    await tester.pump();
    await tester.pump();

    expect(_dom('select', 'aria-label', 'World'), findsOneComponent);
    expect(_dom('select', 'aria-label', 'Chunk heatmaps'), findsOneComponent);
    expect(_dom('div', 'role', 'grid'), findsOneComponent);
    expect(_button('Zoom in'), findsOneComponent);
    expect(_button('Zoom out'), findsOneComponent);
    expect(find.text('Chunk 0, 0'), findsOneComponent);
    expect(find.text('Copy position'), findsOneComponent);
    expect(
      heatmaps.requests.first,
      equals(('entity-pressure-heatmap', 'world', 0, 0, 8)),
    );
  });

  testComponents('a world added on the server shows without a reload', (
    ComponentTester tester,
  ) async {
    final _MapClient heatmaps = _MapClient();
    late FleetManager fleet;
    tester.pumpComponent(
      await _wrap(
        tester,
        client: heatmaps,
        onFleet: (FleetManager value) => fleet = value,
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('world_nether'), findsNothing);

    heatmaps.worldList = <WorldSettings>[
      ...heatmaps.worldList,
      const WorldSettings(
        key: 'world_nether',
        name: 'world_nether',
        pressureMode: PressureMode.normal,
        budgetMs: 50,
        panicMs: 45,
        releaseMs: 30,
      ),
    ];
    final ServerSession session = fleet.sessionFor('a')!;
    // The first counters are the baseline; the next move is a change.
    session.revisions
      ..update(ServerRevisions(<RevisionKind, int>{RevisionKind.worlds: 1}))
      ..update(ServerRevisions(<RevisionKind, int>{RevisionKind.worlds: 2}));
    await Future<void>.delayed(const Duration(milliseconds: 400));
    await tester.pump();

    expect(find.text('world_nether'), findsOneComponent);
  });

  testComponents('Simple leaves out the expert controls and tiles', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(await _wrap(tester, client: _MapClient()));
    await tester.pump();
    await tester.pump();

    expect(find.text('Center chunk X'), findsNothing);
    expect(_button('North'), findsNothing);
    expect(_button('Fit border'), findsNothing);
    expect(find.text('Spatial metrics'), findsNothing);
  });

  testComponents('Advanced adds the fields, compass and spatial tiles', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(
      await _wrap(tester, client: _MapClient(), mode: ReactorMode.advanced),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('Center chunk X'), findsOneComponent);
    expect(find.text('Radius (chunks)'), findsOneComponent);
    for (final String direction in <String>['North', 'South', 'East', 'West']) {
      expect(_button(direction), findsOneComponent);
    }
    expect(find.text('Spatial metrics'), findsOneComponent);
    expect(find.text('Entity pressure'), findsOneComponent);
  });

  testComponents('a click on a direction pans by one radius', (
    ComponentTester tester,
  ) async {
    final _MapClient heatmaps = _MapClient();
    tester.pumpComponent(
      await _wrap(tester, client: heatmaps, mode: ReactorMode.advanced),
    );
    await tester.pump();
    await tester.pump();
    await tester.click(_button('North'));
    await tester.pump();

    expect(
      heatmaps.requests.last,
      equals(('entity-pressure-heatmap', 'world', 0, -8, 8)),
    );
  });

  testComponents('teleport shows for an admin only', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(await _wrap(tester, client: _MapClient()));
    await tester.pump();
    await tester.pump();
    expect(find.text('Teleport'), findsNothing);
    expect(_dom('select', 'aria-label', 'Player'), findsNothing);

    tester.pumpComponent(
      await _wrap(tester, client: _MapClient(), role: _admin),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('Teleport'), findsOneComponent);
    expect(_dom('select', 'aria-label', 'Player'), findsOneComponent);
  });

  testComponents('a 403 on the grid reads as no access without a retry', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(
      await _wrap(tester, client: _MapClient(fail: const ReactForbidden())),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('This account does not have access.'), findsOneComponent);
    expect(find.text('Heatmap request failed'), findsNothing);
    expect(find.text('Retry'), findsNothing);
  });
}
