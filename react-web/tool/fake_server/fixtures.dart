import 'dart:convert';
import 'dart:math' as math;

import 'package:crypto/crypto.dart';

import 'metric_fixtures.dart';
import 'plugin_source.dart';

/// How a fake server is doing; it sets its sampler values, incidents and
/// log lines.
enum ServerHealth { healthy, degraded, critical }

/// One fake React server of the fleet: its name, base path, key and health.
final class FakeServerProfile {
  static const List<(String, ServerHealth, String)> _presets =
      <(String, ServerHealth, String)>[
        ('Survival', ServerHealth.healthy, 'Paper'),
        ('Skyblock', ServerHealth.critical, 'Purpur'),
        ('Lobby', ServerHealth.degraded, 'Folia'),
        ('Creative', ServerHealth.healthy, 'Paper'),
        ('Minigames', ServerHealth.degraded, 'Purpur'),
        ('Factions', ServerHealth.critical, 'Paper'),
        ('Events', ServerHealth.healthy, 'Folia'),
        ('Hub', ServerHealth.healthy, 'Paper'),
      ];

  static int get maxServers => _presets.length;

  final int index;
  final int playerCount;

  const FakeServerProfile(this.index, {this.playerCount = 40});

  static List<FakeServerProfile> fleet(int count, {int players = 40}) {
    if (count < 1 || count > maxServers) {
      throw RangeError.range(count, 1, maxServers, 'servers');
    }
    return List<FakeServerProfile>.generate(
      count,
      (int index) => FakeServerProfile(index, playerCount: players),
    );
  }

  /// The first path segment this server answers under (`/s1/api/v1/...`).
  String get slug => 's${index + 1}';
  String get label => _presets[index].$1;
  ServerHealth get health => _presets[index].$2;
  String get brand => _presets[index].$3;
  bool get folia => brand == 'Folia';
  bool get isCritical => health == ServerHealth.critical;
  bool get isStressed => health != ServerHealth.healthy;
  int get keySeed => 17 + index * 31;

  /// [healthy] on a healthy server, [critical] on a critical one, and a
  /// value between them on a degraded one.
  double level(double healthy, double critical) => switch (health) {
    ServerHealth.healthy => healthy,
    ServerHealth.critical => critical,
    ServerHealth.degraded => healthy + (critical - healthy) * 0.55,
  };

  /// The server key as links carry it: an Ed25519 SubjectPublicKeyInfo
  /// (the 12-byte X.509 header and 32 key bytes) in base64url.
  String get publicKey => base64Url
      .encode(<int>[
        0x30,
        0x2a,
        0x30,
        0x05,
        0x06,
        0x03,
        0x2b,
        0x65,
        0x70,
        0x03,
        0x21,
        0x00, //
        for (int index = 0; index < 32; index++) (keySeed + index * 7) % 256,
      ])
      .replaceAll('=', '');

  String get fingerprint => sha256
      .convert(base64Url.decode(base64Url.normalize(publicKey)))
      .toString();

  /// The plugin's `PairingLink`: `<appUrl>/pair#RCT3.<base64url JSON>`.
  String pairingLink({
    required String appUrl,
    required Uri directUrl,
    required String tokenId,
    required String secret,
    required DateTime expiresAt,
    required String role,
    required Duration lifetime,
    String? label,
  }) {
    final Map<String, Object?> payload = <String, Object?>{
      'v': 1,
      'directUrl': directUrl.toString(),
      'fingerprint': fingerprint,
      'serverKey': publicKey,
      'tokenId': tokenId,
      'secret': secret,
      'expiresAt': '${expiresAt.toUtc().toIso8601String().split('.').first}Z',
      'serverName': this.label,
      'scopes': roleScopes[role],
      'role': role,
      'lifetimeSeconds': lifetime.inSeconds,
      'label': ?label,
    };
    final String encoded = base64Url
        .encode(utf8.encode(jsonEncode(payload)))
        .replaceAll('=', '');
    return '$appUrl/pair#RCT3.$encoded';
  }
}

