/// The call tree as an icicle: threads on top, callees below, each frame as
/// wide as its share of its parent. A click zooms to a frame (the top row
/// zooms out one level) and the trail above it walks back up. Frames are
/// tinted by owner; with [FlameGraph.highlight] only that owner's frames
/// carry colour.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;

import '../model/flame_layout.dart';
import '../model/profiler.dart';
import '../ui/kit/density.dart';

/// The series colours of the charts, so an owner reads the same here.
const List<String> _kSeries = <String>[
  'var(--info)',
  'var(--warning)',
  'var(--success)',
  'color-mix(in oklab, var(--info) 50%, var(--destructive))',
  'color-mix(in oklab, var(--warning) 50%, var(--destructive))',
];

/// The tint of a frame owned by [owner]: the server's and the JDK's own
/// frames stay muted, a plugin or mod takes a series colour by its id.
String flameColor(String owner, {String? highlight}) {
  final bool platform =
      owner.isEmpty || owner == 'minecraft' || owner == 'java';
  if (highlight != null) {
    return owner == highlight
        ? 'color-mix(in oklab, var(--warning) 55%, var(--card))'
        : 'var(--muted)';
  }
  if (platform) return 'var(--muted)';
  int hash = 0;
  for (final int unit in owner.codeUnits) {
    hash = (hash * 31 + unit) & 0x7fffffff;
  }
  return 'color-mix(in oklab, ${_kSeries[hash % _kSeries.length]} 45%, var(--card))';
}

int _nextFlameId = 0;

class FlameGraph extends StatefulWidget {
  const FlameGraph({
    required this.tree,
    required this.rootLabel,
    this.highlight,
    this.maxRows = 40,
    super.key,
  });

  final ProfilerTree tree;

  /// The name of the top frame that holds every thread.
  final String rootLabel;

  /// The owner whose frames are coloured; every owner when null.
  final String? highlight;
  final int maxRows;

  @override
  State<FlameGraph> createState() => _FlameGraphState();
}

class _FlameGraphState extends State<FlameGraph> {
  final String _id = 'reactor-flame-${_nextFlameId++}';

  /// The zoomed frame, by method names from the top, so it holds across
  /// refreshes that reorder siblings.
  List<String> _focus = const <String>[];

  void _zoom(ProfilerNode root, List<int> path) {
    final List<ProfilerNode> trail = flameTrail(root, path);
    setState(() {
      _focus = <String>[
        for (final ProfilerNode node in trail.skip(1)) node.method,
      ];
    });
  }

  static String _percent(int part, int whole) =>
      whole <= 0 ? '0%' : profilerPercentText(part * 100 / whole);

  @override
  Widget build(BuildContext context) {
    final ProfilerNode root = flameRoot(component.tree, component.rootLabel);
    final List<int> path = flamePathOf(root, _focus);
    final List<ProfilerNode> trail = flameTrail(root, path);
    final List<FlameBox> boxes = layoutFlame(
      root,
      focus: path,
      minWidth: 0.004,
      maxRows: component.maxRows,
    );
    final int rows = flameRows(boxes);
    final int whole = root.total;
    return dom.div(classes: 'reactor-flame-frame', <Widget>[
      DensityScope.dense(
        child: dom.nav(
          classes: 'reactor-flame-crumbs',
          attributes: <String, String>{'aria-label': component.rootLabel},
          <Widget>[
            for (final (int index, ProfilerNode node) in trail.indexed)
              Button(
                label: profilerShortMethod(node.method),
                size: buttonSize(Density.dense),
                variant: index == trail.length - 1
                    ? ButtonVariant.secondary
                    : ButtonVariant.ghost,
                attributes: <String, String>{
                  'title': node.method,
                  if (index == trail.length - 1) 'aria-current': 'true',
                },
                onPressed: () => _zoom(root, path.sublist(0, index)),
              ),
          ],
        ),
      ),
      dom.div(
        id: _id,
        classes: 'reactor-flame',
        attributes: <String, String>{
          'role': 'group',
          'aria-label': component.rootLabel,
        },
        styles: dom.Styles(
          raw: <String, String>{'height': '${rows * 1.375}rem'},
        ),
        <Widget>[for (final FlameBox box in boxes) _cell(root, box, whole)],
      ),
    ]);
  }

  Widget _cell(ProfilerNode root, FlameBox box, int whole) {
    final ProfilerNode node = box.node;
    final String share = _percent(node.total, whole);
    final String owner = node.ownerName.isEmpty ? '' : ' · ${node.ownerName}';
    final String label = '${node.method}$owner · $share';
    final List<int> target = box.depth == 0 && box.path.isNotEmpty
        ? box.path.sublist(0, box.path.length - 1)
        : box.path;
    return dom.button(
      classes: 'reactor-flame-cell',
      attributes: <String, String>{
        'type': 'button',
        'title': '$label · ${node.total}',
        'aria-label': label,
        'data-method': node.method,
      },
      styles: dom.Styles(
        raw: <String, String>{
          'left': '${(box.x * 100).toStringAsFixed(3)}%',
          'width': '${(box.width * 100).toStringAsFixed(3)}%',
          'top': '${box.depth * 1.375}rem',
          '--flame-color': flameColor(
            node.owner,
            highlight: component.highlight,
          ),
        },
      ),
      events: <String, EventCallback>{'click': (_) => _zoom(root, target)},
      <Widget>[Component.text(profilerShortMethod(node.method))],
    );
  }
}
