/// The sampling profiler's wire shapes: `GET /profiler` and its owners, hot
/// methods, call tree and threads, and a frozen profile snapshot of them.
library;

int _int(Object? value) => value is num ? value.toInt() : 0;

double _double(Object? value) => value is num ? value.toDouble() : 0;

String _string(Object? value) => value is String ? value : '';

List<Map<String, dynamic>> _objects(Object? value) => <Map<String, dynamic>>[
  if (value is List<dynamic>)
    for (final Object? entry in value)
      if (entry is Map<String, dynamic>) entry,
];

Map<String, dynamic> _object(Object? value) =>
    value is Map<String, dynamic> ? value : const <String, dynamic>{};

/// Which threads the sampler reads: the server thread or every thread.
enum ProfilerThreads {
  server('server'),
  all('all');

  const ProfilerThreads(this.wire);

  final String wire;

  static ProfilerThreads parse(Object? value) =>
      value == 'all' ? ProfilerThreads.all : ProfilerThreads.server;
}

/// The sampler's interval range and defaults, as the server clamps them.
const int profilerMinIntervalMs = 1;
const int profilerMaxIntervalMs = 1000;
const int profilerDefaultIntervalMs = 10;
const int profilerDefaultMaxSeconds = 600;
const int profilerMaxSeconds = 3600;

class ProfilerStatus {
  const ProfilerStatus({
    required this.running,
    required this.intervalMs,
    required this.threads,
    required this.samples,
    required this.durationMs,
    this.startedAt,
    this.stopsAt,
  });

  static const ProfilerStatus idle = ProfilerStatus(
    running: false,
    intervalMs: profilerDefaultIntervalMs,
    threads: ProfilerThreads.server,
    samples: 0,
    durationMs: 0,
  );

  final bool running;
  final int intervalMs;
  final ProfilerThreads threads;
  final int samples;
  final int durationMs;
  final DateTime? startedAt;
  final DateTime? stopsAt;

  factory ProfilerStatus.fromJson(Map<String, dynamic> json) {
    DateTime? at(Object? value) => value is num
        ? DateTime.fromMillisecondsSinceEpoch(value.toInt())
        : null;
    final int interval = _int(json['intervalMs']);
    return ProfilerStatus(
      running: json['running'] == true,
      intervalMs: interval > 0 ? interval : profilerDefaultIntervalMs,
      threads: ProfilerThreads.parse(json['threads']),
      samples: _int(json['samples']),
      durationMs: _int(json['durationMs']),
      startedAt: at(json['startedAt']),
      stopsAt: at(json['stopsAt']),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'running': running,
    'intervalMs': intervalMs,
    'threads': threads.wire,
    'startedAt': startedAt?.millisecondsSinceEpoch,
    'stopsAt': stopsAt?.millisecondsSinceEpoch,
    'samples': samples,
    'durationMs': durationMs,
  };
}

/// `POST /profiler`: start (with its settings), stop or reset.
sealed class ProfilerCommand {
  const ProfilerCommand();

  Map<String, Object?> toJson();
}

class ProfilerStart extends ProfilerCommand {
  const ProfilerStart({
    this.intervalMs = profilerDefaultIntervalMs,
    this.threads = ProfilerThreads.server,
    this.maxSeconds = profilerDefaultMaxSeconds,
  });

  final int intervalMs;
  final ProfilerThreads threads;
  final int maxSeconds;

  @override
  Map<String, Object?> toJson() => <String, Object?>{
    'action': 'start',
    'intervalMs': intervalMs.clamp(
      profilerMinIntervalMs,
      profilerMaxIntervalMs,
    ),
    'threads': threads.wire,
    'maxSeconds': maxSeconds.clamp(1, profilerMaxSeconds),
  };
}

class ProfilerStop extends ProfilerCommand {
  const ProfilerStop();

  @override
  Map<String, Object?> toJson() => const <String, Object?>{'action': 'stop'};
}

class ProfilerReset extends ProfilerCommand {
  const ProfilerReset();

  @override
  Map<String, Object?> toJson() => const <String, Object?>{'action': 'reset'};
}

class ProfilerMethodCount {
  const ProfilerMethodCount(this.method, this.samples);

  final String method;
  final int samples;

  factory ProfilerMethodCount.fromJson(Map<String, dynamic> json) =>
      ProfilerMethodCount(_string(json['method']), _int(json['samples']));

  Map<String, Object?> toJson() => <String, Object?>{
    'method': method,
    'samples': samples,
  };
}

/// One owner's share of the samples, with its three hottest methods.
class ProfilerOwner {
  const ProfilerOwner({
    required this.id,
    required this.name,
    required this.samples,
    required this.percent,
    this.top = const <ProfilerMethodCount>[],
  });

  final String id;
  final String name;
  final int samples;
  final double percent;
  final List<ProfilerMethodCount> top;