/// The plugin's `WebRole` scopes.
const Map<String, List<String>> roleScopes = <String, List<String>>{
  'viewer': <String>['read'],
  'operator': <String>['read', 'op:execute'],
  'admin': <String>[
    'read',
    'op:execute',
    'admin',
    'console:read',
    'console:execute',
  ],
};

/// The plugin's `HistoryTier` intervals.
const List<int> historyTiersMs = <int>[1000, 10000, 60000, 900000, 3600000];

abstract final class FakeFixtures {
  static const List<String> _worldNames = <String>[
    'world',
    'world_nether',
    'world_the_end',
    'resources',
    'spawn',
  ];

  static Map<String, dynamic> identity(FakeServerProfile profile) =>
      <String, dynamic>{
        'serverName': profile.label,
        'folia': profile.folia,
        'serverId': profile.fingerprint,
      };

  static Map<String, dynamic> capabilities(FakeServerProfile profile) =>
      <String, dynamic>{
        'protocolVersion': 3,
        'serverFingerprint': profile.fingerprint,
        'serverName': profile.label,
        'relayAvailable': false,
      };

  static const List<String> _firstNames = <String>[
    'Alex', 'Morgan', 'Riley', 'Jordan', 'Casey', 'Quinn', 'Avery', 'Rowan', //
    'Skyler', 'Harper', 'Emery', 'Finley', 'Sage', 'Reese', 'Dakota', 'Ellis',
  ];
  static const List<String> _suffixes = <String>[
    'Builds', 'Miner', 'TheBold', '_mc', 'Craft', 'Smith', 'Fox', 'Pvp', //
    '', 'Redstone', 'Farmer', 'Wolf', '2009', 'xX', 'Scout', 'Arrow',
  ];

  static List<Map<String, dynamic>> players(
    FakeServerProfile profile,
  ) => List<Map<String, dynamic>>.generate(profile.playerCount, (int index) {
    final int n = profile.index * 1000 + index;
    final String hex = (n * 2654435761 % 0xffffffff)
        .toRadixString(16)
        .padLeft(8, '0');
    return <String, dynamic>{
      'id':
          '$hex-${(4000 + index).toRadixString(16).padLeft(4, '0')}-4${(n % 4096).toRadixString(16).padLeft(3, '0')}-8${(index * 37 % 4096).toRadixString(16).padLeft(3, '0')}-${(n * 97 + 4096).toRadixString(16).padLeft(12, '0')}',
      'name':
          '${_firstNames[n % _firstNames.length]}${_suffixes[(n ~/ _firstNames.length + index) % _suffixes.length]}',
    };
  });

  /// One live snapshot. Values drift with [now] so charts move between
  /// frames, as a real server's do.
  static Map<String, dynamic> metrics(
    FakeServerProfile profile, {
    required int sequence,
    required DateTime now,
  }) => <String, dynamic>{
    'data': <String, dynamic>{
      'sequence': sequence,
      'capturedAtMs': now.millisecondsSinceEpoch,
      'samplers': metricData(profile, now),
    },
  };

  static List<Map<String, dynamic>> metricData(
    FakeServerProfile profile,
    DateTime now,
  ) {
    return metricFixtures.map((MetricFixture fixture) {
      final double level = fixture.id == 'players'
          ? profile.playerCount.toDouble()
          : profile.folia && kFoliaZero.contains(fixture.id)
          ? 0
          : sample(profile, fixture, now.millisecondsSinceEpoch);
      final double value = wireValue(fixture, level);
      return <String, dynamic>{
        'id': fixture.id,
        'name': displayName(fixture.id),
        'suffix': wireSuffix(fixture, value),
        'value': value,
        'display': _display(fixture, value),
        'available': true,
      };
    }).toList();
  }

  /// [level] as the plugin sends it: sizes in bytes.
  static double wireValue(MetricFixture fixture, double level) =>
      _isSize(fixture) ? (level * 1048576).roundToDouble() : level;

  /// The suffix the plugin sends with [value]: a size's unit is the one its
  /// figure is written in ("GB", "MB/s").
  static String wireSuffix(MetricFixture fixture, double value) {
    if (!_isSize(fixture)) return fixture.suffix;
    final String unit = _sizeSplit(value).$2;
    return fixture.suffix == kBytesPerSecond ? '$unit/s' : unit;
  }

