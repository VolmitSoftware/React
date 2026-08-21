library;

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

class _FakeClient implements IHeatmapClient {
  final List<HeatmapSummary> summaries;
  final Map<String, HeatmapGrid> grids;
  final Set<String> throwIds;

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
      final List<HeatmapGrid> result = await loadHeatmapGrids(client);
      expect(result.length, equals(2));
      expect(result[0].id, equals('a'));
      expect(result[1].id, equals('b'));
    });

    test('skips summaries whose grid fetch throws ReactUnavailable', () async {
      final _FakeClient client = _FakeClient(
        summaries: <HeatmapSummary>[_summary('good'), _summary('bad')],
        grids: <String, HeatmapGrid>{'good': _grid('good')},
        throwIds: <String>{'bad'},
      );
      final List<HeatmapGrid> result = await loadHeatmapGrids(client);
      expect(result.length, equals(1));
      expect(result[0].id, equals('good'));
    });

    test('returns empty when there are no summaries', () async {
      final _FakeClient client = _FakeClient(
        summaries: const <HeatmapSummary>[],
        grids: const <String, HeatmapGrid>{},
      );
      final List<HeatmapGrid> result = await loadHeatmapGrids(client);
      expect(result, isEmpty);
    });
  });
}
