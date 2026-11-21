/// The call tree as rows: numbered threads merged into one ("Worker-Main
/// (12)"), single-child runs of JDK and executor plumbing folded into one
/// "(N frames)" row, and the tree flattened to the rows a list shows for
/// the open nodes and a search. The server does the first two when it can
/// (`group`, `collapse`); doing them here again changes nothing. Pure, so
/// it is unit tested.
library;

import 'profiler.dart';

final RegExp _numberedThread = RegExp(r'^(.*?\D)[\s#_-]*\d+$');
final RegExp _collapsedLabel = RegExp(r'^\(\d+ frames\)$');

/// The name [thread] shares with its numbered siblings ("Worker-Main" for
/// "Worker-Main-7"), or null when it carries no number.
String? threadGroupName(String thread) {
  final RegExpMatch? match = _numberedThread.firstMatch(thread);
  if (match == null) return null;
  final String base = match
      .group(1)!
      .replaceAll(RegExp(r'[\s#_-]+$'), '')
      .trimRight();
  return base.isEmpty ? null : base;
}

String _mergeKey(ProfilerNode node) => node.isCollapsed
    ? '${node.method}\u0000${node.collapsed.join('\u0000')}'
    : node.method;

/// [nodes] that are the same frame summed into one, children merged the
/// same way, in first-seen order.
List<ProfilerNode> mergeFrames(List<ProfilerNode> nodes) {
  final Map<String, List<ProfilerNode>> byKey = <String, List<ProfilerNode>>{};
  for (final ProfilerNode node in nodes) {
    byKey.putIfAbsent(_mergeKey(node), () => <ProfilerNode>[]).add(node);
  }
  return <ProfilerNode>[
    for (final List<ProfilerNode> same in byKey.values)
      same.length == 1 ? same.single : _merged(same.first.method, same),
  ];
}

ProfilerNode _merged(String method, List<ProfilerNode> same) {
  int total = 0;
  int self = 0;
  for (final ProfilerNode node in same) {
    total += node.total;
    self += node.self;
  }
  final ProfilerNode first = same.first;
  return ProfilerNode(
    method: method,
    owner: first.owner,
    ownerName: first.ownerName,
    total: total,
    self: self,
    collapsed: first.collapsed,
    children: mergeFrames(<ProfilerNode>[
      for (final ProfilerNode node in same) ...node.children,
    ]),
  );
}

/// [roots] (one per thread) with numbered threads of one name merged into
/// `name (count)`. A thread alone keeps its own name.
List<ProfilerNode> groupThreads(List<ProfilerNode> roots) {
  final Map<String, List<ProfilerNode>> byName = <String, List<ProfilerNode>>{};
  for (final ProfilerNode root in roots) {
    final String name = threadGroupName(root.method) ?? root.method;
    byName.putIfAbsent(name, () => <ProfilerNode>[]).add(root);
  }
  return <ProfilerNode>[
    for (final MapEntry<String, List<ProfilerNode>> entry in byName.entries)
      entry.value.length == 1
          ? entry.value.single
          : _merged('${entry.key} (${entry.value.length})', entry.value),
  ];
}

/// Owners that are not a plugin or mod: the server and the JDK.
bool isPlatformOwner(String owner) =>
    owner.isEmpty || owner == 'minecraft' || owner == 'java';

/// Whether [method] is a frame of the JDK or of an executor that only
/// hands work along.
bool isPlumbingFrame(String method) {
  if (method.startsWith('java.') ||
      method.startsWith('jdk.') ||
      method.startsWith('sun.') ||
      method.contains(r'lambda$') ||
      method.contains(r'$$Lambda')) {
    return true;
  }
  final FrameName name = splitFrame(method);
  final String type = name.type.split(r'$').first;
  return type.startsWith('ForkJoin') ||
      type.startsWith('CompletableFuture') ||
      type.startsWith('ProcessorMailbox');
}

/// Whether [node] already stands for several frames.
bool isCollapsedFrame(ProfilerNode node) =>
    node.isCollapsed || _collapsedLabel.hasMatch(node.method);

