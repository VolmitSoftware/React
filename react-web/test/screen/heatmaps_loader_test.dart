library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/screen/heatmaps.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';

HeatmapGrid _grid(String id) => HeatmapGrid(
  id: id,
  label: id,
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
  min: 0.0,
  max: 1.0,
  cells: const <HeatmapCell>[],
);

HeatmapSummary _summary(String id) => HeatmapSummary(id: id, label: id);

const HeatmapViewport _viewport = HeatmapViewport(
  world: 'minecraft:world_nether',
  centerChunkX: 31,
  centerChunkZ: -17,
  radius: 8,
);

class _FakeClient implements IHeatmapClient {
  final List<HeatmapSummary> summaries;
  final Map<String, HeatmapGrid> grids;
  final Set<String> throwIds;
  final List<(String, String?, int?, int?, int?)> requests =
      <(String, String?, int?, int?, int?)>[];
  int catalogRequests = 0;

  _FakeClient({
    required this.summaries,
    required this.grids,
    this.throwIds = const <String>{},
  });

  @override
  Future<List<HeatmapSummary>> heatmaps() async {
    catalogRequests++;
    return summaries;
  }

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) async {
    requests.add((id, world, centerChunkX, centerChunkZ, radius));
    if (throwIds.contains(id)) {
      throw const ReactUnavailable('not found');
    }
    return grids[id]!;
  }
}

