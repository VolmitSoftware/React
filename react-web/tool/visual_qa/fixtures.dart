import 'dart:convert';

import 'package:crypto/crypto.dart';

enum VisualQaProfile {
  alpha(
    label: 'QA Alpha',
    tokenId: 'qa-alpha',
    tokenSig: 'visual',
    keySeed: 17,
  ),
  beta(label: 'QA Beta', tokenId: 'qa-beta', tokenSig: 'visual', keySeed: 17);

  final String label;
  final String tokenId;
  final String tokenSig;
  final int keySeed;

  const VisualQaProfile({
    required this.label,
    required this.tokenId,
    required this.tokenSig,
    required this.keySeed,
  });

  String get bearer => '$tokenId.$tokenSig';

  bool get isCritical => this == VisualQaProfile.beta;

  String get publicKey => base64Url
      .encode(
        List<int>.generate(48, (int index) => (keySeed + index * 7) % 256),
      )
      .replaceAll('=', '');

  String get fingerprint => sha256
      .convert(base64Url.decode(base64Url.normalize(publicKey)))
      .bytes
      .map((int byte) => byte.toRadixString(16).padLeft(2, '0'))
      .join();

  String pairingCode(int port) {
    final Map<String, Object?> payload = <String, Object?>{
      'directUrl': 'http://127.0.0.1:$port',
      'relayUrl': '',
      'serverPubKey': publicKey,
      'fingerprint': fingerprint,
      'tokenId': tokenId,
      'tokenSig': tokenSig,
    };
    final String encoded = base64Url
        .encode(utf8.encode(jsonEncode(payload)))
        .replaceAll('=', '');
    return 'RCT2.$encoded';
  }
}

typedef _MetricFixture = ({
  String id,
  String suffix,
  double alpha,
  double beta,
  double max,
});

