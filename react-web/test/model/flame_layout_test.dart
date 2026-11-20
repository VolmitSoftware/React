import 'package:test/test.dart';

import 'package:react_web/model/flame_layout.dart';
import 'package:react_web/model/profiler.dart';

ProfilerNode _n(
  String method,
  int total, [
  List<ProfilerNode> children = const <ProfilerNode>[],
]) => ProfilerNode(
  method: method,
  owner: 'o',
  ownerName: 'O',
  total: total,
  self: total - children.fold(0, (int s, ProfilerNode c) => s + c.total),
  children: children,
);

void main() {
  final ProfilerTree tree = ProfilerTree(100, <ProfilerNode>[
    _n('Worker', 20, <ProfilerNode>[_n('w.a', 20)]),
    _n('Server thread', 80, <ProfilerNode>[
      _n('s.small', 20),
      _n('s.tick', 60, <ProfilerNode>[_n('s.deep', 30), _n('s.tiny', 0)]),
    ]),
  ]);
  final ProfilerNode root = flameRoot(tree, 'All');

  test('the root holds every thread', () {
    expect(root.method, 'All');
    expect(root.total, 100);
    expect(root.children, hasLength(2));
  });

  test('children are busiest first and share their parent width', () {
    final List<FlameBox> boxes = layoutFlame(root);
    FlameBox box(String method) =>
        boxes.firstWhere((FlameBox b) => b.node.method == method);
    expect(box('All').x, 0);
    expect(box('All').width, 1);
    expect(box('Server thread').depth, 1);
    expect(box('Server thread').x, 0);
    expect(box('Server thread').width, closeTo(0.8, 1e-9));
    expect(box('Worker').x, closeTo(0.8, 1e-9));
    expect(box('s.tick').x, 0);
    expect(box('s.tick').width, closeTo(0.6, 1e-9));
    expect(box('s.small').x, closeTo(0.6, 1e-9));
    expect(box('s.deep').depth, 3);
    expect(box('s.deep').width, closeTo(0.3, 1e-9));
    expect(boxes.any((FlameBox b) => b.node.method == 's.tiny'), isFalse);
    expect(flameRows(boxes), 4);
  });

  test('a box never leaves its parent, even with bad counts', () {
    final ProfilerNode bad = _n('top', 10, <ProfilerNode>[
      _n('a', 8),
      _n('b', 8),
    ]);
    final List<FlameBox> boxes = layoutFlame(bad);
    for (final FlameBox b in boxes) {
      expect(b.x + b.width, lessThanOrEqualTo(1 + 1e-9));
    }
  });

  test('zooming puts the focused frame at full width', () {
    final List<int> path = flamePathOf(root, <String>[
      'Server thread',
      's.tick',
    ]);
    expect(path, <int>[0, 0]);
    final List<FlameBox> boxes = layoutFlame(root, focus: path);
    expect(boxes.first.node.method, 's.tick');
    expect(boxes.first.width, 1);
    expect(boxes.first.path, <int>[0, 0]);
    final FlameBox deep = boxes.firstWhere(
      (FlameBox b) => b.node.method == 's.deep',
    );
    expect(deep.width, closeTo(0.5, 1e-9));
    expect(deep.depth, 1);
    expect(deep.path, <int>[0, 0, 0]);
    expect(flameTrail(root, path).map((ProfilerNode n) => n.method), <String>[
      'All',
      'Server thread',
      's.tick',
    ]);
    expect(flameNodeAt(root, path)!.method, 's.tick');
  });

  test('a zoom that no longer exists stops where the tree ends', () {
    expect(
      flamePathOf(root, <String>['Server thread', 'gone', 's.deep']),
      <int>[0],
    );
    expect(flameNodeAt(root, <int>[9]), isNull);
    expect(layoutFlame(root, focus: <int>[9]), isEmpty);
    expect(flameTrail(root, <int>[0, 7]).length, 2);
  });

  test('rows and narrow frames are capped', () {
    expect(flameRows(layoutFlame(root, maxRows: 2)), 2);
    final List<FlameBox> wide = layoutFlame(root, minWidth: 0.25);
    expect(wide.map((FlameBox b) => b.node.method), <String>[
      'All',
      'Server thread',
      's.tick',
      's.deep',
    ]);
  });
}