  static bool _isSize(MetricFixture fixture) =>
      fixture.suffix == kBytes || fixture.suffix == kBytesPerSecond;

  /// The plugin's `Form.memSizeSplit(bytes, 1)`: one decimal without a
  /// trailing ".0", whole megabytes.
  static (String, String) _sizeSplit(double bytes) {
    const List<String> units = <String>['B', 'KB', 'MB', 'GB', 'TB'];
    double v = bytes;
    int power = 0;
    while (v >= 1024 && power < units.length - 1) {
      v /= 1024;
      power++;
    }
    final String text = units[power] == 'MB' || power == 0
        ? v.round().toString()
        : v.toStringAsFixed(1).replaceFirst(RegExp(r'\.0$'), '');
    return (text, units[power]);
  }

  /// The figure the plugin shows for [value]: sizes in their unit, CPU
  /// loads with their "%", counts whole, the rest with one decimal.
  static String _display(MetricFixture fixture, double value) {
    if (_isSize(fixture)) return _sizeSplit(value).$1;
    if (const <String>{'CPU', 'pCPU', 'xCPU'}.contains(fixture.suffix)) {
      return '${value.round()}%';
    }
    return _displayValue(value, count: _isCount(fixture));
  }

  /// The waves a sampler drifts with: (amplitude, period in seconds): a
  /// day, a couple of hours, a few minutes and a short ripple.
  static const List<(double, double)> _waves = <(double, double)>[
    (0.08, 86400),
    (0.05, 7200),
    (0.03, 300),
    (0.015, 20),
  ];

  /// The value of [fixture] at [timestampMs], the same for the same instant.
  /// Averaged over [windowMs], waves shorter than four windows drop out, as
  /// they do in a real server's rolled-up history.
  static double sample(
    FakeServerProfile profile,
    MetricFixture fixture,
    int timestampMs, {
    int windowMs = 1000,
  }) {
    final double base = profile.level(fixture.alpha, fixture.beta);
    final int seed = fixture.id.codeUnits.fold(
      profile.index * 131,
      (int sum, int unit) => (sum * 31 + unit) & 0x7fffffff,
    );
    final double t = timestampMs / 1000.0;
    double wave = 0;
    for (int index = 0; index < _waves.length; index++) {
      final (double amplitude, double period) = _waves[index];
      if (period * 1000 < windowMs * 4) continue;
      wave +=
          amplitude * math.sin(2 * math.pi * t / period + (seed >> index) % 13);
    }
    return (base * (1.0 + wave)).clamp(0.0, fixture.max).toDouble();
  }

  /// The share of [fixture]'s level that moves inside one [windowMs] bucket.
  static double _spread(int windowMs) => _waves
      .where(((double, double) wave) => wave.$2 * 1000 < windowMs * 4)
      .fold(0.0, (double sum, (double, double) wave) => sum + wave.$1);

  static List<Map<String, dynamic>> metricHistoryCatalog(DateTime now) {
    final int nowMs = now.millisecondsSinceEpoch;
    return metricFixtures.map((MetricFixture fixture) {
      return <String, dynamic>{
        'id': fixture.id,
        'name': displayName(fixture.id),
        'suffix': wireSuffix(fixture, wireValue(fixture, fixture.alpha)),
        'firstTimestampMs': nowMs - const Duration(days: 30).inMilliseconds,
        'lastTimestampMs': nowMs,
        'active': true,
      };
    }).toList();
  }

  /// The plugin's `HistoryQueryEngine.selectResolution` for a viewport.
  static int resolutionFor(int fromMs, int toMs, int maxPoints) {
    final int range = math.max(1, toMs - fromMs);
    final int required = (range + maxPoints - 1) ~/ math.max(1, maxPoints);
    for (final int tier in historyTiersMs) {
      if (required <= tier) return tier;
    }
    return (required + 3599999) ~/ 3600000 * 3600000;
  }