  factory ProfilerOwner.fromJson(Map<String, dynamic> json) {
    final String id = _string(json['id']);
    final String name = _string(json['name']);
    return ProfilerOwner(
      id: id,
      name: name.isEmpty ? id : name,
      samples: _int(json['samples']),
      percent: _double(json['percent']),
      top: <ProfilerMethodCount>[
        for (final Map<String, dynamic> entry in _objects(json['top']))
          ProfilerMethodCount.fromJson(entry),
      ],
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'name': name,
    'samples': samples,
    'percent': percent,
    'top': <Map<String, Object?>>[
      for (final ProfilerMethodCount method in top) method.toJson(),
    ],
  };
}

class ProfilerOwners {
  const ProfilerOwners(this.samples, this.owners);

  static const ProfilerOwners empty = ProfilerOwners(0, <ProfilerOwner>[]);

  final int samples;
  final List<ProfilerOwner> owners;

  factory ProfilerOwners.fromJson(Map<String, dynamic> json) {
    final List<ProfilerOwner> owners = <ProfilerOwner>[
      for (final Map<String, dynamic> entry in _objects(json['owners']))
        ProfilerOwner.fromJson(entry),
    ]..sort((ProfilerOwner a, ProfilerOwner b) => b.samples - a.samples);
    return ProfilerOwners(_int(json['samples']), owners);
  }

  ProfilerOwner? byId(String id) {
    for (final ProfilerOwner owner in owners) {
      if (owner.id == id) return owner;
    }
    return null;
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'samples': samples,
    'owners': <Map<String, Object?>>[
      for (final ProfilerOwner owner in owners) owner.toJson(),
    ],
  };
}

class ProfilerHotMethod {
  const ProfilerHotMethod({
    required this.method,
    required this.owner,
    required this.ownerName,
    required this.self,
    required this.total,
    required this.selfPercent,
    required this.totalPercent,
  });

  final String method;
  final String owner;
  final String ownerName;
  final int self;
  final int total;
  final double selfPercent;
  final double totalPercent;

  factory ProfilerHotMethod.fromJson(Map<String, dynamic> json) {
    final String owner = _string(json['owner']);
    final String ownerName = _string(json['ownerName']);
    return ProfilerHotMethod(
      method: _string(json['method']),
      owner: owner,
      ownerName: ownerName.isEmpty ? owner : ownerName,
      self: _int(json['self']),
      total: _int(json['total']),
      selfPercent: _double(json['selfPercent']),
      totalPercent: _double(json['totalPercent']),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'method': method,
    'owner': owner,
    'ownerName': ownerName,
    'self': self,
    'total': total,
    'selfPercent': selfPercent,
    'totalPercent': totalPercent,
  };
}

class ProfilerHot {
  const ProfilerHot(this.samples, this.methods);

  static const ProfilerHot empty = ProfilerHot(0, <ProfilerHotMethod>[]);

  final int samples;
  final List<ProfilerHotMethod> methods;

  factory ProfilerHot.fromJson(Map<String, dynamic> json) =>
      ProfilerHot(_int(json['samples']), <ProfilerHotMethod>[
        for (final Map<String, dynamic> entry in _objects(json['methods']))
          ProfilerHotMethod.fromJson(entry),
      ]);

  /// The methods [owner] owns; all of them when [owner] is null.
  ProfilerHot ownedBy(String? owner) => owner == null
      ? this
      : ProfilerHot(samples, <ProfilerHotMethod>[
          for (final ProfilerHotMethod method in methods)
            if (method.owner == owner) method,
        ]);

  Map<String, Object?> toJson() => <String, Object?>{
    'samples': samples,
    'methods': <Map<String, Object?>>[
      for (final ProfilerHotMethod method in methods) method.toJson(),
    ],
  };
}

/// One frame of the call tree; a root is a thread.
class ProfilerNode {
  const ProfilerNode({
    required this.method,
    required this.owner,
    required this.ownerName,
    required this.total,
    required this.self,
    this.children = const <ProfilerNode>[],
    this.collapsed = const <String>[],
  });

  /// The folded children below `minPercent`.
  static const String otherMethod = '(other)';

  final String method;
  final String owner;
  final String ownerName;
  final int total;
  final int self;
  final List<ProfilerNode> children;

  /// The plumbing frames this one row stands for, outermost first
  /// (`"method":"(N frames)"`); empty for a real frame.
  final List<String> collapsed;

  bool get isOther => method == otherMethod;

  bool get isCollapsed => collapsed.isNotEmpty;

