/// Saving snapshots from the browser: an export as `.json.gz` and a
/// heatmap as PNG, drawn from its data.
library;

import '../model/heatmap.dart';
import '../model/snapshot.dart';
import 'snapshot_files_stub.dart'
    if (dart.library.js_interop) 'snapshot_files_web.dart'
    as platform;

/// Saves [export] under its name, compressing it first when the transport
/// carried plain JSON. False when the browser cannot.
Future<bool> saveSnapshotExport(SnapshotExport export) =>
    platform.saveSnapshotExport(export);

/// Draws [grid] (one pixel square per cell, scaled) and saves it as
/// [filename]. False when the browser cannot.
Future<bool> saveHeatmapPng(HeatmapGrid grid, String filename) =>
    platform.saveHeatmapPng(grid, filename);

/// `react-heatmap-<server>-<stamp>.png` beside the export's name.
String heatmapPngName(SnapshotMeta meta) =>
    snapshotFileName(meta).replaceFirst(RegExp(r'\.json\.gz$'), '.png');
