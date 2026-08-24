library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/screen/heatmaps.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

HeatmapGrid _grid(String id) => HeatmapGrid(
  id: id,
  label: id,
  world: 'world',
  centerChunkX: 0,
  centerChunkZ: 0,
  radius: 2,
  min: 0.0,
  max: 1.0,
  cells: const <HeatmapCell>[],
);

HeatmapSummary _summary(String id) => HeatmapSummary(id: id, label: id);

const HeatmapViewport _viewport = HeatmapViewport(
  world: 'minecraft:world_nether',
  centerX: 31,
  centerZ: -17,
  radius: 8,
);

class _FakeClient implements IHeatmapClient {
  final List<HeatmapSummary> summaries;
  final Map<String, HeatmapGrid> grids;
  final Set<String> throwIds;
  final List<(String, String?, int?, int?, int?)> requests =
      <(String, String?, int?, int?, int?)>[];

  _FakeClient({
    required this.summaries,
    required this.grids,
    this.throwIds = const <String>{},
  });

  @override
  Future<List<HeatmapSummary>> heatmaps() async => summaries;

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerX,
    int? centerZ,
    int? radius,
  }) async {
    requests.add((id, world, centerX, centerZ, radius));
    if (throwIds.contains(id)) {
      throw const ReactUnavailable('not found');
    }
    return grids[id]!;
  }
}

void main() {
  group('loadHeatmapGrids', () {
    test('fetches a grid for every summary', () async {
      final _FakeClient client = _FakeClient(
        summaries: <HeatmapSummary>[_summary('a'), _summary('b')],
        grids: <String, HeatmapGrid>{'a': _grid('a'), 'b': _grid('b')},
      );
      final List<HeatmapGrid> result = await loadHeatmapGrids(
        client,
        _viewport,
      );
      expect(result.length, equals(2));
      expect(result[0].id, equals('a'));
      expect(result[1].id, equals('b'));
      expect(
        client.requests,
        equals(<(String, String?, int?, int?, int?)>[
          ('a', 'minecraft:world_nether', 31, -17, 8),
          ('b', 'minecraft:world_nether', 31, -17, 8),
        ]),
      );
    });

    test('skips summaries whose grid fetch throws ReactUnavailable', () async {
      final _FakeClient client = _FakeClient(
        summaries: <HeatmapSummary>[_summary('good'), _summary('bad')],
        grids: <String, HeatmapGrid>{'good': _grid('good')},
        throwIds: <String>{'bad'},
      );
      final List<HeatmapGrid> result = await loadHeatmapGrids(
        client,
        _viewport,
      );
      expect(result.length, equals(1));
      expect(result[0].id, equals('good'));
    });

    test('returns empty when there are no summaries', () async {
      final _FakeClient client = _FakeClient(
        summaries: const <HeatmapSummary>[],
        grids: const <String, HeatmapGrid>{},
      );
      final List<HeatmapGrid> result = await loadHeatmapGrids(
        client,
        _viewport,
      );
      expect(result, isEmpty);
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
