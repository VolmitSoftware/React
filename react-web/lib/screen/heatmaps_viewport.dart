/// The map's view (world, centre chunk, radius) and the moves on it, the
/// one grid request per view, and the poller that refreshes it.
library;

import 'dart:async';
import 'dart:math' as math;

import '../model/heatmap.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';

const Duration heatmapRefreshInterval = Duration(seconds: 10);
const int heatmapMinimumRadius = 1;
const int heatmapMaximumRadius = 1875000;

final class HeatmapViewport {
  final String world;
  final int centerChunkX;
  final int centerChunkZ;
  final int radius;

  const HeatmapViewport({
    required this.world,
    required this.centerChunkX,
    required this.centerChunkZ,
    required this.radius,
  });

  HeatmapViewport copyWith({
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) => HeatmapViewport(
    world: world ?? this.world,
    centerChunkX: centerChunkX ?? this.centerChunkX,
    centerChunkZ: centerChunkZ ?? this.centerChunkZ,
    radius: radius ?? this.radius,
  );
}

HeatmapViewport panHeatmapViewport(
  HeatmapViewport viewport, {
  required int horizontal,
  required int vertical,
}) {
  final int step = math.max(1, viewport.radius);
  return viewport.copyWith(
    centerChunkX: viewport.centerChunkX + horizontal * step,
    centerChunkZ: viewport.centerChunkZ + vertical * step,
  );
}

HeatmapViewport zoomHeatmapViewport(
  HeatmapViewport viewport, {
  required bool zoomIn,
}) {
  final int radius = zoomIn
      ? math.max(heatmapMinimumRadius, (viewport.radius / 2).ceil())
      : math.min(heatmapMaximumRadius, viewport.radius * 2);
  return viewport.copyWith(radius: radius);
}

HeatmapViewport fitHeatmapWorldBorder(
  HeatmapViewport viewport,
  HeatmapWorldBorder border,
) {
  final int minimumChunkX = (border.minimumBlockX / 16).floor();
  final int maximumChunkX = (border.maximumBlockX / 16).ceil() - 1;
  final int minimumChunkZ = (border.minimumBlockZ / 16).floor();
  final int maximumChunkZ = (border.maximumBlockZ / 16).ceil() - 1;
  final int centerChunkX = ((minimumChunkX + maximumChunkX) / 2).floor();
  final int centerChunkZ = ((minimumChunkZ + maximumChunkZ) / 2).floor();
  final int radius = math.max(
    math.max(
      (centerChunkX - minimumChunkX).abs(),
      (maximumChunkX - centerChunkX).abs(),
    ),
    math.max(
      (centerChunkZ - minimumChunkZ).abs(),
      (maximumChunkZ - centerChunkZ).abs(),
    ),
  );
  return viewport.copyWith(
    centerChunkX: centerChunkX,
    centerChunkZ: centerChunkZ,
    radius: radius.clamp(heatmapMinimumRadius, heatmapMaximumRadius),
  );
}

Future<HeatmapGrid> loadHeatmapGrid(
  IHeatmapClient client,
  String id,
  HeatmapViewport viewport,
) => client.heatmap(
  id,
  world: viewport.world,
  centerChunkX: viewport.centerChunkX,
  centerChunkZ: viewport.centerChunkZ,
  radius: viewport.radius,
);

HeatmapTarget initialHeatmapTarget(HeatmapGrid grid) {
  final int column =
      ((grid.centerChunkX - grid.originChunkX) / grid.cellSizeChunks)
          .floor()
          .clamp(0, grid.columns - 1);
  final int row =
      ((grid.centerChunkZ - grid.originChunkZ) / grid.cellSizeChunks)
          .floor()
          .clamp(0, grid.rows - 1);
  return HeatmapTarget(
    world: grid.world,
    originChunkX: grid.originChunkX + column * grid.cellSizeChunks,
    originChunkZ: grid.originChunkZ + row * grid.cellSizeChunks,
    sizeChunks: grid.cellSizeChunks,
  );
}

bool heatmapTeleportEnabled({
  required ConnState state,
  required RoleInfo? role,
  required bool clientAvailable,
  required bool playerSelected,
  required bool pending,
}) =>
    state == ConnState.live &&
    role?.isAdmin == true &&
    clientAvailable &&
    playerSelected &&
    !pending;

typedef HeatmapRefreshOperation = Future<void> Function();

final class HeatmapRefreshController {
  final Duration interval;

  Timer? _timer;
  HeatmapRefreshOperation? _operation;
  bool _pending = false;
  bool _running = false;
  bool _disposed = false;

  HeatmapRefreshController({this.interval = heatmapRefreshInterval}) {
    if (interval <= Duration.zero) {
      throw ArgumentError.value(interval, 'interval', 'Must be positive.');
    }
  }

  void start(HeatmapRefreshOperation operation) {
    if (_disposed) return;
    stop();
    _operation = operation;
    _timer = Timer.periodic(interval, (Timer _) => request());
  }

  void request() {
    if (_disposed || _operation == null) return;
    _pending = true;
    if (_running) return;
    _running = true;
    unawaited(_drain());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
    _operation = null;
    _pending = false;
  }

  void dispose() {
    if (_disposed) return;
    stop();
    _disposed = true;
  }

  Future<void> _drain() async {
    try {
      while (!_disposed && _operation != null && _pending) {
        _pending = false;
        final HeatmapRefreshOperation operation = _operation!;
        await operation();
      }
    } finally {
      _running = false;
      if (!_disposed && _operation != null && _pending) request();
    }
  }
}

/// A number the map's view fields edit.
enum HeatmapField { centerX, centerZ, radius }

/// [viewport] with [field] set to the number typed as [text]; null when
/// [text] is not a whole number, or not a radius from
/// [heatmapMinimumRadius] to [heatmapMaximumRadius].
HeatmapViewport? heatmapViewportWith(
  HeatmapViewport viewport,
  HeatmapField field,
  String text,
) {
  final int? value = int.tryParse(text.trim());
  if (value == null) return null;
  return switch (field) {
    HeatmapField.centerX => viewport.copyWith(centerChunkX: value),
    HeatmapField.centerZ => viewport.copyWith(centerChunkZ: value),
    HeatmapField.radius =>
      value < heatmapMinimumRadius || value > heatmapMaximumRadius
          ? null
          : viewport.copyWith(radius: value),
  };
}