  /// One page of history for [ids] from [pageFromMs], at most [pagePoints]
  /// points per series; `null` when no id is known.
  static Map<String, dynamic>? metricHistoryPage(
    FakeServerProfile profile, {
    required List<String> ids,
    required int requestedFromMs,
    required int requestedToMs,
    required int pageFromMs,
    required int resolutionMs,
    required int pagePoints,
    required int throughSequence,
    required int throughMs,
    required String? Function(int pageToMs) nextCursor,
  }) {
    final List<MetricFixture> known = <MetricFixture>[
      for (final String id in ids)
        ...metricFixtures.where((MetricFixture fixture) => fixture.id == id),
    ];
    if (known.isEmpty) return null;
    final int pageToMs = math.min(
      requestedToMs,
      pageFromMs + resolutionMs * pagePoints,
    );
    final int firstBucket = (pageFromMs ~/ resolutionMs) * resolutionMs;
    return <String, dynamic>{
      'requestedFromMs': requestedFromMs,
      'requestedToMs': requestedToMs,
      'pageFromMs': pageFromMs,
      'pageToMs': pageToMs,
      'actualResolutionMs': resolutionMs,
      'throughSequence': throughSequence,
      'throughMs': throughMs,
      'nextCursor': pageToMs >= requestedToMs ? null : nextCursor(pageToMs),
      'series': <Map<String, dynamic>>[
        for (final MetricFixture fixture in known)
          <String, dynamic>{
            'id': fixture.id,
            'name': displayName(fixture.id),
            'suffix': wireSuffix(
              fixture,
              wireValue(fixture, profile.level(fixture.alpha, fixture.beta)),
            ),
            'points': <List<num>>[
              for (
                int bucket = firstBucket;
                bucket < pageToMs;
                bucket += resolutionMs
              )
                if (bucket >= pageFromMs)
                  _point(profile, fixture, bucket, resolutionMs),
            ],
          },
      ],
    };
  }

  /// The samplers the plugin keeps chart ranges for, in its order.
  static const List<String> chartSamplerIds = <String>[
    'tick-time',
    'tick-ms-p50',
    'tick-ms-p95',
    'tick-ms-p99',
    'memory-used',
    'memory-free',
    'memory-used-after-gc',
    'disk-read-rate',
    'disk-write-rate',
    'network-receive-rate',
    'network-send-rate',
    'react-async-tick-time',
    'react-sync-tick-time',
  ];

  /// The plugin's chart ranges: id to bucket length; 360 buckets each.
  static const Map<String, int> chartRangeStepsMs = <String, int>{
    '1h': 10000,
    '6h': 60000,
    '24h': 240000,
  };

  /// `GET /metrics/range`: 360 bucket averages of each kept sampler that
  /// has a fixture, ending at the last closed bucket; null for an unknown
  /// range.
  static Map<String, dynamic>? metricRange(
    FakeServerProfile profile, {
    required String range,
    required List<String>? ids,
    required int nowMs,
  }) {
    final int? step = chartRangeStepsMs[range];
    if (step == null) return null;
    final int toMs = nowMs ~/ step * step;
    final int fromMs = toMs - 360 * step;
    final List<String> wanted = <String>[
      for (final String id in ids ?? chartSamplerIds)
        if (chartSamplerIds.contains(id)) id,
    ];
    return <String, dynamic>{
      'range': range,
      'stepMs': step,
      'fromMs': fromMs,
      'toMs': toMs,
      'series': <Map<String, dynamic>>[
        for (final String id in wanted)
          for (final MetricFixture fixture in metricFixtures.where(
            (MetricFixture fixture) => fixture.id == id,
          ))
            <String, dynamic>{
              'id': id,
              'values': <double>[
                for (int bucket = fromMs; bucket < toMs; bucket += step)
                  wireValue(
                    fixture,
                    _round(
                      sample(
                        profile,
                        fixture,
                        bucket + step ~/ 2,
                        windowMs: step,
                      ),
                    ),
                  ),
              ],
            },
      ],
    };
  }

