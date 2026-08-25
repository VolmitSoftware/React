library;

import 'package:react_web/model/heatmap.dart';
import 'package:test/test.dart';

Map<String, dynamic> _gridDto({List<Map<String, dynamic>>? cells}) =>
    <String, dynamic>{
      'id': 'entity-pressure',
      'label': 'Entity Pressure',
      'world': 'world',
      'centerChunkX': -17,
      'centerChunkZ': 31,
      'radius': 64,
      'originChunkX': -80,
      'originChunkZ': -32,
      'width': 17,
      'height': 17,
      'cellSizeChunks': 8,
      'capturedAtMs': 1750000000123,
      'spawnChunkX': -2,
      'spawnChunkZ': 3,
      'worldBorder': <String, dynamic>{
        'centerBlockX': -128.0,
        'centerBlockZ': 256.0,
        'sizeBlocks': 1024.0,
      },
      'min': 0.0,
      'max': 100.0,
      'cells':
          cells ??
          <Map<String, dynamic>>[
            <String, dynamic>{
              'x': -80,
              'z': -32,
              'sizeChunks': 8,
              'score': 7.0,
              'averageScore': 4.5,
              'samples': 12,
            },
          ],
    };

void main() {
  group('HeatmapTarget', () {
    test('uses the exact square center for negative aggregate chunks', () {
      const HeatmapTarget target = HeatmapTarget(
        world: 'minecraft:overworld',
        originChunkX: -8,
        originChunkZ: -16,
        sizeChunks: 8,
      );
      expect(target.centerBlockX, equals(-64));
      expect(target.centerBlockZ, equals(-192));
      expect(target.clipboardText, equals('minecraft:overworld -64 -192'));
    });

    test('single-chunk target resolves to block offset eight', () {
      const HeatmapTarget target = HeatmapTarget(
        world: 'minecraft:world_nether',
        originChunkX: -1,
        originChunkZ: 2,
        sizeChunks: 1,
      );
      expect(target.centerBlockX, equals(-8));
      expect(target.centerBlockZ, equals(40));
    });
  });

  group('HeatmapSummary', () {
    test('fromJson decodes id and label', () {
      final HeatmapSummary summary = HeatmapSummary.fromJson(<String, dynamic>{
        'id': 'entity-pressure',
        'label': 'Entity Pressure',
      });
      expect(summary.id, equals('entity-pressure'));
      expect(summary.label, equals('Entity Pressure'));
    });
  });

  group('HeatmapCell', () {
    test('decodes aggregate statistics and preserves negative ranges', () {
      final HeatmapCell cell = HeatmapCell.fromJson(<String, dynamic>{
        'x': -8,
        'z': -16,
        'sizeChunks': 8,
        'score': 9,
        'averageScore': 4.25,
        'samples': 17,
      });
      expect(cell.maximumChunkX, equals(-1));
      expect(cell.maximumChunkZ, equals(-9));
      expect(cell.minimumBlockX, equals(-128));
      expect(cell.maximumBlockX, equals(-1));
      expect(cell.minimumBlockZ, equals(-256));
      expect(cell.maximumBlockZ, equals(-129));
      expect(cell.score, equals(9.0));
      expect(cell.averageScore, equals(4.25));
      expect(cell.samples, equals(17));
      expect(cell.containsChunk(-1, -9), isTrue);
      expect(cell.containsChunk(0, -9), isFalse);
    });
  });

  group('HeatmapGrid', () {
    test('decodes the coordinate viewport and optional world border', () {
      final HeatmapGrid grid = HeatmapGrid.fromJson(_gridDto());
      expect(grid.originChunkX, equals(-80));
      expect(grid.originChunkZ, equals(-32));
      expect(grid.columns, equals(17));
      expect(grid.rows, equals(17));
      expect(grid.cellSizeChunks, equals(8));
      expect(grid.maximumChunkX, equals(55));
      expect(grid.maximumChunkZ, equals(103));
      expect(grid.capturedAt.millisecondsSinceEpoch, equals(1750000000123));
      expect(grid.spawnChunkX, equals(-2));
      expect(grid.spawnChunkZ, equals(3));
      expect(grid.worldBorder?.minimumBlockX, equals(-640.0));
      expect(grid.worldBorder?.maximumBlockZ, equals(768.0));
    });

    test('uses width and height as cell counts, not chunk spans', () {
      final HeatmapGrid grid = HeatmapGrid.fromJson(_gridDto());
      expect(grid.columns, equals(grid.width));
      expect(grid.maximumChunkX, equals(-80 + 17 * 8 - 1));
    });

    test('indexes sparse bucket origins and finds containing chunks', () {
      final HeatmapGrid grid = HeatmapGrid.fromJson(_gridDto());
      expect(grid.indexCells()[(-80, -32)]?.score, equals(7.0));
      expect(grid.cellAtOrigin(-80, -32), isNotNull);
      expect(grid.cellAtOrigin(-79, -31), isNull);
      expect(grid.cellContaining(-73, -25), isNotNull);
      expect(grid.cellContaining(-72, -25), isNull);
    });

    test('worldBorder is optional and sparse cells may be empty', () {
      final Map<String, dynamic> dto = _gridDto(
        cells: const <Map<String, dynamic>>[],
      )..remove('worldBorder');
      final HeatmapGrid grid = HeatmapGrid.fromJson(dto);
      expect(grid.worldBorder, isNull);
      expect(grid.cells, isEmpty);
    });
  });
}
