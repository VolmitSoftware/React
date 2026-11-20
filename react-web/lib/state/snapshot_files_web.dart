library;

import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

import '../model/heatmap.dart';
import '../model/snapshot.dart';

void _save(String url, String filename) {
  final web.HTMLAnchorElement anchor = web.HTMLAnchorElement()
    ..href = url
    ..download = filename;
  anchor.click();
}

Future<bool> saveSnapshotExport(SnapshotExport export) async {
  try {
    final web.Blob raw = web.Blob(
      <JSAny>[Uint8List.fromList(export.bytes).toJS].toJS,
      web.BlobPropertyBag(
        type: export.gzipped ? 'application/gzip' : 'application/json',
      ),
    );
    final web.Blob blob;
    if (export.gzipped) {
      blob = raw;
    } else {
      final web.CompressionStream gzip = web.CompressionStream('gzip');
      final web.ReadableStream packed = raw.stream().pipeThrough(
        web.ReadableWritablePair(
          readable: gzip.readable,
          writable: gzip.writable,
        ),
      );
      blob = await web.Response(packed).blob().toDart;
    }
    final String url = web.URL.createObjectURL(blob);
    _save(url, export.filename);
    web.URL.revokeObjectURL(url);
    return true;
  } on Object {
    return false;
  }
}

String _token(web.CSSStyleDeclaration style, String name, String fallback) {
  final String value = style.getPropertyValue(name).trim();
  return value.isEmpty ? fallback : value;
}

Future<bool> saveHeatmapPng(HeatmapGrid grid, String filename) async {
  try {
    final web.Element? themed =
        web.document.querySelector('#arcane-root') ??
        web.document.documentElement;
    final web.CSSStyleDeclaration style = web.window.getComputedStyle(
      themed!,
    );
    final String background = _token(style, '--background', '#0a0a0a');
    final String muted = _token(style, '--muted', '#262626');
    final String hot = _token(style, '--destructive', '#dc2626');
    final String text = _token(style, '--foreground', '#fafafa');
    final int columns = grid.columns < 1 ? 1 : grid.columns;
    final int rows = grid.rows < 1 ? 1 : grid.rows;
    final int cell = (768 / (columns > rows ? columns : rows)).floor().clamp(
      4,
      48,
    );
    const int header = 36;
    const int pad = 12;
    final web.HTMLCanvasElement canvas = web.HTMLCanvasElement()
      ..width = columns * cell + pad * 2
      ..height = rows * cell + pad * 2 + header;
    final web.CanvasRenderingContext2D context =
        canvas.getContext('2d')! as web.CanvasRenderingContext2D;
    context
      ..fillStyle = background.toJS
      ..fillRect(0, 0, canvas.width, canvas.height)
      ..fillStyle = text.toJS
      ..font = '14px sans-serif'
      ..fillText(
        '${grid.label} · ${grid.world} · ${grid.centerChunkX}, '
        '${grid.centerChunkZ} · r${grid.radius}',
        pad,
        pad + 14,
      );
    final double span = grid.max - grid.min;
    for (int z = 0; z < rows; z++) {
      for (int x = 0; x < columns; x++) {
        context
          ..globalAlpha = 1
          ..fillStyle = muted.toJS
          ..fillRect(
            pad + x * cell,
            pad + header + z * cell,
            cell - (cell > 6 ? 1 : 0),
            cell - (cell > 6 ? 1 : 0),
          );
      }
    }
    for (final HeatmapCell each in grid.cells) {
      final int x = (each.x - grid.originChunkX) ~/ grid.cellSizeChunks;
      final int z = (each.z - grid.originChunkZ) ~/ grid.cellSizeChunks;
      if (x < 0 || z < 0 || x >= columns || z >= rows) continue;
      final double level = span <= 0.0001
          ? (each.score > 0 ? 0.5 : 0)
          : ((each.score - grid.min) / span).clamp(0.0, 1.0);
      context
        ..globalAlpha = level
        ..fillStyle = hot.toJS
        ..fillRect(
          pad + x * cell,
          pad + header + z * cell,
          cell - (cell > 6 ? 1 : 0),
          cell - (cell > 6 ? 1 : 0),
        );
    }
    _save(canvas.toDataURL('image/png'), filename);
    return true;
  } on Object {
    return false;
  }
}