abstract final class VisualQaFixtures {
  static const List<_MetricFixture> _metrics = <_MetricFixture>[
    (id: 'ticks-per-second', suffix: 'TPS', alpha: 19.9, beta: 8.9, max: 20.0),
    (id: 'tick-time', suffix: 'ms', alpha: 38.2, beta: 67.8, max: 100.0),
    (id: 'players', suffix: '', alpha: 42.0, beta: 117.0, max: 200.0),
    (id: 'entities', suffix: '', alpha: 1840.0, beta: 6270.0, max: 8000.0),
    (id: 'chunks', suffix: '', alpha: 912.0, beta: 2214.0, max: 3000.0),
    (
      id: 'memory-used',
      suffix: 'MB',
      alpha: 6230.0,
      beta: 13740.0,
      max: 16384.0,
    ),
    (id: 'gc-time-percent', suffix: '%', alpha: 3.1, beta: 18.7, max: 30.0),
    (id: 'incident-score', suffix: '', alpha: 22.0, beta: 72.0, max: 100.0),
    (id: 'tick-ms-p50', suffix: 'ms', alpha: 34.6, beta: 59.4, max: 100.0),
    (id: 'tick-ms-p95', suffix: 'ms', alpha: 44.8, beta: 82.6, max: 120.0),
    (id: 'tick-ms-p99', suffix: 'ms', alpha: 51.2, beta: 109.3, max: 150.0),
    (id: 'tick-spike-rate', suffix: '/min', alpha: 1.7, beta: 14.2, max: 25.0),
    (id: 'top-world-mspt', suffix: 'ms', alpha: 19.8, beta: 43.7, max: 60.0),
    (id: 'top-chunk-cost', suffix: 'ms', alpha: 2.4, beta: 12.8, max: 20.0),
    (
      id: 'memory-free',
      suffix: 'MB',
      alpha: 10154.0,
      beta: 2644.0,
      max: 16384.0,
    ),
    (
      id: 'memory-used-after-gc',
      suffix: 'MB',
      alpha: 4890.0,
      beta: 11960.0,
      max: 16384.0,
    ),
    (id: 'memory-pressure', suffix: '%', alpha: 61.0, beta: 94.0, max: 100.0),
    (id: 'gc-pause-p95', suffix: 'ms', alpha: 24.0, beta: 146.0, max: 200.0),
    (
      id: 'memory-garbage',
      suffix: 'MB/s',
      alpha: 112.0,
      beta: 487.0,
      max: 600.0,
    ),
    (
      id: 'entity-ai-active-count',
      suffix: '',
      alpha: 734.0,
      beta: 3184.0,
      max: 5000.0,
    ),
    (
      id: 'entities-spawns',
      suffix: '/min',
      alpha: 86.0,
      beta: 244.0,
      max: 350.0,
    ),
    (id: 'player-ping-p95', suffix: 'ms', alpha: 47.0, beta: 238.0, max: 300.0),
    (id: 'ping-jitter', suffix: 'ms', alpha: 8.0, beta: 54.0, max: 80.0),
    (id: 'chunks-loaded', suffix: '/min', alpha: 74.0, beta: 219.0, max: 300.0),
    (
      id: 'chunks-generated',
      suffix: '/min',
      alpha: 12.0,
      beta: 48.0,
      max: 80.0,
    ),
    (id: 'chunk-load-ms', suffix: 'ms', alpha: 3.7, beta: 16.4, max: 25.0),
    (id: 'chunk-gen-ms', suffix: 'ms', alpha: 8.4, beta: 31.7, max: 45.0),
    (
      id: 'world-save-event-interval',
      suffix: 'ms',
      alpha: 31.0,
      beta: 164.0,
      max: 220.0,
    ),
    (
      id: 'pdc-write-batcher',
      suffix: 'writes',
      alpha: 94.0,
      beta: 331.0,
      max: 450.0,
    ),
    (id: 'redstone', suffix: '/tick', alpha: 146.0, beta: 581.0, max: 800.0),
    (
      id: 'redstone-burst-rate',
      suffix: '/min',
      alpha: 4.0,
      beta: 29.0,
      max: 40.0,
    ),
    (
      id: 'redstone-event-span',
      suffix: 'ms',
      alpha: 2.8,
      beta: 15.6,
      max: 24.0,
    ),
    (id: 'hopper', suffix: '/tick', alpha: 93.0, beta: 413.0, max: 600.0),
    (id: 'hopper-event-span', suffix: 'ms', alpha: 1.9, beta: 9.4, max: 15.0),
    (
      id: 'hopper-chain-coalescing',
      suffix: '%',
      alpha: 81.0,
      beta: 34.0,
      max: 100.0,
    ),
    (id: 'physics', suffix: '/tick', alpha: 72.0, beta: 317.0, max: 450.0),
    (id: 'physics-event-span', suffix: 'ms', alpha: 1.6, beta: 7.8, max: 12.0),
    (id: 'fluid', suffix: '/tick', alpha: 51.0, beta: 286.0, max: 400.0),
    (id: 'fluid-event-span', suffix: 'ms', alpha: 1.2, beta: 6.9, max: 10.0),
    (
      id: 'crop-fast-forward',
      suffix: '/tick',
      alpha: 29.0,
      beta: 113.0,
      max: 180.0,
    ),
    (
      id: 'lazy-gravity-skipped',
      suffix: '/tick',
      alpha: 41.0,
      beta: 207.0,
      max: 280.0,
    ),
    (
      id: 'spawner-light-cache-skipped',
      suffix: '/tick',
      alpha: 18.0,
      beta: 97.0,
      max: 150.0,
    ),
    (
      id: 'explosion-packet-reduction',
      suffix: '%',
      alpha: 68.0,
      beta: 43.0,
      max: 100.0,
    ),
    (
      id: 'event-handles-per-tick',
      suffix: '',
      alpha: 718.0,
      beta: 2144.0,
      max: 3000.0,
    ),
    (id: 'events-listeners', suffix: '', alpha: 284.0, beta: 391.0, max: 500.0),
    (id: 'event-time', suffix: 'ms', alpha: 5.7, beta: 21.4, max: 30.0),
    (
      id: 'react-async-tick-time',
      suffix: 'ms',
      alpha: 3.8,
      beta: 12.7,
      max: 20.0,
    ),
    (
      id: 'react-sync-tick-time',
      suffix: 'ms',
      alpha: 4.1,
      beta: 18.3,
      max: 25.0,
    ),
    (id: 'react-jobs-queue', suffix: '', alpha: 7.0, beta: 94.0, max: 120.0),
    (
      id: 'react-job-queue-time',
      suffix: 'ms',
      alpha: 2.1,
      beta: 24.8,
      max: 35.0,
    ),
    (id: 'react-job-budget', suffix: 'ms', alpha: 8.0, beta: 8.0, max: 12.0),
    (
      id: 'processor-system-load',
      suffix: '%',
      alpha: 48.0,
      beta: 93.0,
      max: 100.0,
    ),
    (
      id: 'processor-process-load',
      suffix: '%',
      alpha: 36.0,
      beta: 87.0,
      max: 100.0,
    ),
    (id: 'processor-outside', suffix: '%', alpha: 12.0, beta: 6.0, max: 100.0),
    (
      id: 'scheduler-backlog',
      suffix: 'jobs',
      alpha: 6.0,
      beta: 127.0,
      max: 180.0,
    ),
    (
      id: 'backlog-growth-rate',
      suffix: '/min',
      alpha: 0.4,
      beta: 18.6,
      max: 25.0,
    ),
    (
      id: 'per-world-tick-time',
      suffix: 'ms',
      alpha: 18.6,
      beta: 42.8,
      max: 60.0,
    ),
    (
      id: 'adapt-ability-checks-per-tick',
      suffix: '',
      alpha: 96.0,
      beta: 244.0,
      max: 320.0,
    ),
    (
      id: 'adapt-ability-ops',
      suffix: '/s',
      alpha: 128.0,
      beta: 391.0,
      max: 500.0,
    ),
    (id: 'adapt-session-load', suffix: 'ms', alpha: 2.3, beta: 8.6, max: 12.0),
    (
      id: 'adapt-world-policy-latency',
      suffix: 'ms',
      alpha: 1.7,
      beta: 7.9,
      max: 12.0,
    ),
    (
      id: 'iris-chunks-per-second',
      suffix: '/s',
      alpha: 18.0,
      beta: 7.0,
      max: 50.0,
    ),
    (
      id: 'iris-generation-total-ms',
      suffix: 'ms',
      alpha: 7.2,
      beta: 29.4,
      max: 40.0,
    ),
    (
      id: 'iris-pregen-queue',
      suffix: 'chunks',
      alpha: 24.0,
      beta: 162.0,
      max: 220.0,
    ),
    (
      id: 'wormholes-block-changes',
      suffix: '/s',
      alpha: 82.0,
      beta: 217.0,
      max: 300.0,
    ),
    (
      id: 'wormholes-packets',
      suffix: '/s',
      alpha: 418.0,
      beta: 1194.0,
      max: 1600.0,
    ),
    (id: 'wormholes-portals', suffix: '', alpha: 14.0, beta: 37.0, max: 50.0),
    (
      id: 'wormholes-projection-observers',
      suffix: '',
      alpha: 21.0,
      beta: 58.0,
      max: 80.0,
    ),
    (
      id: 'wormholes-projection-render-ms',
      suffix: 'ms',
      alpha: 3.4,
      beta: 13.8,
      max: 20.0,
    ),
    (
      id: 'wormholes-projections-active',
      suffix: '',
      alpha: 9.0,
      beta: 28.0,
      max: 40.0,
    ),
    (
      id: 'wormholes-spoofed-entities',
      suffix: '',
      alpha: 44.0,
      beta: 131.0,
      max: 180.0,
    ),
    (
      id: 'wormholes-traversals',
      suffix: '/min',
      alpha: 17.0,
      beta: 51.0,
      max: 70.0,
    ),
    (
      id: 'entity-pressure-heatmap',
      suffix: '%',
      alpha: 37.0,
      beta: 89.0,
      max: 100.0,
    ),
    (
      id: 'chunk-load-gen-cost-map',
      suffix: 'ms',
      alpha: 6.4,
      beta: 27.1,
      max: 40.0,
    ),
    (
      id: 'chunk-sampler-map',
      suffix: 'chunks',
      alpha: 73.0,
      beta: 187.0,
      max: 240.0,
    ),
    (
      id: 'redstone-activity-heatmap',
      suffix: '%',
      alpha: 31.0,
      beta: 84.0,
      max: 100.0,
    ),
    (
      id: 'hopper-container-throughput-map',
      suffix: '/s',
      alpha: 142.0,
      beta: 482.0,
      max: 600.0,
    ),
    (
      id: 'tick-spike-origin-replay-map',
      suffix: 'events',
      alpha: 3.0,
      beta: 28.0,
      max: 40.0,
    ),
    (
      id: 'plugin-event-impact-pie-map',
      suffix: '%',
      alpha: 28.0,
      beta: 66.0,
      max: 100.0,
    ),
    (
      id: 'plugin-event-impact-list-map',
      suffix: 'plugins',
      alpha: 12.0,
      beta: 19.0,
      max: 30.0,
    ),
    (
      id: 'iris-biome-chunk-share-pie-map',
      suffix: '%',
      alpha: 46.0,
      beta: 57.0,
      max: 100.0,
    ),
    (
      id: 'iris-world-chunk-share-pie-map',
      suffix: '%',
      alpha: 64.0,
      beta: 79.0,
      max: 100.0,
    ),
  ];