  static List<num> _point(
    FakeServerProfile profile,
    MetricFixture fixture,
    int bucket,
    int resolutionMs,
  ) {
    final double average = sample(
      profile,
      fixture,
      bucket + resolutionMs ~/ 2,
      windowMs: resolutionMs,
    );
    final double spread =
        _spread(resolutionMs) * profile.level(fixture.alpha, fixture.beta);
    final double last = sample(profile, fixture, bucket + resolutionMs - 1);
    double wire(double level) => wireValue(fixture, _round(level));
    return <num>[
      bucket,
      wire(average),
      wire(math.max(0.0, math.min(average - spread, last))),
      wire(math.min(fixture.max, math.max(average + spread, last))),
      wire(last),
      math.max(1, resolutionMs ~/ 1000),
    ];
  }

  static double _round(double value) => (value * 1000).roundToDouble() / 1000;

  static List<Map<String, dynamic>> worlds(FakeServerProfile profile) =>
      <Map<String, dynamic>>[
        for (int index = 0; index < _worldNames.length; index++)
          <String, dynamic>{
            'key': 'minecraft:${_worldNames[index]}',
            'name': _worldNames[index],
            'pressureMode': switch ((profile.health, index)) {
              (ServerHealth.critical, 0) => 'PANIC',
              (ServerHealth.critical, 1) ||
              (ServerHealth.degraded, 0) => 'PRESSURE',
              _ => 'NORMAL',
            },
            'budgetMs': <double>[32.0, 18.0, 12.0, 14.0, 8.0][index],
            'panicMs': <double>[48.0, 30.0, 22.0, 24.0, 16.0][index],
            'releaseMs': <double>[28.0, 15.0, 10.0, 12.0, 6.0][index],
          },
      ];

  static Map<String, dynamic> _param(
    String key,
    String label,
    String type, {
    bool required = true,
    Object? defaultValue,
    List<String> options = const <String>[],
  }) => <String, dynamic>{
    'key': key,
    'label': label,
    'type': type,
    'required': required,
    'default': defaultValue,
    'options': options,
  };

  /// The plugin's action catalog (`content/action`).
  static List<Map<String, dynamic>> actions() => <Map<String, dynamic>>[
    <String, dynamic>{
      'id': 'action-incident-playbook',
      'name': 'Incident Playbook',
      'description': 'Runs relevant incident mitigations one at a time, observes pressure between actions, and completes after their terminal outcomes.',
      'destructive': false,
      'params': <Map<String, dynamic>>[],
    },
    <String, dynamic>{
      'id': 'action-prewarm-critical-chunks',
      'name': 'Prewarm Critical Chunks',
      'description': 'Loads the chunks around spawn and busy players ahead of a join wave.',
      'destructive': false,
      'params': <Map<String, dynamic>>[
        _param(
          'world',
          'World',
          'enum',
          defaultValue: 'world',
          options: _worldNames,
        ),
        _param('radius', 'Radius', 'int', defaultValue: 8),
      ],
    },
    <String, dynamic>{
      'id': 'action-quarantine-hot-chunks',
      'name': 'Quarantine Hot Chunks',
      'description':
          'Freezes ticking in the costliest chunks until they cool down.',
      'destructive': true,
      'params': <Map<String, dynamic>>[
        _param(
          'world',
          'World',
          'enum',
          defaultValue: 'world',
          options: _worldNames,
        ),
        _param('limit', 'Chunks', 'int', defaultValue: 16),
      ],
    },
    <String, dynamic>{
      'id': 'action-trim-entities-by-age-priority',
      'name': 'Trim Entities By Age',
      'description': 'Removes the oldest low-value entities first until the target count is met.',
      'destructive': true,
      'params': <Map<String, dynamic>>[
        _param(
          'world',
          'World',
          'enum',
          defaultValue: 'world',
          options: _worldNames,
        ),
        _param('target', 'Target count', 'int', defaultValue: 4000),
      ],
    },
    <String, dynamic>{
      'id': 'action-hopper-network-normalize',
      'name': 'Normalize Hopper Networks',
      'description':
          'Rebuilds hopper indexes and resets stalled transfer chains.',
      'destructive': false,
      'params': <Map<String, dynamic>>[],
    },
    <String, dynamic>{
      'id': 'capture-profile',
      'name': 'Capture Profile',
      'description': 'Captures an on-demand local JDK Flight Recorder profile with execution, allocation, and GC events. Retains three captures; never uploads recordings.',
      'destructive': false,
      'params': <Map<String, dynamic>>[
        _param('seconds', 'Duration', 'int', defaultValue: 30),
      ],
    },
    <String, dynamic>{
      'id': 'collect-garbage',
      'name': 'Collect Garbage',
      'description': 'Queues a controlled garbage collection request.',
      'destructive': false,
      'params': <Map<String, dynamic>>[],
    },
    <String, dynamic>{
      'id': 'purge-chunks',
      'name': 'Purge Chunks',
      'description':
          'Unloads idle chunks with no players or force-load tickets.',
      'destructive': true,
      'params': <Map<String, dynamic>>[
        _param(
          'world',
          'World',
          'enum',
          defaultValue: 'world',
          options: _worldNames,
        ),
      ],
    },
    <String, dynamic>{
      'id': 'purge-entities',
      'name': 'Purge Entities',
      'description': 'Removes eligible entities in a bounded radius.',
      'destructive': true,
      'params': <Map<String, dynamic>>[
        _param('radius', 'Radius', 'int', defaultValue: 64),
        _param(
          'type',
          'Entity type',
          'string',
          required: false,
          defaultValue: '',
        ),
      ],
    },
  ];

