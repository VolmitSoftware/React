/// Snapshots: a profile or a heatmap frozen on the server, listed by
/// `GET /snapshots`, read whole by `GET /snapshots/{id}` and downloaded as
/// gzip JSON by `GET /snapshots/{id}/export`.
library;

import 'heatmap.dart';
import 'profiler.dart';

enum SnapshotKind {
  profile('profile'),
  heatmap('heatmap');

  const SnapshotKind(this.wire);

  final String wire;

  static SnapshotKind? parse(Object? value) {
    for (final SnapshotKind kind in values) {
      if (kind.wire == value) return kind;
    }
    return null;
  }
}

class SnapshotMeta {
  const SnapshotMeta({
    required this.id,
    required this.kind,
    required this.label,
    required this.createdAt,
    required this.server,
    required this.bytes,
    required this.summary,
  });

  final String id;

  /// Null for a kind this client does not know.
  final SnapshotKind? kind;
  final String label;
  final DateTime createdAt;
  final String server;
  final int bytes;
  final String summary;

  factory SnapshotMeta.fromJson(Map<String, dynamic> json) {
    final Object? created = json['createdAt'];
    final Object? bytes = json['bytes'];
    return SnapshotMeta(
      id: json['id'] as String? ?? '',
      kind: SnapshotKind.parse(json['kind']),
      label: json['label'] as String? ?? '',
      createdAt: DateTime.fromMillisecondsSinceEpoch(
        created is num ? created.toInt() : 0,
      ),
      server: json['server'] as String? ?? '',
      bytes: bytes is num ? bytes.toInt() : 0,
      summary: json['summary'] as String? ?? '',
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'kind': kind?.wire,
    'label': label,
    'createdAt': createdAt.millisecondsSinceEpoch,
    'server': server,
    'bytes': bytes,
    'summary': summary,
  };
}

List<SnapshotMeta> snapshotListFromJson(Map<String, dynamic> json) {
  final Object? list = json['snapshots'];
  return <SnapshotMeta>[
    if (list is List<dynamic>)
      for (final Object? entry in list)
        if (entry is Map<String, dynamic>) SnapshotMeta.fromJson(entry),
  ]..sort(
    (SnapshotMeta a, SnapshotMeta b) => b.createdAt.compareTo(a.createdAt),
  );
}

/// One snapshot with its frozen payload: [profile] or [heatmap] by kind.
class SnapshotDetail {
  const SnapshotDetail({
    required this.meta,
    required this.data,
    this.profile,
    this.heatmap,
  });

  final SnapshotMeta meta;
  final Map<String, dynamic> data;
  final ProfileData? profile;
  final HeatmapGrid? heatmap;

  factory SnapshotDetail.fromJson(Map<String, dynamic> json) {
    final Object? metaJson = json['meta'];
    final SnapshotMeta meta = SnapshotMeta.fromJson(
      metaJson is Map<String, dynamic> ? metaJson : const <String, dynamic>{},
    );
    final Object? dataJson = json['data'];
    final Map<String, dynamic> data = dataJson is Map<String, dynamic>
        ? dataJson
        : const <String, dynamic>{};
    return SnapshotDetail(
      meta: meta,
      data: data,
      profile: meta.kind == SnapshotKind.profile
          ? ProfileData.fromJson(data)
          : null,
      heatmap: meta.kind == SnapshotKind.heatmap && data.isNotEmpty
          ? HeatmapGrid.fromJson(data)
          : null,
    );
  }
}

/// The heatmap a heatmap snapshot freezes: the live heatmap GET's id and
/// query.
class HeatmapSnapshotQuery {
  const HeatmapSnapshotQuery({
    required this.id,
    required this.world,
    required this.centerChunkX,
    required this.centerChunkZ,
    required this.radius,
  });

  final String id;
  final String world;
  final int centerChunkX;
  final int centerChunkZ;
  final int radius;

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'world': world,
    'centerChunkX': centerChunkX,
    'centerChunkZ': centerChunkZ,
    'radius': radius,
  };
}

/// `POST /snapshots`.
class SnapshotRequest {
  const SnapshotRequest.profile({this.label})
    : kind = SnapshotKind.profile,
      heatmap = null;

  const SnapshotRequest.heatmap(HeatmapSnapshotQuery this.heatmap, {this.label})
    : kind = SnapshotKind.heatmap;

  final SnapshotKind kind;
  final String? label;
  final HeatmapSnapshotQuery? heatmap;

  Map<String, Object?> toJson() => <String, Object?>{
    'kind': kind.wire,
    if (label != null && label!.trim().isNotEmpty) 'label': label!.trim(),
    if (heatmap != null) 'heatmap': heatmap!.toJson(),
  };
}

/// A downloaded export: [bytes] are gzip JSON when [gzipped], else plain
/// JSON the browser compresses before saving (the relay carries JSON only).
class SnapshotExport {
  const SnapshotExport({
    required this.filename,
    required this.bytes,
    required this.gzipped,
  });

  final String filename;
  final List<int> bytes;
  final bool gzipped;
}

String _two(int value) => value.toString().padLeft(2, '0');

/// `react-<kind>-<server>-<yyyyMMdd-HHmmss>.json.gz`, the export route's
/// name, for when the browser cannot read its Content-Disposition.
String snapshotFileName(SnapshotMeta meta) {
  final DateTime at = meta.createdAt;
  final String server = meta.server
      .replaceAll(RegExp(r'[^A-Za-z0-9._-]+'), '-')
      .replaceAll(RegExp(r'^-+|-+$'), '');
  final String stamp =
      '${at.year}${_two(at.month)}${_two(at.day)}-'
      '${_two(at.hour)}${_two(at.minute)}${_two(at.second)}';
  return 'react-${meta.kind?.wire ?? 'snapshot'}-'
      '${server.isEmpty ? 'server' : server}-$stamp.json.gz';
}

/// The filename in a Content-Disposition header, or null.
String? contentDispositionFileName(String? header) {
  if (header == null) return null;
  final RegExpMatch? quoted = RegExp(r'filename="([^"]+)"').firstMatch(header);
  if (quoted != null) return quoted[1];
  final RegExpMatch? bare = RegExp(r'filename=([^;\s]+)').firstMatch(header);
  return bare?[1];
}

/// "1.4 KB".
String snapshotSizeText(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
}