/// [node] with every run of two or more plumbing frames (of the server or
/// the JDK, never a plugin's or mod's) that each have one child folded into one "(N frames)" row over the frame the run leads to.
ProfilerNode collapsePlumbing(ProfilerNode node) => ProfilerNode(
  method: node.method,
  owner: node.owner,
  ownerName: node.ownerName,
  total: node.total,
  self: node.self,
  collapsed: node.collapsed,
  children: <ProfilerNode>[
    for (final ProfilerNode child in node.children) _collapseFrom(child),
  ],
);

bool _foldable(ProfilerNode node) =>
    node.children.length == 1 &&
    !node.isOther &&
    !isCollapsedFrame(node) &&
    isPlatformOwner(node.owner) &&
    isPlumbingFrame(node.method);

ProfilerNode _collapseFrom(ProfilerNode start) {
  final List<ProfilerNode> run = <ProfilerNode>[];
  ProfilerNode cursor = start;
  while (_foldable(cursor)) {
    run.add(cursor);
    cursor = cursor.children.single;
  }
  if (run.length < 2) return collapsePlumbing(start);
  int self = 0;
  for (final ProfilerNode frame in run) {
    self += frame.self;
  }
  return ProfilerNode(
    method: '(${run.length} frames)',
    owner: start.owner,
    ownerName: start.ownerName,
    total: start.total,
    self: self,
    collapsed: <String>[for (final ProfilerNode frame in run) frame.method],
    children: <ProfilerNode>[_collapseFrom(cursor)],
  );
}

/// [tree] as the list shows it: threads grouped, plumbing folded.
List<ProfilerNode> readableRoots(ProfilerTree tree) => <ProfilerNode>[
  for (final ProfilerNode root in groupThreads(tree.roots))
    collapsePlumbing(root),
];

/// The samples percentages are of: the tree's, else its threads' sum.
int treeSamples(ProfilerTree tree) => tree.samples > 0
    ? tree.samples
    : tree.roots.fold(0, (int sum, ProfilerNode root) => sum + root.total);

/// A frame split for display: "ServerLevel.tick" and its package.
class FrameName {
  const FrameName({required this.type, required this.call, this.package = ''});

  /// The class without its package ("ServerLevel", "Outer$Inner").
  final String type;

  /// The method, with any line suffix ("tick", "tick(ServerLevel.java:12)").
  final String call;
  final String package;

  /// "ServerLevel.tick"; the whole name when it is not a frame.
  String get short => type.isEmpty ? call : '$type.$call';
}

/// [method] split into class, method and package. Thread names, "(other)"
/// and "(N frames)" stay whole.
FrameName splitFrame(String method) {
  final int paren = method.indexOf('(');
  if (paren == 0) return FrameName(type: '', call: method);
  final String bare = paren < 0 ? method : method.substring(0, paren);
  final String suffix = paren < 0 ? '' : method.substring(paren);
  if (bare.contains(' ')) return FrameName(type: '', call: method);
  final int dot = bare.lastIndexOf('.');
  if (dot <= 0 || dot == bare.length - 1) {
    return FrameName(type: '', call: method);
  }
  final String owner = bare.substring(0, dot);
  final String call = '${bare.substring(dot + 1)}$suffix';
  final int packageDot = owner.lastIndexOf('.');
  return packageDot < 0
      ? FrameName(type: owner, call: call)
      : FrameName(
          type: owner.substring(packageDot + 1),
          call: call,
          package: owner.substring(0, packageDot),
        );
}

/// [node]'s children busiest first, ties by name, so rows keep their
/// place between refreshes.
List<ProfilerNode> hottestFirst(List<ProfilerNode> nodes) =>
    List<ProfilerNode>.of(nodes)..sort((ProfilerNode a, ProfilerNode b) {
      final int byTotal = b.total.compareTo(a.total);
      return byTotal != 0 ? byTotal : a.method.compareTo(b.method);
    });

