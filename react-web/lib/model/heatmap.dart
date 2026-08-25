library;

final class HeatmapTarget {
  final String world;
  final int originChunkX;
  final int originChunkZ;
  final int sizeChunks;

  const HeatmapTarget({
    required this.world,
    required this.originChunkX,
    required this.originChunkZ,
    required this.sizeChunks,
  }) : assert(sizeChunks > 0);

  int get centerBlockX => originChunkX * 16 + sizeChunks * 8;
  int get centerBlockZ => originChunkZ * 16 + sizeChunks * 8;
  String get clipboardText => '$world $centerBlockX $centerBlockZ';
}

class HeatmapWorldBorder {
  final double centerBlockX;
  final double centerBlockZ;
  final double sizeBlocks;

  const HeatmapWorldBorder({
    required this.centerBlockX,
    required this.centerBlockZ,
    required this.sizeBlocks,
  });

  factory HeatmapWorldBorder.fromJson(Map<String, dynamic> json) =>
      HeatmapWorldBorder(
        centerBlockX: (json['centerBlockX'] as num).toDouble(),
        centerBlockZ: (json['centerBlockZ'] as num).toDouble(),
        sizeBlocks: (json['sizeBlocks'] as num).toDouble(),
      );

  double get minimumBlockX => centerBlockX - sizeBlocks / 2;
  double get maximumBlockX => centerBlockX + sizeBlocks / 2;
  double get minimumBlockZ => centerBlockZ - sizeBlocks / 2;
  double get maximumBlockZ => centerBlockZ + sizeBlocks / 2;
}

class HeatmapCell {
  final int x;
  final int z;
  final int sizeChunks;
  final double score;
  final double averageScore;
  final int samples;

  const HeatmapCell({
    required this.x,
    required this.z,
    required this.sizeChunks,
    required this.score,
    required this.averageScore,
    required this.samples,
  });

  factory HeatmapCell.fromJson(Map<String, dynamic> json) => HeatmapCell(
    x: (json['x'] as num).toInt(),
    z: (json['z'] as num).toInt(),
    sizeChunks: (json['sizeChunks'] as num).toInt(),
    score: (json['score'] as num).toDouble(),
    averageScore: (json['averageScore'] as num).toDouble(),
    samples: (json['samples'] as num).toInt(),
  );

  int get maximumChunkX => x + sizeChunks - 1;
  int get maximumChunkZ => z + sizeChunks - 1;
  int get minimumBlockX => x * 16;
  int get minimumBlockZ => z * 16;
  int get maximumBlockX => (x + sizeChunks) * 16 - 1;
  int get maximumBlockZ => (z + sizeChunks) * 16 - 1;

  bool containsChunk(int chunkX, int chunkZ) =>
      chunkX >= x &&
      chunkX <= maximumChunkX &&
      chunkZ >= z &&
      chunkZ <= maximumChunkZ;
}

class HeatmapSummary {
  final String id;
  final String label;

  const HeatmapSummary({required this.id, required this.label});

  factory HeatmapSummary.fromJson(Map<String, dynamic> json) =>
      HeatmapSummary(id: json['id'] as String, label: json['label'] as String);
}

class HeatmapGrid {
  final String id;
  final String label;
  final String world;
  final int centerChunkX;
  final int centerChunkZ;
  final int radius;
  final int originChunkX;
  final int originChunkZ;
  final int width;
  final int height;
  final int cellSizeChunks;
  final int capturedAtMs;
  final int spawnChunkX;
  final int spawnChunkZ;
  final HeatmapWorldBorder? worldBorder;
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
    required this.originChunkX,
    required this.originChunkZ,
    required this.width,
    required this.height,
    required this.cellSizeChunks,
    required this.capturedAtMs,
    required this.spawnChunkX,
    required this.spawnChunkZ,
    required this.min,
    required this.max,
    required this.cells,
    this.worldBorder,
  });

  factory HeatmapGrid.fromJson(Map<String, dynamic> json) {
    final Object? borderJson = json['worldBorder'];
    return HeatmapGrid(
      id: json['id'] as String,
      label: json['label'] as String,
      world: (json['world'] as String?) ?? '',
      centerChunkX: (json['centerChunkX'] as num).toInt(),
      centerChunkZ: (json['centerChunkZ'] as num).toInt(),
      radius: (json['radius'] as num).toInt(),
      originChunkX: (json['originChunkX'] as num).toInt(),
      originChunkZ: (json['originChunkZ'] as num).toInt(),
      width: (json['width'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      cellSizeChunks: (json['cellSizeChunks'] as num).toInt(),
      capturedAtMs: (json['capturedAtMs'] as num).toInt(),
      spawnChunkX: (json['spawnChunkX'] as num).toInt(),
      spawnChunkZ: (json['spawnChunkZ'] as num).toInt(),
      worldBorder: borderJson is Map<String, dynamic>
          ? HeatmapWorldBorder.fromJson(borderJson)
          : null,
      min: (json['min'] as num).toDouble(),
      max: (json['max'] as num).toDouble(),
      cells: (json['cells'] as List<dynamic>? ?? const <dynamic>[])
          .map(
            (dynamic value) =>
                HeatmapCell.fromJson(value as Map<String, dynamic>),
          )
          .toList(growable: false),
    );
  }

  int get columns => width;
  int get rows => height;
  int get maximumChunkX => originChunkX + width * cellSizeChunks - 1;
  int get maximumChunkZ => originChunkZ + height * cellSizeChunks - 1;
  DateTime get capturedAt => DateTime.fromMillisecondsSinceEpoch(capturedAtMs);

  Map<(int, int), HeatmapCell> indexCells() => <(int, int), HeatmapCell>{
    for (final HeatmapCell cell in cells) (cell.x, cell.z): cell,
  };

  HeatmapCell? cellAtOrigin(int chunkX, int chunkZ) {
    for (final HeatmapCell cell in cells) {
      if (cell.x == chunkX && cell.z == chunkZ) return cell;
    }
    return null;
  }

  HeatmapCell? cellContaining(int chunkX, int chunkZ) {
    for (final HeatmapCell cell in cells) {
      if (cell.containsChunk(chunkX, chunkZ)) return cell;
    }
    return null;
  }
}
