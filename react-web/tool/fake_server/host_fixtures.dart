import 'fixtures.dart';

/// What a fake server reports about its host and setup: plugin API packs,
/// the machine, the config tree and the heatmaps.
abstract final class HostFixtures {
  static Map<String, dynamic> pluginApiPacks(FakeServerProfile profile) =>
      <String, dynamic>{
        'folder': '/plugins/React/plugin-apis',
        'packs': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'example.adapt-earth-mover',
            'version': '1.0.0',
            'name': 'Adapt Earth Mover',
            'authors': <String>['Volmit Software'],
            'targetPlugin': 'Adapt',
            'targetVersion': '2.4.0',
            'targetVersions': <String>['*'],
            'enabled': true,
            'trusted': false,
            'state': profile.isCritical ? 'DEGRADED' : 'HEALTHY',
            'detail': profile.isCritical
                ? '2/3 metrics available'
                : 'all-metrics-available',
            'fileName': 'adapt-ability-ops.toml',
            'rawContent':
                'schema = "react.plugin-api/v1"\n'
                'id = "example.adapt-earth-mover"\n'
                'version = "1.0.0"\n'
                'name = "Adapt Earth Mover"\n'
                'authors = ["Volmit Software"]\n'
                'enabled = true\n'
                'trusted = false\n'
                'targetPlugin = "Adapt"\n'
                'targetVersions = ["*"]\n',
            'metrics': <Map<String, dynamic>>[
              _pluginApiMetric(
                id: 'execution-ops',
                name: 'Earth Mover Executions',
                source: 'integration',
                available: true,
              ),
              _pluginApiMetric(
                id: 'execution-timing',
                name: 'Earth Mover Execution Time',
                source: 'integration',
                available: !profile.isCritical,
              ),
              _pluginApiMetric(
                id: 'guard-checks',
                name: 'Earth Mover Guard Checks',
                source: 'integration',
                available: true,
              ),
            ],
          },
          <String, dynamic>{
            'id': 'example.placeholderapi-server',
            'version': '1.0.0',
            'name': 'PlaceholderAPI Server',
            'authors': <String>['Volmit Software'],
            'targetPlugin': 'PlaceholderAPI',
            'targetVersion': '2.11.6',
            'targetVersions': <String>['*'],
            'enabled': true,
            'trusted': true,
            'state': 'HEALTHY',
            'detail': 'all-metrics-available',
            'fileName': 'placeholderapi-server.toml',
            'rawContent':
                'schema = "react.plugin-api/v1"\n'
                'id = "example.placeholderapi-server"\n'
                'trusted = true\n',
            'metrics': <Map<String, dynamic>>[
              _pluginApiMetric(
                id: 'online-players',
                name: 'Online Players',
                source: 'placeholder',
                available: true,
              ),
            ],
          },
        ],
        'errors': profile.isCritical
            ? <Map<String, dynamic>>[
                <String, dynamic>{
                  'fileName': 'community-broken.toml',
                  'message': 'Unknown key root.legacyMode',
                },
              ]
            : <Map<String, dynamic>>[],
      };

  static Map<String, dynamic> _pluginApiMetric({
    required String id,
    required String name,
    required String source,
    required bool available,
  }) => <String, dynamic>{
    'id': id,
    'samplerId': 'plugin-api-example-$id',
    'displayName': name,
    'sourceType': source,
    'available': available,
    'availabilityReason': available ? '' : 'source-unavailable',
    'sampledAtMs': DateTime.now().millisecondsSinceEpoch,
    'sampleDurationMs': 1,
    'acceptedSamples': 40,
    'failedSamples': available ? 0 : 2,
    'quarantined': false,
  };

  /// Host details. Disk and network counters grow with [now], so the
  /// Environment page shows live rates.
  static Map<String, dynamic> environment(
    FakeServerProfile profile,
    DateTime now,
  ) {
    final int nowMs = now.millisecondsSinceEpoch;
    final int up = nowMs ~/ 1000 % 10000000;
    int grow(double healthy, double critical) =>
        (profile.level(healthy, critical) * up).round();
    return <String, dynamic>{
      'cpu': <String, Object?>{
        'model': 'AMD Ryzen 9 7950X 16-Core Processor',
        'cores': 16,
        'systemLoad': profile.isCritical ? '93%' : '48%',
        'processLoad': profile.isCritical ? '87%' : '36%',
      },
      'memory': <String, Object?>{
        'allocated': '16 GB',
        'used': profile.isCritical ? '13.4 GB' : '6.1 GB',
        'pressure': profile.isCritical ? '94%' : '61%',
      },
      'jvm': <String, Object?>{
        'vendor': 'Eclipse Adoptium',
        'version': '25.0.1',
        'uptime': '3d 14h 22m',
        'gc': 'G1 Young Generation',
      },
      'server': <String, Object?>{
        'brand': profile.brand,
        'minecraft': '1.21.11',
        'react': '1.7.4-qa',
        'onlineMode': true,
      },
      'disks': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'nvme0n1',
          'model': 'Samsung SSD 990 PRO 2TB',
          'sizeBytes': 1000000000000,
          'readBytes': 420000000000 + grow(18e6, 96e6),
          'writeBytes': 190000000000 + grow(9e6, 64e6),
          'reads': 1800000 + grow(140, 820),
          'writes': 950000 + grow(90, 510),
          'queueLength': profile.isCritical ? 14 : 1,
          'transferTimeMillis': 930000,
          'timestampMillis': nowMs,
        },
      ],
      'mounts': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'world-storage',
          'mount': '/srv/minecraft',
          'description': 'World storage',
          'type': 'ext4',
          'totalBytes': 1000000000000,
          'freeBytes': profile.isCritical ? 83000000000 : 470000000000,
          'usableBytes': profile.isCritical ? 78000000000 : 450000000000,
        },
        // What a macOS host lists besides its storage: system volumes,
        // read-only images (near full by design) and pseudo file systems.
        _mount('devfs', '/dev', 'devfs', 237000, 0),
        _mount(
          'Preboot',
          '/System/Volumes/Preboot',
          'apfs',
          494384795648,
          14800000000,
        ),
        _mount(
          'map auto_home',
          '/System/Volumes/Data/home',
          'autofs',
          0,
          0,
        ),
        _mount(
          'Cryptex',
          '/private/var/run/com.apple.security.cryptexd/mnt/'
              'com.apple.MobileAsset.MetalToolchain-v17.1.4097.0.F1C9A0B2',
          'apfs',
          1500000000,
          30000000,
        ),
      ],
      'network': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'eth0',
          'displayName': 'Primary network',
          'mtu': 1500,
          'macAddress': '02:00:00:00:00:01',
          'ipv4Addresses': <String>['10.0.0.42'],
          'ipv6Addresses': <String>[],
          'speedBitsPerSecond': 1000000000,
          'receivedBytes': 360000000000 + grow(4.2e6, 11e6),
          'sentBytes': 210000000000 + grow(6.8e6, 17e6),
          'receivedPackets': 64000000 + grow(5200, 14000),
          'sentPackets': 41000000 + grow(6100, 16000),
          'receiveErrors': 0,
          'sendErrors': 0,
          'receiveDrops': profile.isCritical ? 140 : 0,
          'collisions': 0,
          'timestampMillis': nowMs,
        },
      ],
    };
  }

  static Map<String, dynamic> config(FakeServerProfile profile) =>
      <String, dynamic>{
        'sections': <Map<String, dynamic>>[
          <String, dynamic>{
            'name': 'Performance',
            'nodes': <Map<String, dynamic>>[
              _knob(
                key: 'main.language',
                label: 'Language',
                type: 'enum',
                value: 'en_US',
                options: const <String>[
                  'en_US',
                  'de_DE',
                  'es_ES',
                  'fi_FI',
                  'fr_FR',
                  'he_IL',
                  'it_IT',
                  'ja-JP',
                  'ko_KR',
                  'lt_LT',
                  'nl_NL',
                  'pl_PL',
                  'pt_PT',
                  'ru_RU',
                  'tr_TR',
                  'vi_VI',
                  'zh_CN',
                  'zh_TW',
                ],
                doc: 'Language used by React for in-game messages.',
              ),
              _knob(
                key: 'tick-budget-ms',
                label: 'Tick Budget',
                type: 'double',
                value: profile.isCritical ? 42.0 : 45.0,
                doc: 'Target server tick budget in milliseconds.',
              ),
              _knob(
                key: 'sampling-interval',
                label: 'Sampling Interval',
                type: 'int',
                value: 20,
                doc: 'Ticks between aggregate sampling windows.',
              ),
              _knob(
                key: 'incident-mode-enabled',
                label: 'Incident Mode',
                type: 'bool',
                value: true,
              ),
            ],
          },
          <String, dynamic>{
            'name': 'Network',
            'nodes': <Map<String, dynamic>>[
              _knob(
                key: 'metrics-socket-rate',
                label: 'Metrics Socket Rate',
                type: 'int',
                value: 1,
                doc: 'Seconds between live WebSocket frames.',
              ),
              _knob(
                key: 'console-stream-enabled',
                label: 'Console Stream',
                type: 'bool',
                value: true,
              ),
            ],
          },
        ],
      };

  static List<Map<String, dynamic>> heatmapSummaries() =>
      <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 'chunk-load-gen-cost-map',
          'label': 'Chunk Load Cost',
        },
        <String, dynamic>{
          'id': 'entity-pressure-heatmap',
          'label': 'Entity Pressure',
        },
        <String, dynamic>{
          'id': 'redstone-activity-heatmap',
          'label': 'Redstone Activity',
        },
      ];

  static Map<String, dynamic>? heatmap(
    FakeServerProfile profile,
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) {
    final String label = switch (id) {
      'chunk-load-gen-cost-map' => 'Chunk Load Cost',
      'entity-pressure-heatmap' => 'Entity Pressure',
      'redstone-activity-heatmap' => 'Redstone Activity',
      _ => '',
    };
    if (label.isEmpty) return null;

    final String resolvedWorld =
        world ??
        (id == 'chunk-load-gen-cost-map'
            ? 'minecraft:world_nether'
            : 'minecraft:world');
    final int resolvedCenterX = centerChunkX ?? 148;
    final int resolvedCenterZ = centerChunkZ ?? -73;
    final int resolvedRadius = (radius ?? 8).clamp(1, 1875000);
    final int diameter = resolvedRadius * 2 + 1;
    int cellSizeChunks = 1;
    final int minimumCellSize = (diameter / 33).ceil();
    while (cellSizeChunks < minimumCellSize) {
      cellSizeChunks *= 2;
    }
    final int originChunkX =
        ((resolvedCenterX - resolvedRadius) / cellSizeChunks).floor() *
        cellSizeChunks;
    final int originChunkZ =
        ((resolvedCenterZ - resolvedRadius) / cellSizeChunks).floor() *
        cellSizeChunks;
    final int width =
        ((resolvedCenterX + resolvedRadius - originChunkX + 1) / cellSizeChunks)
            .ceil();
    final int height =
        ((resolvedCenterZ + resolvedRadius - originChunkZ + 1) / cellSizeChunks)
            .ceil();
    final List<Map<String, dynamic>> cells = <Map<String, dynamic>>[];
    for (int row = 0; row < height; row++) {
      for (int column = 0; column < width; column++) {
        final int chunkX = originChunkX + column * cellSizeChunks;
        final int chunkZ = originChunkZ + row * cellSizeChunks;
        final int distance =
            (chunkX - resolvedCenterX).abs() + (chunkZ - resolvedCenterZ).abs();
        final double baseline = id == 'chunk-load-gen-cost-map' ? 88.0 : 72.0;
        final double profileOffset = profile.isCritical ? 8.0 : -19.0;
        final double score =
            (baseline + profileOffset - (distance / cellSizeChunks) * 5.0)
                .clamp(0.0, 100.0)
                .toDouble();
        if (score <= 0.0 || (row + column) % 11 == 0) continue;
        cells.add(<String, dynamic>{
          'x': chunkX,
          'z': chunkZ,
          'sizeChunks': cellSizeChunks,
          'score': score,
          'averageScore': (score * 0.74).clamp(0.0, 100.0),
          'samples': 4 + (row * width + column) % 37,
        });
      }
    }
    return <String, dynamic>{
      'id': id,
      'label': label,
      'world': resolvedWorld,
      'centerChunkX': resolvedCenterX,
      'centerChunkZ': resolvedCenterZ,
      'radius': resolvedRadius,
      'originChunkX': originChunkX,
      'originChunkZ': originChunkZ,
      'width': width,
      'height': height,
      'cellSizeChunks': cellSizeChunks,
      'capturedAtMs': DateTime.now().millisecondsSinceEpoch,
      'spawnChunkX': 148,
      'spawnChunkZ': -73,
      'worldBorder': <String, dynamic>{
        'centerBlockX': 2368.0,
        'centerBlockZ': -1168.0,
        'sizeBlocks': 512.0,
      },
      'min': 0.0,
      'max': 100.0,
      'cells': cells,
    };
  }

  static Map<String, dynamic> _knob({
    required String key,
    required String label,
    required String type,
    required Object? value,
    List<String> options = const <String>[],
    String doc = '',
  }) => <String, dynamic>{
    'key': key,
    'label': label,
    'type': type,
    'value': value,
    'options': options,
    'doc': doc,
  };

  static Map<String, Object?> _mount(
    String name,
    String mount,
    String type,
    int totalBytes,
    int freeBytes,
  ) => <String, Object?>{
    'name': name,
    'mount': mount,
    'description': 'Local Disk',
    'type': type,
    'totalBytes': totalBytes,
    'freeBytes': freeBytes,
    'usableBytes': freeBytes,
  };
}
