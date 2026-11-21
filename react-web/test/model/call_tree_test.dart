import 'package:test/test.dart';

import 'package:react_web/model/call_tree.dart';
import 'package:react_web/model/flame_layout.dart';
import 'package:react_web/model/profiler.dart';

ProfilerNode _node(
  String method,
  int total, {
  int self = 0,
  String owner = 'minecraft',
  List<ProfilerNode> children = const <ProfilerNode>[],
  List<String> collapsed = const <String>[],
}) => ProfilerNode(
  method: method,
  owner: owner,
  ownerName: owner,
  total: total,
  self: self,
  children: children,
  collapsed: collapsed,
);

const String _gen =
    'net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.fillFromNoise';

ProfilerNode _worker(String name, int total) => _node(
  name,
  total,
  children: <ProfilerNode>[
    _node(
      'java.util.concurrent.ForkJoinWorkerThread.run',
      total,
      owner: 'java',
      children: <ProfilerNode>[
        _node(
          'java.util.concurrent.ForkJoinPool.runWorker',
          total,
          owner: 'java',
          children: <ProfilerNode>[
            _node(
              'net.minecraft.util.thread.ProcessorMailbox.run',
              total,
              children: <ProfilerNode>[_node(_gen, total, self: total)],
            ),
          ],
        ),
      ],
    ),
  ],
);