  static Map<String, dynamic> _contributor(
    String id,
    String label,
    double weight,
    double value,
    String display,
    double pressure,
    double minimum,
    double maximum,
  ) => <String, dynamic>{
    'id': id,
    'label': label,
    'available': true,
    'weight': weight,
    'value': value,
    'display': display,
    'pressure': pressure,
    'scorePoints': pressure * weight * 100,
    'minimum': minimum,
    'maximum': maximum,
  };

  static const List<(String, String, String, String, String)> _incidentKinds =
      <(String, String, String, String, String)>[
        (
          'SERVER_PRESSURE',
          'incident-mode',
          'Incident mode engaged',
          'Runtime rate guardrails are active.',
          'Scheduler backlog and tick latency crossed their thresholds.',
        ),
        (
          'TICK_SPIKE',
          'tick-spike-origin-replay-map',
          'Tick spike',
          'One tick took over 250 ms.',
          'Entity AI in a crowded farm ran long.',
        ),
        (
          'REDSTONE_CIRCUIT',
          'circuit-manager',
          'Redstone clock throttled',
          'A fast clock was slowed to the configured rate.',
          'A circuit toggled more than 40 times a second.',
        ),
        (
          'TRINITY_PRESSURE',
          'feature-trinity-incident-mode',
          'Memory, CPU and tick pressure',
          'All three pressure signals were high at once.',
          'Garbage collection pauses grew while tick time rose.',
        ),
        (
          'ENTITY_SURGE',
          'spawn-burst-limiter',
          'Spawn burst limited',
          'Mob spawns were capped for 30 seconds.',
          'A spawner farm released over 200 mobs in a minute.',
        ),
        (
          'CHUNK_LOAD_STORM',
          'chunk-load-gen-cost-map',
          'Chunk load storm',
          'New chunk generation was paced.',
          'Several players flew into ungenerated terrain.',
        ),
      ];