void main() {
  group('loadHeatmapGrid', () {
    test('fetches only the selected layer for the applied viewport', () async {
      final _FakeClient client = _FakeClient(
        summaries: <HeatmapSummary>[_summary('a'), _summary('b')],
        grids: <String, HeatmapGrid>{'a': _grid('a'), 'b': _grid('b')},
      );
      final HeatmapGrid result = await loadHeatmapGrid(client, 'b', _viewport);
      expect(result.id, equals('b'));
      expect(client.catalogRequests, equals(0));
      expect(
        client.requests,
        equals(<(String, String?, int?, int?, int?)>[
          ('b', 'minecraft:world_nether', 31, -17, 8),
        ]),
      );
    });

    test('propagates a selected layer failure', () async {
      final _FakeClient client = _FakeClient(
        summaries: <HeatmapSummary>[_summary('bad')],
        grids: const <String, HeatmapGrid>{},
        throwIds: <String>{'bad'},
      );
      await expectLater(
        loadHeatmapGrid(client, 'bad', _viewport),
        throwsA(isA<ReactUnavailable>()),
      );
    });
  });

  group('heatmap viewport math', () {
    test('initial target aligns the requested center to its square cell', () {
      final HeatmapGrid aggregate = HeatmapGrid(
        id: 'aggregate',
        label: 'Aggregate',
        world: 'minecraft:overworld',
        centerChunkX: -1,
        centerChunkZ: -9,
        radius: 16,
        originChunkX: -16,
        originChunkZ: -16,
        width: 4,
        height: 4,
        cellSizeChunks: 8,
        capturedAtMs: 1750000000000,
        spawnChunkX: 0,
        spawnChunkZ: 0,
        min: 0,
        max: 1,
        cells: const <HeatmapCell>[],
      );
      final HeatmapTarget target = initialHeatmapTarget(aggregate);
      expect(target.originChunkX, equals(-8));
      expect(target.originChunkZ, equals(-16));
      expect(target.centerBlockX, equals(-64));
      expect(target.centerBlockZ, equals(-192));
    });

    test('pans north-up by one radius and keeps negative coordinates', () {
      final HeatmapViewport north = panHeatmapViewport(
        _viewport,
        horizontal: 0,
        vertical: -1,
      );
      final HeatmapViewport west = panHeatmapViewport(
        north,
        horizontal: -1,
        vertical: 0,
      );
      expect(north.centerChunkZ, equals(-25));
      expect(west.centerChunkX, equals(23));
    });

    test('zooms discretely and clamps to the supported radius bounds', () {
      expect(zoomHeatmapViewport(_viewport, zoomIn: true).radius, equals(4));
      expect(
        zoomHeatmapViewport(
          const HeatmapViewport(
            world: 'world',
            centerChunkX: 0,
            centerChunkZ: 0,
            radius: heatmapMaximumRadius,
          ),
          zoomIn: false,
        ).radius,
        equals(heatmapMaximumRadius),
      );
    });

    test('fits a negative-coordinate world border without truncation', () {
      final HeatmapViewport fitted = fitHeatmapWorldBorder(
        _viewport,
        const HeatmapWorldBorder(
          centerBlockX: -256,
          centerBlockZ: -512,
          sizeBlocks: 1024,
        ),
      );
      expect(fitted.centerChunkX, equals(-17));
      expect(fitted.centerChunkZ, equals(-33));
      expect(fitted.radius, equals(32));
    });
  });

  group('heatmap teleport gating', () {
    const RoleInfo admin = RoleInfo(
      role: 'admin',
      scopes: <String>['read', 'op:execute', 'admin'],
    );
    const RoleInfo operator = RoleInfo(
      role: 'operator',
      scopes: <String>['read', 'op:execute'],
    );

    test('allows only a live resolved admin target', () {
      expect(
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: admin,
          clientAvailable: true,
          playerSelected: true,
          pending: false,
        ),
        isTrue,
      );
      expect(
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: operator,
          clientAvailable: true,
          playerSelected: true,
          pending: false,
        ),
        isFalse,
      );
    });

    test('fails closed while offline, unresolved, unselected, or pending', () {
      final List<bool> results = <bool>[
        heatmapTeleportEnabled(
          state: ConnState.offline,
          role: admin,
          clientAvailable: true,
          playerSelected: true,
          pending: false,
        ),
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: null,
          clientAvailable: true,
          playerSelected: true,
          pending: false,
        ),
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: admin,
          clientAvailable: false,
          playerSelected: true,
          pending: false,
        ),
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: admin,
          clientAvailable: true,
          playerSelected: false,
          pending: false,
        ),
        heatmapTeleportEnabled(
          state: ConnState.live,
          role: admin,
          clientAvailable: true,
          playerSelected: true,
          pending: true,
        ),
      ];
      expect(results, everyElement(isFalse));
    });
  });

  group('HeatmapRefreshController', () {
    test(
      'coalesces overlapping requests to the latest pending state',
      () async {
        final HeatmapRefreshController controller = HeatmapRefreshController(
          interval: const Duration(hours: 1),
        );
        addTearDown(controller.dispose);
        final Completer<void> firstRefresh = Completer<void>();
        final List<int> observedVersions = <int>[];
        int version = 1;
        int active = 0;
        int maxActive = 0;

        controller.start(() async {
          final int observedVersion = version;
          observedVersions.add(observedVersion);
          active++;
          if (active > maxActive) maxActive = active;
          if (observedVersions.length == 1) await firstRefresh.future;
          active--;
        });

        controller.request();
        await Future<void>.delayed(Duration.zero);
        version = 2;
        controller.request();
        version = 3;
        controller.request();

        expect(observedVersions, equals(<int>[1]));
        firstRefresh.complete();
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);

        expect(observedVersions, equals(<int>[1, 3]));
        expect(maxActive, equals(1));
      },
    );

    test('dispose cancels periodic and queued refreshes', () async {
      final HeatmapRefreshController controller = HeatmapRefreshController(
        interval: const Duration(milliseconds: 5),
      );
      final Completer<void> activeRefresh = Completer<void>();
      int calls = 0;

      controller.start(() async {
        calls++;
        await activeRefresh.future;
      });
      controller.request();
      await Future<void>.delayed(const Duration(milliseconds: 20));

      expect(calls, equals(1));
      controller.dispose();
      activeRefresh.complete();
      await Future<void>.delayed(const Duration(milliseconds: 20));

      expect(calls, equals(1));
    });
  });
}