/// The id of [node] under the parent id [parent]: frame names from the top,
/// so it holds across refreshes that reorder siblings.
String callTreeKey(String parent, ProfilerNode node) {
  final String own = node.isCollapsed
      ? '${node.method}|${node.collapsed.first}'
      : node.method;
  return parent.isEmpty ? own : '$parent\n$own';
}

/// The ids to open first: the busiest thread and, below it, the busiest
/// child at each level while it holds at least [minShare] of the samples,
/// [maxDepth] levels at most.
Set<String> hotPathKeys(
  List<ProfilerNode> roots, {
  required int samples,
  int maxDepth = 14,
  double minShare = 0.02,
}) {
  final Set<String> keys = <String>{};
  List<ProfilerNode> level = roots;
  String parent = '';
  for (int depth = 0; depth < maxDepth && level.isNotEmpty; depth++) {
    final ProfilerNode hottest = hottestFirst(level).first;
    if (depth > 0 && samples > 0 && hottest.total / samples < minShare) break;
    if (hottest.children.isEmpty) break;
    parent = callTreeKey(parent, hottest);
    keys.add(parent);
    level = hottest.children;
  }
  return keys;
}

/// One row of the list.
class CallTreeRow {
  const CallTreeRow({
    required this.node,
    required this.key,
    required this.depth,
    required this.expanded,
    required this.totalShare,
    required this.selfShare,
    required this.match,
    this.parentKey,
  });

  final ProfilerNode node;
  final String key;
  final String? parentKey;
  final int depth;

  /// Whether its children are shown; false for a leaf.
  final bool expanded;

  /// Its total and self samples as fractions (0 to 1) of all samples.
  final double totalShare;
  final double selfShare;

  /// Whether it matches the search.
  final bool match;

  bool get expandable => node.children.isNotEmpty;
}

/// Whether [node] or one of the frames it folds contains [query]
/// (lower case).
bool callTreeMatches(ProfilerNode node, String query) {
  if (query.isEmpty) return false;
  if (node.method.toLowerCase().contains(query)) return true;
  for (final String frame in node.collapsed) {
    if (frame.toLowerCase().contains(query)) return true;
  }
  return false;
}

/// The rows shown for [roots]: a node's children follow it, busiest first,
/// when its id is in [expanded]. With a [query] only matching frames and
/// the frames above them are listed, those above open; under a match the
/// tree is whole again and opens as [expanded] says.
List<CallTreeRow> flattenCallTree(
  List<ProfilerNode> roots, {
  required int samples,
  required Set<String> expanded,
  String query = '',
}) {
  final String needle = query.trim().toLowerCase();
  final List<CallTreeRow> rows = <CallTreeRow>[];
  final Map<ProfilerNode, bool> holds = <ProfilerNode, bool>{};
  bool below(ProfilerNode node) => holds[node] ??=
      callTreeMatches(node, needle) ||
      node.children.any((ProfilerNode child) => below(child));

  void visit(ProfilerNode node, String? parentKey, int depth, bool underMatch) {
    final String key = callTreeKey(parentKey ?? '', node);
    final bool match = callTreeMatches(node, needle);
    final bool whole = needle.isEmpty || underMatch || match;
    final bool leadsToMatch =
        !whole && node.children.any((ProfilerNode child) => below(child));
    final bool open =
        node.children.isNotEmpty && (leadsToMatch || expanded.contains(key));
    rows.add(
      CallTreeRow(
        node: node,
        key: key,
        parentKey: parentKey,
        depth: depth,
        expanded: open,
        totalShare: samples <= 0 ? 0 : node.total / samples,
        selfShare: samples <= 0 ? 0 : node.self / samples,
        match: match,
      ),
    );
    if (!open) return;
    for (final ProfilerNode child in hottestFirst(node.children)) {
      if (whole || below(child)) {
        visit(child, key, depth + 1, whole && needle.isNotEmpty);
      }
    }
  }

  for (final ProfilerNode root in hottestFirst(roots)) {
    if (needle.isEmpty || below(root)) visit(root, null, 0, false);
  }
  return rows;
}
