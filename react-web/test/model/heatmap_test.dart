library;

import 'package:test/test.dart';
import 'package:react_web/model/heatmap.dart';

void main() {
  group('HeatmapSummary', () {
    test('fromJson decodes id and label', () {
      final Map<String, dynamic> json = <String, dynamic>{
        'id': 'entity-pressure',
        'label': 'Entity Pressure',
      };
      final HeatmapSummary summary = HeatmapSummary.fromJson(json);
      expect(summary.id, equals('entity-pressure'));
      expect(summary.label, equals('Entity Pressure'));
    });
  });

  group('HeatmapCell', () {
    test('fromJson coerces int x/z and double score', () {
      final Map<String, dynamic> json = <String, dynamic>{
        'x': 1,
        'z': -2,
        'score': 3,
      };
      final HeatmapCell cell = HeatmapCell.fromJson(json);
      expect(cell.x, equals(1));
      expect(cell.z, equals(-2));
      expect(cell.score, equals(3.0));
    });
  });

  group('HeatmapGrid', () {
    final Map<String, dynamic> fullDto = <String, dynamic>{
      'id': 'entity-pressure',
      'label': 'Entity Pressure',
      'world': 'world',
      'centerChunkX': 10,
      'centerChunkZ': -5,
      'radius': 16,
      'min': 0.0,
      'max': 100.0,
      'cells': <Map<String, dynamic>>[
        <String, dynamic>{'x': 0, 'z': 0, 'score': 3.0},
        <String, dynamic>{'x': 1, 'z': 0, 'score': 7.0},
      ],
    };

    test('fromJson decodes full DTO', () {
      final HeatmapGrid grid = HeatmapGrid.fromJson(fullDto);
      expect(grid.id, equals('entity-pressure'));
      expect(grid.label, equals('Entity Pressure'));
      expect(grid.world, equals('world'));
      expect(grid.centerChunkX, equals(10));
      expect(grid.centerChunkZ, equals(-5));
      expect(grid.radius, equals(16));
      expect(grid.min, equals(0.0));
      expect(grid.max, equals(100.0));
      expect(grid.cells.length, equals(2));
      expect(grid.cells[1].score, equals(7.0));
    });

    test('fromJson tolerates empty cells', () {
      final Map<String, dynamic> dto = <String, dynamic>{
        'id': 'x',
        'label': 'X',
        'world': 'world',
        'centerChunkX': 0,
        'centerChunkZ': 0,
        'radius': 5,
        'min': 0.0,
        'max': 1.0,
        'cells': <Map<String, dynamic>>[],
      };
      final HeatmapGrid grid = HeatmapGrid.fromJson(dto);
      expect(grid.cells.isEmpty, isTrue);
      expect(grid.size, equals(5 * 2 + 1));
    });

    test('fromJson defaults missing world to empty string', () {
      final Map<String, dynamic> dto = <String, dynamic>{
        'id': 'x',
        'label': 'X',
        'centerChunkX': 0,
        'centerChunkZ': 0,
        'radius': 1,
        'min': 0.0,
        'max': 1.0,
        'cells': <Map<String, dynamic>>[],
      };
      final HeatmapGrid grid = HeatmapGrid.fromJson(dto);
      expect(grid.world, equals(''));
    });

    test('size and cellAt lookup', () {
      final Map<String, dynamic> dto = <String, dynamic>{
        'id': 'x',
        'label': 'X',
        'world': 'world',
        'centerChunkX': 0,
        'centerChunkZ': 0,
        'radius': 1,
        'min': 0.0,
        'max': 10.0,
        'cells': <Map<String, dynamic>>[
          <String, dynamic>{'x': 0, 'z': 0, 'score': 3.0},
          <String, dynamic>{'x': 1, 'z': 0, 'score': 7.0},
        ],
      };
      final HeatmapGrid grid = HeatmapGrid.fromJson(dto);
      expect(grid.size, equals(3));
      final HeatmapCell? found = grid.cellAt(0, 0);
      expect(found, isNotNull);
      expect(found!.score, equals(3.0));
      expect(grid.cellAt(5, 5), isNull);
    });
  });
}