  /// The incident score, its contributors and the recent incident events:
  /// 3 on a healthy server, 7 on a degraded one, 12 on a critical one.
  static Map<String, dynamic> incidents(
    FakeServerProfile profile,
    DateTime now,
  ) {
    final int nowMs = now.millisecondsSinceEpoch;
    final int count = switch (profile.health) {
      ServerHealth.healthy => 3,
      ServerHealth.degraded => 7,
      ServerHealth.critical => 12,
    };
    final double score = profile.level(22.0, 72.0);
    return <String, dynamic>{
      'score': score,
      'scoreAvailable': true,
      'sampledAtMs': nowMs,
      'state': switch (profile.health) {
        ServerHealth.healthy => 'NORMAL',
        ServerHealth.degraded => 'ELEVATED',
        ServerHealth.critical => 'CRITICAL',
      },
      'contributors': <Map<String, dynamic>>[
        _contributor(
          'scheduler-backlog',
          'Scheduler backlog',
          0.12,
          profile.level(6, 127),
          '${profile.level(6, 127).round()} jobs',
          profile.level(0, 0.84),
          10,
          300,
        ),
        _contributor(
          'tick-ms-p95',
          'World tick time',
          0.30,
          profile.level(18.6, 42.8),
          '${profile.level(18.6, 42.8).toStringAsFixed(1)} ms',
          profile.level(0, 0.72),
          50,
          150,
        ),
        _contributor(
          'gc-time-percent',
          'Memory pressure',
          0.10,
          profile.level(3.1, 18.7),
          '${profile.level(3.1, 18.7).toStringAsFixed(1)}%',
          profile.level(0, 0.61),
          2,
          25,
        ),
        _contributor(
          'entity-ai-active-count',
          'Active entity AI',
          0.18,
          profile.level(734, 3184),
          '${profile.level(734, 3184).round()}',
          profile.level(0.05, 0.66),
          1500,
          6000,
        ),
        _contributor(
          'player-ping-p95',
          'Player ping',
          0.08,
          profile.level(47, 238),
          '${profile.level(47, 238).round()} ms',
          profile.level(0, 0.48),
          150,
          400,
        ),
        _contributor(
          'chunks-generated',
          'Chunk generation',
          0.12,
          profile.level(12, 48),
          '${profile.level(12, 48).round()}/min',
          profile.level(0, 0.40),
          30,
          120,
        ),
      ],
      'incidents': <Map<String, dynamic>>[
        for (int index = 0; index < count; index++)
          _incident(profile, index, nowMs - index * 7 * 60 * 1000 - 45000),
      ],
    };
  }

