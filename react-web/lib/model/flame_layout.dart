/// The icicle layout of a call tree: the focused frame spans the full width
/// on the top row and each child takes its share of its parent's width on
/// the row below, busiest first. Pure, so the geometry is unit tested.
library;

import 'call_tree.dart';
import 'profiler.dart';

/// The top of the view: every thread under one frame.
ProfilerNode flameRoot(ProfilerTree tree, String label) {
  final int sum = tree.roots.fold(
    0,
    (int total, ProfilerNode root) => total + root.total,
  );
  return ProfilerNode(
    method: label,
    owner: '',
    ownerName: '',
    total: sum,
    self: 0,
    children: tree.roots,
  );
}

/// One drawn frame: [x] and [width] are fractions of the view's width.
class FlameBox {
  const FlameBox({
    required this.node,
    required this.path,
    required this.depth,
    required this.x,
    required this.width,
  });

  final ProfilerNode node;

  /// Child indexes from the view's root to this frame (in [flameChildren]
  /// order), the address a click zooms to.
  final List<int> path;
  final int depth;
  final double x;
  final double width;
}

/// [node]'s children busiest first, ties by name, so a frame keeps its
/// place between refreshes.
List<ProfilerNode> flameChildren(ProfilerNode node) =>
    hottestFirst(node.children);

/// The frame at [path] under [root], or null when the tree no longer has it.
ProfilerNode? flameNodeAt(ProfilerNode root, List<int> path) {
  ProfilerNode node = root;
  for (final int index in path) {
    final List<ProfilerNode> children = flameChildren(node);
    if (index < 0 || index >= children.length) return null;
    node = children[index];
  }
  return node;
}

/// The frames from [root] down to the one at [path] (the breadcrumb),
/// stopping where the tree no longer goes.
List<ProfilerNode> flameTrail(ProfilerNode root, List<int> path) {
  final List<ProfilerNode> trail = <ProfilerNode>[root];
  ProfilerNode node = root;
  for (final int index in path) {
    final List<ProfilerNode> children = flameChildren(node);
    if (index < 0 || index >= children.length) break;
    node = children[index];
    trail.add(node);
  }
  return trail;
}

/// The path of the frame matching [methods] by name from the root, so a
/// zoom survives a refresh that reorders siblings; as deep as still found.
List<int> flamePathOf(ProfilerNode root, List<String> methods) {
  final List<int> path = <int>[];
  ProfilerNode node = root;
  for (final String method in methods) {
    final List<ProfilerNode> children = flameChildren(node);
    final int index = children.indexWhere(
      (ProfilerNode child) => child.method == method,
    );
    if (index < 0) break;
    path.add(index);
    node = children[index];
  }
  return path;
}

/// The boxes of the view focused on the frame at [focus]: that frame on
/// row 0 at full width, then [maxRows] - 1 rows below it. A frame narrower
/// than [minWidth] is left out with everything under it.
List<FlameBox> layoutFlame(
  ProfilerNode root, {
  List<int> focus = const <int>[],
  double minWidth = 0.002,
  int maxRows = 48,
}) {
  final ProfilerNode? top = flameNodeAt(root, focus);
  if (top == null || maxRows <= 0) return const <FlameBox>[];
  final List<FlameBox> boxes = <FlameBox>[];
  void place(
    ProfilerNode node,
    List<int> path,
    int depth,
    double x,
    double width,
  ) {
    boxes.add(
      FlameBox(node: node, path: path, depth: depth, x: x, width: width),
    );
    if (depth + 1 >= maxRows || node.total <= 0) return;
    double cursor = x;
    for (final (int index, ProfilerNode child) in flameChildren(node).indexed) {
      final double room = x + width - cursor;
      final double wanted = width * child.total / node.total;
      final double share = wanted < room ? wanted : (room > 0 ? room : 0);
      if (share >= minWidth) {
        place(child, <int>[...path, index], depth + 1, cursor, share);
      }
      cursor += share;
    }
  }

  place(top, List<int>.unmodifiable(focus), 0, 0, 1);
  return boxes;
}

/// How many rows [boxes] fill.
int flameRows(List<FlameBox> boxes) => boxes.fold(
  0,
  (int rows, FlameBox box) => box.depth + 1 > rows ? box.depth + 1 : rows,
);

/// The width of one character of the frame font (0.6875rem monospace) and
/// a frame's side padding, in CSS pixels.
const double flameCharPx = 6.7;
const double flamePaddingPx = 8;

/// What fits on a frame [px] wide: the class and method, else the method,
/// else nothing.
String flameLabel(String method, double px) {
  final FrameName name = splitFrame(method);
  for (final String text in <String>[name.short, name.call]) {
    if (text.length * flameCharPx + flamePaddingPx <= px) return text;
  }
  return '';
}
