import 'package:test/test.dart';

import 'package:react_web/model/snapshot.dart';

Map<String, dynamic> _meta(String id, String kind, int createdAt) =>
    <String, dynamic>{
      'id': id,
      'kind': kind,
      'label': 'Label $id',
      'createdAt': createdAt,
      'server': 'Survival SMP',
      'bytes': 2048,
      'summary': 'sum',
    };

void main() {
  test('the list comes newest first and keeps unknown kinds', () {
    final List<SnapshotMeta> list = snapshotListFromJson(<String, dynamic>{
      'snapshots': <Object?>[
        _meta('a', 'profile', 1000),
        _meta('b', 'heatmap', 3000),
        _meta('c', 'other', 2000),
        'junk',
      ],
    });
    expect(list.map((SnapshotMeta m) => m.id), <String>['b', 'c', 'a']);
    expect(list.first.kind, SnapshotKind.heatmap);
    expect(list[1].kind, isNull);
    expect(list.last.bytes, 2048);
    expect(
      SnapshotMeta.fromJson(list.first.toJson()).toJson(),
      list.first.toJson(),
    );
  });

  test('a detail parses its payload by kind', () {
    final SnapshotDetail profile = SnapshotDetail.fromJson(<String, dynamic>{
      'meta': _meta('a', 'profile', 1000),
      'data': <String, dynamic>{
        'profiler': <String, dynamic>{'running': false, 'samples': 3},
      },
    });
    expect(profile.profile!.status.samples, 3);
    expect(profile.heatmap, isNull);

    final SnapshotDetail heatmap = SnapshotDetail.fromJson(<String, dynamic>{
      'meta': _meta('b', 'heatmap', 1000),
      'data': <String, dynamic>{
        'id': 'entity-pressure-heatmap',
        'label': 'Entity Pressure',
        'world': 'world',
        'centerChunkX': 0,
        'centerChunkZ': 0,
        'radius': 1,
        'originChunkX': -1,
        'originChunkZ': -1,
        'width': 3,
        'height': 3,
        'cellSizeChunks': 1,
        'capturedAtMs': 1000,
        'spawnChunkX': 0,
        'spawnChunkZ': 0,
        'min': 0,
        'max': 4,
        'cells': <Map<String, dynamic>>[
          <String, dynamic>{
            'x': 0,
            'z': 0,
            'sizeChunks': 1,
            'score': 4,
            'averageScore': 2,
            'samples': 3,
          },
        ],
      },
    });
    expect(heatmap.profile, isNull);
    expect(heatmap.heatmap!.cells.single.score, 4);
  });

  test('requests carry kind, a trimmed label and the heatmap query', () {
    expect(const SnapshotRequest.profile().toJson(), <String, Object?>{
      'kind': 'profile',
    });
    expect(
      const SnapshotRequest.profile(label: '  Lag  ').toJson()['label'],
      'Lag',
    );
    expect(
      const SnapshotRequest.heatmap(
        HeatmapSnapshotQuery(
          id: 'chunk-load-gen-cost-map',
          world: 'world_nether',
          centerChunkX: -3,
          centerChunkZ: 9,
          radius: 12,
        ),
      ).toJson(),
      <String, Object?>{
        'kind': 'heatmap',
        'heatmap': <String, Object?>{
          'id': 'chunk-load-gen-cost-map',
          'world': 'world_nether',
          'centerChunkX': -3,
          'centerChunkZ': 9,
          'radius': 12,
        },
      },
    );
  });

  test('export names follow the route and its header', () {
    final SnapshotMeta meta = SnapshotMeta.fromJson(
      _meta(
        'a',
        'profile',
        DateTime(2026, 11, 20, 9, 5, 7).millisecondsSinceEpoch,
      ),
    );
    expect(
      snapshotFileName(meta),
      'react-profile-Survival-SMP-20261120-090507.json.gz',
    );
    expect(
      contentDispositionFileName(
        'attachment; filename="react-heatmap-s1-20261120-090507.json.gz"',
      ),
      'react-heatmap-s1-20261120-090507.json.gz',
    );
    expect(contentDispositionFileName('attachment; filename=a.gz'), 'a.gz');
    expect(contentDispositionFileName('inline'), isNull);
    expect(contentDispositionFileName(null), isNull);
  });

  test('sizes', () {
    expect(snapshotSizeText(512), '512 B');
    expect(snapshotSizeText(2048), '2.0 KB');
    expect(snapshotSizeText(3 * 1024 * 1024), '3.0 MB');
  });
}
