import 'package:test/test.dart';

import 'package:react_web/model/profiler.dart';

Map<String, dynamic> _node(
  String method,
  String owner,
  int total,
  int self, [
  List<Map<String, dynamic>> children = const <Map<String, dynamic>>[],
]) => <String, dynamic>{
  'method': method,
  'owner': owner,
  'ownerName': owner == 'minecraft' ? 'Minecraft' : owner,
  'total': total,
  'self': self,
  'children': children,
};

void main() {
  group('ProfilerStatus', () {
    test('reads a running profiler', () {
      final ProfilerStatus status = ProfilerStatus.fromJson(<String, dynamic>{
        'running': true,
        'intervalMs': 5,
        'threads': 'all',
        'startedAt': 1000,
        'stopsAt': 601000,
        'samples': 1234,
        'durationMs': 6170,
      });
      expect(status.running, isTrue);
      expect(status.intervalMs, 5);
      expect(status.threads, ProfilerThreads.all);
      expect(status.startedAt, DateTime.fromMillisecondsSinceEpoch(1000));
      expect(status.stopsAt, DateTime.fromMillisecondsSinceEpoch(601000));
      expect(status.samples, 1234);
      expect(
        ProfilerStatus.fromJson(status.toJson()).toJson(),
        status.toJson(),
      );
    });

    test('falls back on missing or odd fields', () {
      final ProfilerStatus status = ProfilerStatus.fromJson(<String, dynamic>{
        'running': 'yes',
        'intervalMs': 0,
        'threads': 'some',
        'startedAt': null,
      });
      expect(status.running, isFalse);
      expect(status.intervalMs, profilerDefaultIntervalMs);
      expect(status.threads, ProfilerThreads.server);
      expect(status.startedAt, isNull);
      expect(status.samples, 0);
    });
  });

  test('commands carry the contract body, clamped', () {
    expect(
      const ProfilerStart(intervalMs: 0, maxSeconds: 99999).toJson(),
      <String, Object?>{
        'action': 'start',
        'intervalMs': 1,
        'threads': 'server',
        'maxSeconds': 3600,
      },
    );
    expect(
      const ProfilerStart(threads: ProfilerThreads.all).toJson()['threads'],
      'all',
    );
    expect(const ProfilerStop().toJson(), <String, Object?>{'action': 'stop'});
    expect(const ProfilerReset().toJson(), <String, Object?>{
      'action': 'reset',
    });
  });

  test('owners are ranked by samples with their top methods', () {
    final ProfilerOwners owners = ProfilerOwners.fromJson(<String, dynamic>{
      'samples': 100,
      'owners': <Map<String, dynamic>>[
        <String, dynamic>{
          'id': 'minecraft',
          'name': 'Minecraft',
          'samples': 20,
          'percent': 20.0,
        },
        <String, dynamic>{
          'id': 'React',
          'name': '',
          'samples': 70,
          'percent': 70.0,
          'top': <Object?>[
            <String, dynamic>{'method': 'a.B.c', 'samples': 50},
            'junk',
          ],
        },
      ],
    });
    expect(owners.owners.map((ProfilerOwner o) => o.id), <String>[
      'React',
      'minecraft',
    ]);
    expect(owners.byId('React')!.name, 'React');
    expect(owners.byId('React')!.top.single.samples, 50);
    expect(owners.byId('nope'), isNull);
  });

  test('hot methods narrow to one owner', () {
    final ProfilerHot hot = ProfilerHot.fromJson(<String, dynamic>{
      'samples': 10,
      'methods': <Map<String, dynamic>>[
        <String, dynamic>{
          'method': 'a.X.run',
          'owner': 'Iris',
          'self': 4,
          'total': 6,
          'selfPercent': 40,
          'totalPercent': 60,
        },
        <String, dynamic>{
          'method': 'b.Y.run',
          'owner': 'React',
          'ownerName': 'React',
          'self': 2,
          'total': 2,
          'selfPercent': 20,
          'totalPercent': 20,
        },
      ],
    });
    expect(hot.methods.first.ownerName, 'Iris');
    expect(hot.methods.first.selfPercent, 40);
    expect(hot.ownedBy('React').methods.single.method, 'b.Y.run');
    expect(hot.ownedBy(null).methods, hasLength(2));
  });

  test('the tree reads nested frames and the folded rest', () {
    final ProfilerTree tree = ProfilerTree.fromJson(<String, dynamic>{
      'samples': 10,
      'roots': <Map<String, dynamic>>[
        _node('Server thread', 'minecraft', 10, 0, <Map<String, dynamic>>[
          _node('a.B.tick', 'Iris', 8, 3, <Map<String, dynamic>>[
            _node('(other)', '', 5, 5),
          ]),
        ]),
      ],
    });
    final ProfilerNode thread = tree.roots.single;
    expect(thread.ownerName, 'Minecraft');
    expect(thread.children.single.children.single.isOther, isTrue);
    expect(ProfilerTree.fromJson(tree.toJson()).toJson(), tree.toJson());
  });

  test('threads come busiest first, and an owner tree names its threads', () {
    final List<ProfilerThread> threads = profilerThreadsFromJson(
      <String, dynamic>{
        'threads': <Map<String, dynamic>>[
          <String, dynamic>{
            'name': 'Worker',
            'state': 'WAITING',
            'samples': 5,
            'percent': 5.0,
          },
          <String, dynamic>{
            'name': 'Server thread',
            'state': 'RUNNABLE',
            'samples': 95,
            'percent': 95.0,
          },
        ],
      },
    );
    expect(threads.first.name, 'Server thread');
    final List<ProfilerThread> owned = profilerThreadsOf(
      ProfilerTree.fromJson(<String, dynamic>{
        'samples': 40,
        'roots': <Map<String, dynamic>>[
          _node('Worker', 'minecraft', 10, 0),
          _node('Server thread', 'minecraft', 30, 0),
        ],
      }),
    );
    expect(owned.map((ProfilerThread t) => t.name), <String>[
      'Server thread',
      'Worker',
    ]);
    expect(owned.first.percent, 75);
  });

  test('a profile snapshot reads every view', () {
    final ProfileData data = ProfileData.fromJson(<String, dynamic>{
      'profiler': <String, dynamic>{'running': false, 'samples': 10},
      'owners': <String, dynamic>{'samples': 10, 'owners': <dynamic>[]},
      'hot': <String, dynamic>{'samples': 10, 'methods': <dynamic>[]},
      'tree': <String, dynamic>{
        'samples': 10,
        'roots': <Map<String, dynamic>>[
          _node('Server thread', 'minecraft', 10, 10),
        ],
      },
      'threads': <String, dynamic>{
        'threads': <Map<String, dynamic>>[
          <String, dynamic>{
            'name': 'Server thread',
            'samples': 10,
            'percent': 100,
          },
        ],
      },
    });
    expect(data.status.samples, 10);
    expect(data.tree.roots.single.total, 10);
    expect(data.threads.single.percent, 100);
    expect(ProfileData.fromJson(<String, dynamic>{}).status.running, isFalse);
    expect(
      ProfileData.fromJson(data.toJson()).toJson().toString(),
      data.toJson().toString(),
    );
  });

  test('method and percent text', () {
    expect(
      profilerShortMethod('net.minecraft.server.MinecraftServer.tickServer'),
      'MinecraftServer.tickServer',
    );
    expect(profilerShortMethod('Server thread'), 'Server thread');
    expect(profilerShortMethod('(other)'), '(other)');
    expect(profilerPercentText(12.345), '12.3%');
    expect(profilerPercentText(1.234), '1.23%');
    expect(profilerPercentText(0), '0.0%');
  });
}