  static Map<String, dynamic> _incident(
    FakeServerProfile profile,
    int index,
    int occurredAtMs,
  ) {
    final (
      String kind,
      String source,
      String title,
      String summary,
      String cause,
    ) = _incidentKinds[(index + profile.index) % _incidentKinds.length];
    final bool open =
        profile.isStressed && index < (profile.isCritical ? 3 : 1);
    final String severity = open
        ? (profile.isCritical && index == 0 ? 'CRITICAL' : 'WARNING')
        : (index.isEven ? 'INFO' : 'WARNING');
    final String world = _worldNames[index % 3];
    return <String, dynamic>{
      'id': '${profile.slug}-event-$index',
      'incidentId': '${profile.slug}-incident-${index ~/ 2}',
      'kind': kind,
      'phase': open ? 'STARTED' : 'RESOLVED',
      'severity': severity,
      'occurredAtMs': occurredAtMs,
      'startedAtMs': occurredAtMs - 60000 - index * 15000,
      'source': source,
      'title': open ? title : '$title resolved',
      'summary': summary,
      'cause': cause,
      'location': index % 3 == 2
          ? null
          : <String, dynamic>{
              'worldId': 'minecraft:$world',
              'world': world,
              'x': 2368 + index * 97,
              'y': 64,
              'z': -1168 - index * 53,
            },
      'evidence': <Map<String, dynamic>>[
        <String, dynamic>{
          'metricId': 'tick-ms-p95',
          'label': 'World tick time',
          'available': true,
          'value': profile.level(44.8, 82.6),
          'display': '${profile.level(44.8, 82.6).toStringAsFixed(1)} ms',
          'pressure': profile.level(0.2, 0.72),
          'weight': 0.30,
          'scorePoints': profile.level(6.0, 21.6),
          'minimum': 50.0,
          'maximum': 150.0,
        },
      ],
      'actions': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': '$source-guard',
          'label': 'Runtime rate guardrails',
          'status': open ? 'ACTIVE' : 'COMPLETED',
          'detail': open
              ? 'Excess events are limited.'
              : 'The incident window ended.',
          'occurredAtMs': occurredAtMs,
        },
      ],
      'context': <String, String>{'server': profile.label, 'world': world},
    };
  }

  static const List<String> _logTemplates = <String>[
    '[INFO] {player} joined the game',
    '[INFO] {player} left the game',
    '[INFO] Saving chunks for level \'ServerLevel[{world}]\'/minecraft:{world}',
    '[INFO] ThreadedAnvilChunkStorage ({world}): All chunks are saved',
    '[INFO] [React] Metrics sampler published {samplers} values in 1.8 ms',
    '[INFO] [React] Feature mob-stacking merged 14 entities near {x}, {z}',
    '[INFO] {player} issued server command: /home base',
    '[INFO] [Adapt] Saved 40 player profiles',
    '[INFO] [Iris] Generated 64 chunks in {world} (12.4 chunks/s)',
    '[WARN] Can\'t keep up! Is the server overloaded? Running 2051ms or 41 ticks behind',
    '[INFO] [React] Lazy gravity skipped 312 falling-block ticks',
    '[WARN] [React] Redstone clock at {x}, {z} throttled to 4 Hz',
    '[INFO] <{player}> anyone want to trade diamonds?',
    '[INFO] [Wormholes] Portal frame "spawn-gate" rendered for 6 players',
  ];

  /// Log line [n] of [profile], the same text for the same number.
  static String logLine(FakeServerProfile profile, int n, DateTime at) {
    final List<String> pool = <String>[
      for (final String line in _logTemplates)
        if (profile.isStressed || !line.startsWith('[WARN]')) line,
    ];
    final String template =
        pool[(n * 5 + n ~/ 7 + profile.index) % pool.length];
    final String time =
        '${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}:${at.second.toString().padLeft(2, '0')}';
    final Map<String, dynamic> player = players(
      profile,
    )[(n * 13) % profile.playerCount];
    final String body = template
        .replaceAll('{player}', player['name'] as String)
        .replaceAll('{world}', _worldNames[n % 3])
        .replaceAll('{samplers}', '${metricFixtures.length}')
        .replaceAll('{x}', '${(n * 37) % 4000 - 2000}')
        .replaceAll('{z}', '${(n * 53) % 4000 - 2000}');
    final int close = body.indexOf('] ');
    return '[$time ${body.substring(1, close)}]: ${body.substring(close + 2)}';
  }

  /// The [count] lines a server has logged up to [now], oldest first, one
  /// every two seconds.
  static List<String> logBacklog(
    FakeServerProfile profile,
    DateTime now,
    int count,
  ) => <String>[
    for (int n = 0; n < count; n++)
      logLine(profile, n, now.subtract(Duration(seconds: (count - n) * 2))),
  ];

  /// A copy of the plugin's controls for [profile]: map and overlay
  /// features off (they cost a map item each), incident mode on only where
  /// the server is under pressure, the rest on.
  static List<Map<String, dynamic>> controls(
    FakeServerProfile profile,
    List<Map<String, dynamic>> source,
  ) => <Map<String, dynamic>>[
    for (final Map<String, dynamic> control in source)
      <String, dynamic>{
        ...control,
        'enabled': switch (control['id'] as String) {
          final String id when id.contains('incident-mode') =>
            profile.isStressed,
          final String id
              when PluginControlSource.categoryFor(id) == 'Diagnostics' =>
            profile.index.isEven,
          _ => true,
        },
        'knobs': <Map<String, dynamic>>[
          for (final dynamic knob in control['knobs'] as List<dynamic>)
            Map<String, dynamic>.of(knob as Map<String, dynamic>),
        ],
      },
  ];

  /// Whether a sampler counts things (players, jobs, chunks): the plugin
  /// shows those as whole numbers.
  /// Counts the plugin shows whole: their suffix is a tag with no unit.
  static bool _isCount(MetricFixture fixture) =>
      const <String>{
        '',
        'PLR',
        'ENT',
        'CHK',
        'AI',
        'LISTENERS',
        'JOBS',
        'BACKLOG',
        'chunks',
        'events',
        'plugins',
      }.contains(fixture.suffix) &&
      fixture.id != 'incident-score' &&
      !fixture.id.contains('per-tick');

  static String _displayValue(double value, {bool count = false}) {
    if (count || value.abs() >= 100.0 || value == value.roundToDouble()) {
      return value.round().toString();
    }
    return value.toStringAsFixed(1);
  }

  static String displayName(String id) {
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