  factory ProfilerNode.fromJson(Map<String, dynamic> json, [int depth = 0]) {
    final String owner = _string(json['owner']);
    final String ownerName = _string(json['ownerName']);
    return ProfilerNode(
      method: _string(json['method']),
      owner: owner,
      ownerName: ownerName.isEmpty ? owner : ownerName,
      total: _int(json['total']),
      self: _int(json['self']),
      children: depth >= profilerMaxTreeDepth
          ? const <ProfilerNode>[]
          : <ProfilerNode>[
              for (final Map<String, dynamic> child in _objects(
                json['children'],
              ))
                ProfilerNode.fromJson(child, depth + 1),
            ],
      collapsed: <String>[
        if (json['collapsed'] case final List<dynamic> frames)
          for (final Object? frame in frames)
            if (frame is String && frame.isNotEmpty)
              frame
            else if (frame is Map<String, dynamic> &&
                _string(frame['method']).isNotEmpty)
              _string(frame['method']),
      ],
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'method': method,
    'owner': owner,
    'ownerName': ownerName,
    'total': total,
    'self': self,
    'children': <Map<String, Object?>>[
      for (final ProfilerNode child in children) child.toJson(),
    ],
    if (collapsed.isNotEmpty) 'collapsed': collapsed,
  };
}

/// The deepest frame the server sends (`maxDepth` caps it at 256).
const int profilerMaxTreeDepth = 256;

class ProfilerTree {
  const ProfilerTree(this.samples, this.roots);

  static const ProfilerTree empty = ProfilerTree(0, <ProfilerNode>[]);

  final int samples;
  final List<ProfilerNode> roots;

  factory ProfilerTree.fromJson(Map<String, dynamic> json) =>
      ProfilerTree(_int(json['samples']), <ProfilerNode>[
        for (final Map<String, dynamic> entry in _objects(json['roots']))
          ProfilerNode.fromJson(entry),
      ]);

  Map<String, Object?> toJson() => <String, Object?>{
    'samples': samples,
    'roots': <Map<String, Object?>>[
      for (final ProfilerNode root in roots) root.toJson(),
    ],
  };
}

class ProfilerThread {
  const ProfilerThread({
    required this.name,
    required this.state,
    required this.samples,
    required this.percent,
  });

  final String name;
  final String state;
  final int samples;
  final double percent;

  factory ProfilerThread.fromJson(Map<String, dynamic> json) => ProfilerThread(
    name: _string(json['name']),
    state: _string(json['state']),
    samples: _int(json['samples']),
    percent: _double(json['percent']),
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'name': name,
    'state': state,
    'samples': samples,
    'percent': percent,
  };
}

List<ProfilerThread> profilerThreadsFromJson(Map<String, dynamic> json) =>
    <ProfilerThread>[
      for (final Map<String, dynamic> entry in _objects(json['threads']))
        ProfilerThread.fromJson(entry),
    ]..sort((ProfilerThread a, ProfilerThread b) => b.samples - a.samples);

/// The threads [tree] runs on: its roots (one per thread), busiest first.
/// An owner-pruned tree keeps only the threads that owner was sampled on.
List<ProfilerThread> profilerThreadsOf(ProfilerTree tree) {
  final int samples = tree.samples > 0
      ? tree.samples
      : tree.roots.fold(0, (int sum, ProfilerNode root) => sum + root.total);
  return <ProfilerThread>[
    for (final ProfilerNode root in tree.roots)
      ProfilerThread(
        name: root.method,
        state: '',
        samples: root.total,
        percent: samples == 0 ? 0 : root.total * 100 / samples,
      ),
  ]..sort((ProfilerThread a, ProfilerThread b) => b.samples - a.samples);
}

/// Everything the profiler shows at one moment: live, or frozen in a
/// profile snapshot (`data` of `GET /snapshots/{id}`, keyed `profiler`,
/// `owners`, `hot`, `tree` and `threads`, each the live route's shape).
class ProfileData {
  const ProfileData({
    required this.status,
    required this.owners,
    required this.hot,
    required this.tree,
    required this.threads,
  });

  final ProfilerStatus status;
  final ProfilerOwners owners;
  final ProfilerHot hot;
  final ProfilerTree tree;
  final List<ProfilerThread> threads;

  factory ProfileData.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> status = _object(
      json['profiler'] ?? json['status'],
    );
    return ProfileData(
      status: status.isEmpty
          ? ProfilerStatus.idle
          : ProfilerStatus.fromJson(status),
      owners: ProfilerOwners.fromJson(_object(json['owners'])),
      hot: ProfilerHot.fromJson(_object(json['hot'])),
      tree: ProfilerTree.fromJson(_object(json['tree'])),
      threads: profilerThreadsFromJson(_object(json['threads'])),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'profiler': status.toJson(),
    'owners': owners.toJson(),
    'hot': hot.toJson(),
    'tree': tree.toJson(),
    'threads': <String, Object?>{
      'threads': <Map<String, Object?>>[
        for (final ProfilerThread thread in threads) thread.toJson(),
      ],
    },
  };
}

/// "1.2%" from a percent (0-100).
String profilerPercentText(double percent) =>
    '${percent.toStringAsFixed(percent >= 10 || percent == 0 ? 1 : 2)}%';

/// The last two parts of a frame: "Class.method" without the package.
String profilerShortMethod(String method) {
  final int paren = method.indexOf('(');
  final String bare = paren < 0 ? method : method.substring(0, paren);
  final List<String> parts = bare.split('.');
  if (parts.length <= 2) return method;
  return parts.sublist(parts.length - 2).join('.');
}