void main() {
  group('threadGroupName', () {
    test('strips the number numbered threads share', () {
      expect(threadGroupName('Worker-Main-7'), 'Worker-Main');
      expect(threadGroupName('Worker-Main-12'), 'Worker-Main');
      expect(
        threadGroupName('Netty Epoll Server IO #0'),
        'Netty Epoll Server IO',
      );
      expect(
        threadGroupName('Craft Scheduler Thread - 4'),
        'Craft Scheduler Thread',
      );
      expect(threadGroupName('pool-3-thread-1'), 'pool-3-thread');
    });

    test('leaves names without a number, or already grouped, alone', () {
      expect(threadGroupName('Server thread'), isNull);
      expect(threadGroupName('Worker-Main (12)'), isNull);
      expect(threadGroupName('42'), isNull);
    });
  });

  group('groupThreads', () {
    test('merges numbered threads and sums their frames', () {
      final List<ProfilerNode> grouped = groupThreads(<ProfilerNode>[
        _node('Server thread', 50, self: 50),
        _worker('Worker-Main-1', 10),
        _worker('Worker-Main-2', 20),
        _worker('Worker-Main-3', 5),
        _node('Netty Epoll Server IO #0', 4, self: 4),
      ]);
      expect(grouped.map((ProfilerNode root) => root.method), <String>[
        'Server thread',
        'Worker-Main (3)',
        'Netty Epoll Server IO #0',
      ]);
      final ProfilerNode workers = grouped[1];
      expect(workers.total, 35);
      expect(workers.children.single.total, 35);
      ProfilerNode leaf = workers;
      while (leaf.children.isNotEmpty) {
        leaf = leaf.children.single;
      }
      expect(leaf.method, _gen);
      expect(leaf.self, 35);
    });

    test('is a no-op on a tree the server already grouped', () {
      final List<ProfilerNode> once = groupThreads(<ProfilerNode>[
        _worker('Worker-Main-1', 10),
        _worker('Worker-Main-2', 20),
      ]);
      final List<ProfilerNode> twice = groupThreads(once);
      expect(twice.single.method, 'Worker-Main (2)');
      expect(twice.single.total, 30);
    });
  });

  group('collapsePlumbing', () {
    test('folds single-child plumbing runs into one row', () {
      final ProfilerNode root = collapsePlumbing(_worker('Worker-Main-1', 10));
      final ProfilerNode folded = root.children.single;
      expect(folded.method, '(3 frames)');
      expect(folded.collapsed, <String>[
        'java.util.concurrent.ForkJoinWorkerThread.run',
        'java.util.concurrent.ForkJoinPool.runWorker',
        'net.minecraft.util.thread.ProcessorMailbox.run',
      ]);
      expect(folded.total, 10);
      expect(isCollapsedFrame(folded), isTrue);
      expect(folded.children.single.method, _gen);
    });

    test('keeps a lone plumbing frame and a branching one', () {
      final ProfilerNode branching = collapsePlumbing(
        _node(
          'Server thread',
          10,
          children: <ProfilerNode>[
            _node(
              'java.util.ArrayList.forEach',
              10,
              owner: 'java',
              children: <ProfilerNode>[
                _node('a.B.c', 6, self: 6, owner: 'Iris'),
                _node('a.B.d', 4, self: 4, owner: 'Iris'),
              ],
            ),
          ],
        ),
      );
      expect(branching.children.single.method, 'java.util.ArrayList.forEach');
    });

    test('never folds a plugin or mod frame, lambda or not', () {
      final ProfilerNode root = collapsePlumbing(
        _node(
          'Server thread',
          8,
          children: <ProfilerNode>[
            _node(
              r'com.example.Mod.lambda$tick$0',
              8,
              owner: 'example',
              children: <ProfilerNode>[
                _node(
                  r'com.example.Mod.lambda$tick$1',
                  8,
                  owner: 'example',
                  children: <ProfilerNode>[_node('com.example.Mod.work', 8)],
                ),
              ],
            ),
          ],
        ),
      );
      expect(root.children.single.method, r'com.example.Mod.lambda$tick$0');
    });

    test('leaves rows the server folded as they are', () {
      final ProfilerNode server = _node(
        'Server thread',
        5,
        children: <ProfilerNode>[
          _node(
            '(4 frames)',
            5,
            owner: 'java',
            collapsed: <String>[
              'java.a.B.c',
              'java.a.B.d',
              'java.a.B.e',
              'java.a.B.f',
            ],
            children: <ProfilerNode>[_node('x.Y.z', 5, self: 5)],
          ),
        ],
      );
      final ProfilerNode again = collapsePlumbing(server);
      expect(again.children.single.method, '(4 frames)');
      expect(again.children.single.collapsed.length, 4);
    });

    test('reads the server shape', () {
      final ProfilerNode parsed = ProfilerNode.fromJson(<String, dynamic>{
        'method': '(2 frames)',
        'owner': 'java',
        'total': 3,
        'self': 0,
        'collapsed': <dynamic>[
          'java.a.B.c',
          <String, dynamic>{'method': 'java.a.B.d'},
        ],
        'children': <dynamic>[],
      });
      expect(parsed.collapsed, <String>['java.a.B.c', 'java.a.B.d']);
      expect(
        ProfilerNode.fromJson(parsed.toJson()).collapsed,
        parsed.collapsed,
      );
    });
  });

  group('isPlumbingFrame', () {
    test('knows JDK and executor frames', () {
      expect(
        isPlumbingFrame('java.util.concurrent.ForkJoinTask.doExec'),
        isTrue,
      );
      expect(isPlumbingFrame('jdk.internal.misc.Unsafe.park'), isTrue);
      expect(
        isPlumbingFrame(
          r'java.util.concurrent.CompletableFuture$AsyncSupply.run',
        ),
        isTrue,
      );
      expect(
        isPlumbingFrame('net.minecraft.util.thread.ProcessorMailbox.pollTask'),
        isTrue,
      );
      expect(
        isPlumbingFrame(r'net.minecraft.server.level.ChunkMap.lambda$x$27'),
        isTrue,
      );
      expect(
        isPlumbingFrame('net.minecraft.server.level.ServerLevel.tick'),
        isFalse,
      );
    });
  });

  group('splitFrame', () {
    test('shortens the class and keeps the package apart', () {
      final FrameName name = splitFrame(
        'net.minecraft.server.level.ServerLevel.tick',
      );
      expect(name.short, 'ServerLevel.tick');
      expect(name.package, 'net.minecraft.server.level');
      expect(
        splitFrame(r'a.Outer$Inner.run(Outer.java:12)').short,
        r'Outer$Inner.run(Outer.java:12)',
      );
    });

    test('keeps names that are not frames whole', () {
      expect(splitFrame('Worker-Main (12)').short, 'Worker-Main (12)');
      expect(splitFrame('(other)').short, '(other)');
      expect(splitFrame('(3 frames)').short, '(3 frames)');
      expect(splitFrame('Server thread').short, 'Server thread');
    });
  });

  group('flattenCallTree', () {
    final List<ProfilerNode> roots = <ProfilerNode>[
      _node(
        'Server thread',
        60,
        children: <ProfilerNode>[
          _node('a.Tick.small', 10, self: 10),
          _node(
            'a.Tick.big',
            50,
            children: <ProfilerNode>[
              _node('a.Gen.noise', 40, self: 40, owner: 'Iris'),
              _node('a.Gen.light', 10, self: 10),
            ],
          ),
        ],
      ),
      _node(
        'Worker-Main (2)',
        40,
        children: <ProfilerNode>[_node('a.Work.run', 40, self: 40)],
      ),
    ];

    test('lists closed roots busiest first', () {
      final List<CallTreeRow> rows = flattenCallTree(
        roots,
        samples: 100,
        expanded: const <String>{},
      );
      expect(rows.map((CallTreeRow row) => row.node.method), <String>[
        'Server thread',
        'Worker-Main (2)',
      ]);
      expect(rows.first.expandable, isTrue);
      expect(rows.first.expanded, isFalse);
      expect(rows.first.totalShare, 0.6);
    });

    test('opens the hot path and shows children busiest first', () {
      final Set<String> hot = hotPathKeys(roots, samples: 100);
      expect(hot, <String>{'Server thread', 'Server thread\na.Tick.big'});
      final List<CallTreeRow> rows = flattenCallTree(
        roots,
        samples: 100,
        expanded: hot,
      );
      expect(rows.map((CallTreeRow row) => row.node.method), <String>[
        'Server thread',
        'a.Tick.big',
        'a.Gen.noise',
        'a.Gen.light',
        'a.Tick.small',
        'Worker-Main (2)',
      ]);
      expect(rows[2].depth, 2);
      expect(rows[2].parentKey, 'Server thread\na.Tick.big');
      expect(rows[2].selfShare, 0.4);
    });

    test('a search keeps matches and the frames above them, open', () {
      final List<CallTreeRow> rows = flattenCallTree(
        roots,
        samples: 100,
        expanded: const <String>{},
        query: 'NOISE',
      );
      expect(rows.map((CallTreeRow row) => row.node.method), <String>[
        'Server thread',
        'a.Tick.big',
        'a.Gen.noise',
      ]);
      expect(rows.last.match, isTrue);
      expect(rows.first.match, isFalse);
      expect(
        flattenCallTree(
          roots,
          samples: 100,
          expanded: const <String>{},
          query: 'zzz',
        ),
        isEmpty,
      );
    });

    test('a match shows its whole subtree when opened', () {
      final List<CallTreeRow> rows = flattenCallTree(
        roots,
        samples: 100,
        expanded: const <String>{'Server thread\na.Tick.big'},
        query: 'tick.big',
      );
      expect(rows.map((CallTreeRow row) => row.node.method), <String>[
        'Server thread',
        'a.Tick.big',
        'a.Gen.noise',
        'a.Gen.light',
      ]);
    });

    test('a search finds frames inside a folded row', () {
      final List<CallTreeRow> rows = flattenCallTree(
        <ProfilerNode>[collapsePlumbing(_worker('Worker-Main-1', 10))],
        samples: 10,
        expanded: const <String>{},
        query: 'runWorker',
      );
      expect(rows.last.node.method, '(3 frames)');
      expect(rows.last.match, isTrue);
    });
  });

  group('flameLabel', () {
    test('shows what fits and nothing when nothing does', () {
      const String method = 'net.minecraft.server.level.ServerLevel.tick';
      expect(flameLabel(method, 400), 'ServerLevel.tick');
      expect(flameLabel(method, 40), 'tick');
      expect(flameLabel(method, 10), '');
    });
  });
}