  static Map<String, dynamic> identity(VisualQaProfile profile) =>
      <String, dynamic>{
        'serverName': profile.label,
        'version': profile.isCritical ? '1.7.4-qa-beta' : '1.7.4-qa-alpha',
        'folia': profile == VisualQaProfile.alpha,
        'serverId': profile.fingerprint,
      };

  static Map<String, dynamic> capabilities(VisualQaProfile profile) =>
      <String, dynamic>{
        'protocolVersion': 2,
        'serverFingerprint': profile.fingerprint,
        'relayAvailable': false,
      };

  static Map<String, dynamic> role(VisualQaProfile profile) =>
      <String, dynamic>{
        'role': 'admin',
        'scopes': <String>['read', 'op:execute', 'console:execute', 'admin'],
      };

  static List<Map<String, dynamic>> players(VisualQaProfile profile) =>
      <Map<String, dynamic>>[
        <String, dynamic>{
          'id': profile == VisualQaProfile.alpha
              ? '11111111-1111-4111-8111-111111111111'
              : '22222222-2222-4222-8222-222222222222',
          'name': profile == VisualQaProfile.alpha ? 'Alex' : 'Morgan',
        },
        <String, dynamic>{
          'id': profile == VisualQaProfile.alpha
              ? '33333333-3333-4333-8333-333333333333'
              : '44444444-4444-4444-8444-444444444444',
          'name': profile == VisualQaProfile.alpha ? 'Builder' : 'Scout',
        },
      ];

