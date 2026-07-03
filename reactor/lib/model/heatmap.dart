library;

class HeatmapCell {
  final int x;
  final int z;
  final double score;

  const HeatmapCell({
    required this.x,
    required this.z,
    required this.score,
  });

  factory HeatmapCell.fromJson(Map<String, dynamic> json) => HeatmapCell(
        x: (json['x'] as num).toInt(),
        z: (json['z'] as num).toInt(),
        score: (json['score'] as num).toDouble(),
      );
}

class HeatmapSummary {
  final String id;
  final String label;

  const HeatmapSummary({
    required this.id,
    required this.label,
  });

  factory HeatmapSummary.fromJson(Map<String, dynamic> json) => HeatmapSummary(
        id: json['id'] as String,
        label: json['label'] as String,
      );
}

class HeatmapGrid {
  final String id;
  final String label;
  final String world;
  final int centerChunkX;
  final int centerChunkZ;
  final int radius;
  final double min;
  final double max;
  final List<HeatmapCell> cells;

  const HeatmapGrid({
    required this.id,
    required this.label,
    required this.world,
    required this.centerChunkX,
    required this.centerChunkZ,
    required this.radius,
    required this.min,
    required this.max,
    required this.cells,
  });

  factory HeatmapGrid.fromJson(Map<String, dynamic> json) => HeatmapGrid(
        id: json['id'] as String,
        label: json['label'] as String,
        world: (json['world'] as String?) ?? '',
        centerChunkX: (json['centerChunkX'] as num).toInt(),
        centerChunkZ: (json['centerChunkZ'] as num).toInt(),
        radius: (json['radius'] as num).toInt(),
        min: (json['min'] as num).toDouble(),
        max: (json['max'] as num).toDouble(),
        cells: (json['cells'] as List<dynamic>? ?? const <dynamic>[])
            .map((dynamic e) =>
                HeatmapCell.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  int get size => radius * 2 + 1;

  HeatmapCell? cellAt(int chunkX, int chunkZ) {
    for (final HeatmapCell cell in cells) {
      if (cell.x == chunkX && cell.z == chunkZ) {
        return cell;
      }
    }
    return null;
  }
}