  static Map<String, dynamic> metrics(VisualQaProfile profile) =>
      <String, dynamic>{
        'data': <String, dynamic>{
          'sequence': 1,
          'capturedAtMs': DateTime.now().millisecondsSinceEpoch,
          'samplers': metricData(profile),
        },
      };

  static List<Map<String, dynamic>> metricData(VisualQaProfile profile) {
    return _metrics.map((_MetricFixture fixture) {
      final double value = profile.isCritical ? fixture.beta : fixture.alpha;
      return <String, dynamic>{
        'id': fixture.id,
        'name': _displayName(fixture.id),
        'suffix': fixture.suffix,
        'value': value,
        'display': _displayValue(value),
        'available': true,
      };
    }).toList();
  }

  static List<Map<String, dynamic>> metricHistoryCatalog(
    VisualQaProfile profile,
  ) {
    final int now = DateTime.now().millisecondsSinceEpoch;
    return metricData(profile).map((Map<String, dynamic> metric) {
      return <String, dynamic>{
        'id': metric['id'],
        'name': metric['name'],
        'suffix': metric['suffix'],
        'firstTimestampMs': now - const Duration(days: 30).inMilliseconds,
        'lastTimestampMs': now,
        'active': true,
      };
    }).toList();
  }

  static Map<String, dynamic>? metricHistoryPage(
    VisualQaProfile profile,
    String id,
    int from,
    int to,
  ) {
    final _MetricFixture? fixture = _metric(id);
    if (fixture == null) return null;
    final double value = profile.isCritical ? fixture.beta : fixture.alpha;
    final List<double> history = _history(value, fixture.max, id.hashCode);
    final int resolution = ((to - from) ~/ history.length).clamp(1000, 3600000);
    final List<List<num>> points = <List<num>>[];
    for (int index = 0; index < history.length; index++) {
      final int timestamp = from + index * resolution;
      if (timestamp >= to) break;
      final double sample = history[index];
      points.add(<num>[timestamp, sample, sample, sample, sample, 1]);
    }
    return <String, dynamic>{
      'requestedFromMs': from,
      'requestedToMs': to,
      'pageFromMs': from,
      'pageToMs': to,
      'actualResolutionMs': resolution,
      'throughSequence': 1,
      'throughMs': to,
      'nextCursor': null,
      'series': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': fixture.id,
          'name': _displayName(fixture.id),
          'suffix': fixture.suffix,
          'points': points,
        },
      ],
    };
  }

  static _MetricFixture? _metric(String id) {
    for (final _MetricFixture fixture in _metrics) {
      if (fixture.id == id) return fixture;
    }
    return null;
  }

  static List<Map<String, dynamic>> features(
    VisualQaProfile profile,
  ) => <Map<String, dynamic>>[
    _control(
      id: 'mob-stacking',
      name: 'Mob Stacking',
      category: 'Entity Population',
      enabled: true,
      description: 'Coalesces nearby compatible mobs under load.',
      knobs: <Map<String, dynamic>>[
        _knob(
          key: 'radius',
          label: 'Stack Radius',
          type: 'double',
          value: 8.0,
          doc: 'Maximum search radius in blocks.',
        ),
        _knob(key: 'max-stack', label: 'Maximum Stack', type: 'int', value: 48),
      ],
    ),
    _control(
      id: 'dynamic-view-distance',
      name: 'Dynamic View Distance',
      category: 'Governors',
      enabled: true,
      description: 'Adjusts view distance against the live tick budget.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'minimum', label: 'Minimum Distance', type: 'int', value: 5),
        _knob(
          key: 'maximum',
          label: 'Maximum Distance',
          type: 'int',
          value: 12,
        ),
      ],
    ),
    _control(
      id: 'pathfinder-budget',
      name: 'Pathfinder Budget',
      category: 'Governors',
      enabled: true,
      description: 'Caps navigation work when the scheduler is saturated.',
      knobs: <Map<String, dynamic>>[
        _knob(
          key: 'budget-ms',
          label: 'Budget',
          type: 'double',
          value: profile.isCritical ? 2.5 : 4.0,
          doc: 'Maximum pathfinding time per tick.',
        ),
      ],
    ),
    _control(
      id: 'incident-mode',
      name: 'Incident Mode',
      category: 'Governors',
      enabled: profile.isCritical,
      description: 'Coordinates protective governors during an incident.',
      knobs: <Map<String, dynamic>>[
        _knob(
          key: 'activation-score',
          label: 'Activation Score',
          type: 'double',
          value: 70.0,
        ),
      ],
    ),
    _control(
      id: 'adaptive-entity-sleep',
      name: 'Adaptive Entity Sleep',
      category: 'Entity Population',
      enabled: true,
      description: 'Sleeps distant entity AI without despawning entities.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'distance', label: 'Sleep Distance', type: 'int', value: 48),
      ],
    ),
    _control(
      id: 'chunk-save-coalescing',
      name: 'Chunk Save Coalescing',
      category: 'World IO',
      enabled: true,
      description: 'Batches compatible chunk persistence work.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'batch-size', label: 'Batch Size', type: 'int', value: 32),
      ],
    ),
  ];

  static List<Map<String, dynamic>> tweaks(
    VisualQaProfile profile,
  ) => <Map<String, dynamic>>[
    _control(
      id: 'async-chunk-io',
      name: 'Async Chunk IO',
      category: 'World IO',
      enabled: true,
      description: 'Moves safe chunk serialization work off the tick path.',
      knobs: <Map<String, dynamic>>[
        _knob(
          key: 'workers',
          label: 'Workers',
          type: 'int',
          value: profile.isCritical ? 6 : 4,
        ),
        _knob(
          key: 'priority',
          label: 'Priority',
          type: 'enum',
          value: 'balanced',
          options: <String>['latency', 'balanced', 'throughput'],
        ),
      ],
    ),
    _control(
      id: 'lazy-gravity',
      name: 'Lazy Gravity',
      category: 'Mechanics',
      enabled: true,
      description: 'Skips redundant gravity checks in inactive chunks.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'idle-ticks', label: 'Idle Ticks', type: 'int', value: 40),
      ],
    ),
    _control(
      id: 'hopper-chain-coalescing',
      name: 'Hopper Chain Coalescing',
      category: 'Mechanics',
      enabled: !profile.isCritical,
      description: 'Combines sequential inventory transfer checks.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'chain-limit', label: 'Chain Limit', type: 'int', value: 16),
      ],
    ),
    _control(
      id: 'packet-deduplication',
      name: 'Packet Deduplication',
      category: 'Network',
      enabled: true,
      description: 'Suppresses identical outbound state updates.',
      knobs: <Map<String, dynamic>>[
        _knob(key: 'window-ms', label: 'Window', type: 'int', value: 40),
      ],
    ),
  ];

  static List<Map<String, dynamic>> worlds(VisualQaProfile profile) =>
      <Map<String, dynamic>>[
        <String, dynamic>{
          'key': 'minecraft:world',
          'name': 'world',
          'pressureMode': profile.isCritical ? 'PRESSURE' : 'NORMAL',
          'budgetMs': 32.0,
          'panicMs': 48.0,
          'releaseMs': 28.0,
        },
        <String, dynamic>{
          'key': 'minecraft:world_nether',
          'name': 'world_nether',
          'pressureMode': 'PRESSURE',
          'budgetMs': 18.0,
          'panicMs': 30.0,
          'releaseMs': 15.0,
        },
        <String, dynamic>{
          'key': 'minecraft:world_the_end',
          'name': 'world_the_end',
          'pressureMode': profile.isCritical ? 'PANIC' : 'NORMAL',
          'budgetMs': 12.0,
          'panicMs': 22.0,
          'releaseMs': 10.0,
        },
      ];

  static List<Map<String, dynamic>> actions() => <Map<String, dynamic>>[
    <String, dynamic>{
      'id': 'gc',
      'name': 'Force GC',
      'description': 'Queues a controlled garbage collection request.',
      'destructive': false,
      'params': <Map<String, dynamic>>[],
    },
    <String, dynamic>{
      'id': 'purge-entities',
      'name': 'Purge Nearby Entities',
      'description': 'Queues removal of eligible entities in a bounded radius.',
      'destructive': true,
      'params': <Map<String, dynamic>>[
        <String, dynamic>{
          'key': 'radius',
          'label': 'Radius',
          'type': 'int',
          'required': true,
          'default': 64,
          'options': <String>[],
        },
      ],
    },
    <String, dynamic>{
      'id': 'refresh-chunks',
      'name': 'Refresh Chunks',
      'description': 'Queues a client refresh for currently watched chunks.',
      'destructive': false,
      'params': <Map<String, dynamic>>[
        <String, dynamic>{
          'key': 'world',
          'label': 'World',
          'type': 'enum',
          'required': true,
          'default': 'world',
          'options': <String>['world', 'world_nether', 'world_the_end'],
        },
      ],
    },
  ];

  static Map<String, dynamic> incidents(VisualQaProfile profile) =>
      <String, dynamic>{
        'score': profile.isCritical ? 72.0 : 22.0,
        'scoreAvailable': true,
        'sampledAtMs': 1770000000000,
        'state': profile.isCritical ? 'CRITICAL' : 'NORMAL',
        'contributors': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'scheduler-backlog',
            'label': 'Scheduler backlog',
            'available': true,
            'weight': 0.12,
            'value': profile.isCritical ? 127.0 : 6.0,
            'display': profile.isCritical ? '127 jobs' : '6 jobs',
            'pressure': profile.isCritical ? 0.84 : 0.0,
            'scorePoints': profile.isCritical ? 10.1 : 0.0,
            'minimum': 10.0,
            'maximum': 300.0,
          },
          <String, dynamic>{
            'id': 'tick-ms-p95',
            'label': 'World tick time',
            'available': true,
            'weight': 0.30,
            'value': profile.isCritical ? 42.8 : 18.6,
            'display': profile.isCritical ? '42.8 ms' : '18.6 ms',
            'pressure': profile.isCritical ? 0.72 : 0.0,
            'scorePoints': profile.isCritical ? 21.6 : 0.0,
            'minimum': 50.0,
            'maximum': 150.0,
          },
          <String, dynamic>{
            'id': 'gc-time-percent',
            'label': 'Memory pressure',
            'available': true,
            'weight': 0.10,
            'value': profile.isCritical ? 94.0 : 61.0,
            'display': profile.isCritical ? '94%' : '61%',
            'pressure': profile.isCritical ? 0.61 : 0.0,
            'scorePoints': profile.isCritical ? 6.1 : 0.0,
            'minimum': 2.0,
            'maximum': 25.0,
          },
        ],
        'incidents': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'qa-event',
            'incidentId': 'qa-incident',
            'kind': profile.isCritical ? 'SERVER_PRESSURE' : 'RECOVERY',
            'phase': profile.isCritical ? 'STARTED' : 'RESOLVED',
            'severity': profile.isCritical ? 'CRITICAL' : 'INFO',
            'occurredAtMs': 1770000000000,
            'startedAtMs': 1769999940000,
            'source': 'incident-mode',
            'title': profile.isCritical
                ? 'Incident mode engaged'
                : 'Incident mode released',
            'summary': profile.isCritical
                ? 'Runtime rate guardrails are active.'
                : 'Server pressure returned below exit thresholds.',
            'cause': profile.isCritical
                ? 'Scheduler backlog and tick latency crossed their thresholds.'
                : 'The incident score and tick time recovered.',
            'location': null,
            'evidence': <Map<String, dynamic>>[],
            'actions': <Map<String, dynamic>>[
              <String, dynamic>{
                'id': 'incident-rate-guards',
                'label': 'Runtime rate guardrails',
                'status': profile.isCritical ? 'ACTIVE' : 'COMPLETED',
                'detail': profile.isCritical
                    ? 'Excess events are limited.'
                    : 'The incident window ended.',
                'occurredAtMs': 1770000000000,
              },
            ],
            'context': <String, String>{'profile': profile.name},
          },
        ],
      };

  static Map<String, dynamic> environment(VisualQaProfile profile) =>
      <String, dynamic>{
        'cpu': <String, Object?>{
          'model': 'QA 16-Core',
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
          'brand': profile == VisualQaProfile.alpha ? 'Folia' : 'Purpur',
          'minecraft': '1.21.11',
          'react': '1.7.4-qa',
          'onlineMode': true,
        },
        'disks': <Map<String, Object?>>[
          <String, Object?>{
            'name': 'nvme0n1',
            'model': 'QA NVMe Storage',
            'sizeBytes': 1000000000000,
            'readBytes': profile.isCritical ? 780000000000 : 420000000000,
            'writeBytes': profile.isCritical ? 510000000000 : 190000000000,
            'reads': 1800000,
            'writes': 950000,
            'queueLength': profile.isCritical ? 14 : 1,
            'transferTimeMillis': 930000,
            'timestampMillis': 100000,
          },
        ],
        'mounts': <Map<String, Object?>>[
          <String, Object?>{
            'name': 'world-storage',
            'mount': '/srv/minecraft',
            'description': 'QA world storage',
            'type': 'ext4',
            'totalBytes': 1000000000000,
            'freeBytes': profile.isCritical ? 83000000000 : 470000000000,
            'usableBytes': profile.isCritical ? 78000000000 : 450000000000,
          },
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
            'receivedBytes': profile.isCritical ? 980000000000 : 360000000000,
            'sentBytes': profile.isCritical ? 720000000000 : 210000000000,
            'receivedPackets': 64000000,
            'sentPackets': 41000000,
            'receiveErrors': 0,
            'sendErrors': 0,
            'receiveDrops': profile.isCritical ? 140 : 0,
            'collisions': 0,
            'timestampMillis': 100000,
          },
        ],
      };

  static Map<String, dynamic> config(VisualQaProfile profile) =>
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

  static List<Map<String, dynamic>>
  heatmapSummaries() => <Map<String, dynamic>>[
    <String, dynamic>{'id': 'qa-chunk-pressure', 'label': 'QA Chunk Pressure'},
    <String, dynamic>{'id': 'qa-entity-density', 'label': 'QA Entity Density'},
  ];

  static Map<String, dynamic>? heatmap(
    VisualQaProfile profile,
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) {
    final String label = switch (id) {
      'qa-chunk-pressure' => 'QA Chunk Pressure',
      'qa-entity-density' => 'QA Entity Density',
      _ => '',
    };
    if (label.isEmpty) return null;

    final String resolvedWorld =
        world ??
        (id == 'qa-chunk-pressure'
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
        final double baseline = id == 'qa-chunk-pressure' ? 88.0 : 72.0;
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

  static List<String> logs(VisualQaProfile profile) => <String>[
    '[INFO] QA fixture ready',
    '[INFO] ${profile.label} connected to React Web',
    '[INFO] Metrics sampler published ${_metrics.length} values',
    if (profile.isCritical)
      '[WARN] Tick budget pressure detected in world_nether'
    else
      '[INFO] Tick budget remains within the configured target',
    '[INFO] Console stream is available for local visual QA',
  ];

  static Map<String, dynamic> _control({
    required String id,
    required String name,
    required String category,
    required bool enabled,
    required String description,
    required List<Map<String, dynamic>> knobs,
  }) => <String, dynamic>{
    'id': id,
    'name': name,
    'category': category,
    'enabled': enabled,
    'description': description,
    'knobs': knobs,
  };

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

  static List<double> _history(double value, double max, int seed) {
    const List<double> offsets = <double>[
      -0.08,
      -0.04,
      0.01,
      0.05,
      0.02,
      -0.02,
      0.04,
      0.08,
      0.03,
      -0.01,
      -0.05,
      0.02,
      0.06,
      0.09,
      0.04,
      0.0,
      -0.03,
      0.03,
      0.07,
      0.02,
      -0.02,
      0.01,
      0.05,
      0.0,
    ];
    final int rotation = seed.abs() % offsets.length;
    return List<double>.generate(offsets.length, (int index) {
      final double offset = offsets[(index + rotation) % offsets.length];
      return (value * (1.0 + offset)).clamp(0.0, max).toDouble();
    });
  }

  static String _displayValue(double value) {
    if (value.abs() >= 100.0 || value == value.roundToDouble()) {
      return value.round().toString();
    }
    return value.toStringAsFixed(1);
  }

  static String _displayName(String id) {
    const Map<String, String> specialWords = <String, String>{
      'ai': 'AI',
      'gc': 'GC',
      'io': 'IO',
      'iris': 'Iris',
      'jvm': 'JVM',
      'mb': 'MB',
      'ms': 'MS',
      'p50': 'P50',
      'p95': 'P95',
      'p99': 'P99',
      'pdc': 'PDC',
      'react': 'React',
      'tps': 'TPS',
      'wormholes': 'Wormholes',
      'adapt': 'Adapt',
    };
    return id
        .split('-')
        .map((String word) {
          final String? special = specialWords[word];
          if (special != null) return special;
          return '${word[0].toUpperCase()}${word.substring(1)}';
        })
        .join(' ');
  }
}
