/// The call tree as an indented list: one row per frame with its class and
/// method, package, owner, total share (with a bar) and self share. The
/// busiest path opens on its own until the viewer opens or closes a row.
/// A "(N frames)" row stands for folded plumbing; its name lists them.
/// One tab stop: the arrow keys walk and open rows, Home and End jump,
/// Enter and Space open or close (Enter on a folded row lists its frames).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

import '../model/call_tree.dart';
import '../model/profiler.dart';
import '../ui/kit/browser.dart';
import 'flame_graph.dart';

int _nextCallTreeId = 0;

class CallTreeView extends StatefulWidget {
  const CallTreeView({
    required this.tree,
    required this.label,
    required this.totalLabel,
    required this.selfLabel,
    required this.noMatches,
    this.query = '',
    this.highlight,
    super.key,
  });

  final ProfilerTree tree;

  /// The list's accessible name.
  final String label;

  /// The headings of the share columns.
  final String totalLabel;
  final String selfLabel;

  /// Said when nothing matches [query].
  final String noMatches;

  /// Lists only frames containing this, and the frames above them.
  final String query;

  /// The owner whose badge stands out; every owner's when null.
  final String? highlight;

  @override
  State<CallTreeView> createState() => _CallTreeViewState();
}

class _CallTreeViewState extends State<CallTreeView> {
  final String _id = 'reactor-calltree-${_nextCallTreeId++}';
  Set<String> _open = <String>{};
  final Set<String> _framesShown = <String>{};
  bool _touched = false;
  String? _focusKey;

  ProfilerTree? _source;
  List<ProfilerNode> _roots = const <ProfilerNode>[];

  List<ProfilerNode> _readable() {
    if (!identical(_source, component.tree)) {
      _source = component.tree;
      _roots = readableRoots(component.tree);
    }
    return _roots;
  }

  void _toggle(CallTreeRow row) {
    if (!row.expandable) return;
    setState(() {
      _touched = true;
      _open = <String>{..._open};
      if (row.expanded) {
        _open.remove(row.key);
      } else {
        _open.add(row.key);
      }
      _focusKey = row.key;
    });
  }

  void _toggleFrames(CallTreeRow row) {
    setState(() {
      if (!_framesShown.remove(row.key)) _framesShown.add(row.key);
      _focusKey = row.key;
    });
  }

  void _focusRow(List<CallTreeRow> rows, int index) {
    if (index < 0 || index >= rows.length) return;
    setState(() => _focusKey = rows[index].key);
    focusById(_rowId(index));
  }

  String _rowId(int index) => '$_id-$index';

  /// Whether [event] came from a folded row's frame-list button, which
  /// handles it.
  static bool _inFoldedButton(web.Event event) {
    final JSAny? target = event.target;
    return target != null &&
        target.isA<web.Element>() &&
        (target as web.Element).closest('[data-folded]') != null;
  }

  void _keys(web.Event raw, List<CallTreeRow> rows) {
    final web.KeyboardEvent event = raw as web.KeyboardEvent;
    final int at = rows.indexWhere((CallTreeRow row) => row.key == _focusKey);
    final int current = at < 0 ? 0 : at;
    if (rows.isEmpty) return;
    final CallTreeRow row = rows[current];
    bool handled = true;
    switch (event.key) {
      case 'ArrowDown':
        _focusRow(rows, current + 1);
      case 'ArrowUp':
        _focusRow(rows, current - 1);
      case 'Home':
        _focusRow(rows, 0);
      case 'End':
        _focusRow(rows, rows.length - 1);
      case 'ArrowRight':
        if (row.expandable && !row.expanded) {
          _toggle(row);
        } else if (row.expanded) {
          _focusRow(rows, current + 1);
        }
      case 'ArrowLeft':
        if (row.expanded) {
          _toggle(row);
        } else if (row.parentKey case final String parent) {
          _focusRow(
            rows,
            rows.indexWhere((CallTreeRow each) => each.key == parent),
          );
        }
      case 'Enter' when row.node.isCollapsed:
        _toggleFrames(row);
      case 'Enter' || ' ':
        _toggle(row);
      default:
        handled = false;
    }
    if (handled) event.preventDefault();
  }

  @override
  Widget build(BuildContext context) {
    final List<ProfilerNode> roots = _readable();
    final int samples = treeSamples(component.tree);
    if (!_touched) _open = hotPathKeys(roots, samples: samples);
    final List<CallTreeRow> rows = flattenCallTree(
      roots,
      samples: samples,
      expanded: _open,
      query: component.query,
    );
    if (rows.isEmpty) {
      return dom.p(classes: 'ui-caption', <Widget>[
        Component.text(component.noMatches),
      ]);
    }
    final int focused = rows.indexWhere(
      (CallTreeRow row) => row.key == _focusKey,
    );
    return dom.div(styles: _kFrame, <Widget>[
      dom.div(
        styles: _kHead,
        attributes: const <String, String>{'aria-hidden': 'true'},
        <Widget>[
          dom.span(const <Widget>[]),
          dom.span(<Widget>[Component.text(component.totalLabel)]),
          dom.span(<Widget>[Component.text(component.selfLabel)]),
        ],
      ),
      dom.div(
        id: _id,
        styles: _kTree,
        attributes: <String, String>{
          'role': 'tree',
          'aria-label': component.label,
        },
        events: <String, EventCallback>{
          'keydown': (web.Event event) => _keys(event, rows),
        },
        <Widget>[
          for (final (int index, CallTreeRow row) in rows.indexed)
            _row(row, index, focused < 0 ? index == 0 : index == focused),
        ],
      ),
    ]);
  }

  Widget _row(CallTreeRow row, int index, bool tabStop) {
    final ProfilerNode node = row.node;
    final FrameName name = splitFrame(node.method);
    final bool folded = node.isCollapsed;
    final bool framesOpen = folded && _framesShown.contains(row.key);
    final bool owned = !isPlatformOwner(node.owner);
    final String total = profilerPercentText(row.totalShare * 100);
    final String self = profilerPercentText(row.selfShare * 100);
    final String? highlight = component.highlight;
    final String indent =
        'min(calc(${row.depth.clamp(0, 24)} * 0.875rem), 18vw)';
    final String shortColor = row.match
        ? 'var(--warning)'
        : (folded || node.isOther
              ? 'var(--muted-foreground)'
              : 'var(--foreground)');
    return dom.div(
      id: _rowId(index),
      classes: 'reactor-calltree-row',
      attributes: <String, String>{
        'role': 'treeitem',
        'tabindex': tabStop ? '0' : '-1',
        'aria-level': '${row.depth + 1}',
        if (row.expandable) 'aria-expanded': '${row.expanded}',
        'aria-label': '${name.short} $total',
        'data-method': node.method,
        if (row.match) 'data-match': '',
      },
      styles: _kRow,
      events: <String, EventCallback>{
        'click': (web.Event event) {
          if (!_inFoldedButton(event)) _toggle(row);
        },
        'focus': (web.Event _) => _focusKey = row.key,
      },
      <Widget>[
        dom.div(
          styles: dom.Styles(
            raw: <String, String>{..._kLine, 'padding-inline-start': indent},
          ),
          <Widget>[
            dom.span(
              styles: row.expanded ? _kChevronOpen : _kChevron,
              attributes: const <String, String>{'aria-hidden': 'true'},
              <Widget>[
                if (row.expandable) ArcaneIcon.chevronRight(size: IconSize.sm),
              ],
            ),
            if (folded)
              dom.div(styles: _kName, <Widget>[
                Button(
                  label: node.method,
                  size: ButtonSize.sm,
                  variant: ButtonVariant.ghost,
                  attributes: <String, String>{
                    'tabindex': '-1',
                    'aria-expanded': '$framesOpen',
                    'data-folded': '',
                  },
                  onPressed: () => _toggleFrames(row),
                ),
              ])
            else
              dom.span(
                styles: _kName,
                attributes: <String, String>{'title': node.method},
                <Widget>[
                  dom.span(
                    styles: dom.Styles(
                      raw: <String, String>{
                        ..._kClip,
                        'color': shortColor,
                        if (highlight != null && node.owner == highlight)
                          'font-weight': '600',
                      },
                    ),
                    <Widget>[Component.text(name.short)],
                  ),
                  if (name.package.isNotEmpty)
                    dom.span(styles: _kPackage, <Widget>[
                      Component.text(name.package),
                    ]),
                ],
              ),
            if (owned)
              dom.span(
                styles: dom.Styles(
                  raw: <String, String>{
                    ..._kOwner,
                    'background': flameColor(node.owner, highlight: highlight),
                  },
                ),
                <Widget>[Component.text(node.ownerName)],
              )
            else
              dom.span(const <Widget>[]),
            dom.span(styles: _kTotal, <Widget>[
              dom.span(
                styles: _kBar,
                attributes: const <String, String>{'aria-hidden': 'true'},
                <Widget>[
                  dom.span(
                    styles: dom.Styles(
                      raw: <String, String>{
                        ..._kBarFill,
                        'width':
                            '${(row.totalShare * 100).clamp(0, 100).toStringAsFixed(2)}%',
                      },
                    ),
                    const <Widget>[],
                  ),
                ],
              ),
              dom.span(styles: _kNumber, <Widget>[Component.text(total)]),
            ]),
            dom.span(styles: _kSelf, <Widget>[Component.text(self)]),
          ],
        ),
        if (framesOpen)
          dom.ol(
            styles: dom.Styles(
              raw: <String, String>{
                ..._kFrames,
                'padding-inline-start': 'calc($indent + 2.5rem)',
              },
            ),
            <Widget>[
              for (final String frame in node.collapsed)
                dom.li(
                  styles: const dom.Styles(raw: _kClip),
                  attributes: <String, String>{'title': frame},
                  <Widget>[Component.text(splitFrame(frame).short)],
                ),
            ],
          ),
      ],
    );
  }
}

/// The columns every row and the heading share: name, share bar, self.
const String _kTotalColumn = 'clamp(3.25rem, 30vw - 4rem, 7.5rem)';
const String _kSelfColumn = '3.5rem';

const Map<String, String> _kClip = <String, String>{
  'min-width': '0',
  'overflow': 'hidden',
  'text-overflow': 'ellipsis',
  'white-space': 'nowrap',
};

const dom.Styles _kFrame = dom.Styles(
  raw: <String, String>{'display': 'grid', 'min-width': '0'},
);

const dom.Styles _kHead = dom.Styles(
  raw: <String, String>{
    'display': 'grid',
    'grid-template-columns': 'minmax(0, 1fr) $_kTotalColumn $_kSelfColumn',
    'gap': '0.5rem',
    'padding': '0 0.5rem 0.25rem',
    'border-bottom': '1px solid var(--border)',
    'color': 'var(--muted-foreground)',
    'font-size': '0.75rem',
    'text-align': 'end',
  },
);

const dom.Styles _kTree = dom.Styles(
  raw: <String, String>{
    'display': 'grid',
    'min-width': '0',
    'font-size': '0.8125rem',
  },
);

const dom.Styles _kRow = dom.Styles(
  raw: <String, String>{
    'min-width': '0',
    'padding': '0 0.5rem',
    'border-radius': 'var(--radius-sm)',
    'cursor': 'pointer',
  },
);

const Map<String, String> _kLine = <String, String>{
  'display': 'grid',
  'grid-template-columns':
      'auto minmax(0, 1fr) auto $_kTotalColumn $_kSelfColumn',
  'align-items': 'center',
  'gap': '0.5rem',
  'min-width': '0',
  'min-height': '1.75rem',
};

const dom.Styles _kChevron = dom.Styles(
  raw: <String, String>{
    'display': 'inline-flex',
    'width': '1rem',
    'color': 'var(--muted-foreground)',
  },
);

const dom.Styles _kChevronOpen = dom.Styles(
  raw: <String, String>{
    'display': 'inline-flex',
    'width': '1rem',
    'color': 'var(--muted-foreground)',
    'transform': 'rotate(90deg)',
  },
);

const dom.Styles _kName = dom.Styles(
  raw: <String, String>{
    'display': 'flex',
    'align-items': 'baseline',
    'gap': '0.5rem',
    'min-width': '0',
    'overflow': 'hidden',
    'white-space': 'nowrap',
    'font': '0.75rem var(--font-mono)',
  },
);

const dom.Styles _kPackage = dom.Styles(
  raw: <String, String>{
    ..._kClip,
    'flex': '1 1 0',
    'color': 'var(--muted-foreground)',
    'font-size': '0.6875rem',
  },
);

const Map<String, String> _kOwner = <String, String>{
  ..._kClip,
  'max-width': 'clamp(4rem, 18vw, 9rem)',
  'padding': '0 0.375rem',
  'border-radius': 'var(--radius-sm)',
  'color': 'var(--foreground)',
  'font-size': '0.6875rem',
  'line-height': '1.125rem',
};

const dom.Styles _kTotal = dom.Styles(
  raw: <String, String>{
    'display': 'flex',
    'align-items': 'center',
    'gap': '0.375rem',
    'min-width': '0',
  },
);

const dom.Styles _kBar = dom.Styles(
  raw: <String, String>{
    'flex': '1 1 0',
    'min-width': '0',
    'height': '0.375rem',
    'overflow': 'hidden',
    'border-radius': '999px',
    'background': 'var(--muted)',
  },
);

const Map<String, String> _kBarFill = <String, String>{
  'display': 'block',
  'height': '100%',
  'background': 'var(--info)',
};

const dom.Styles _kNumber = dom.Styles(
  raw: <String, String>{
    'flex': '0 0 3.25rem',
    'font-variant-numeric': 'tabular-nums',
    'text-align': 'end',
    'white-space': 'nowrap',
  },
);

const dom.Styles _kSelf = dom.Styles(
  raw: <String, String>{
    'color': 'var(--muted-foreground)',
    'font-variant-numeric': 'tabular-nums',
    'text-align': 'end',
    'white-space': 'nowrap',
  },
);

const Map<String, String> _kFrames = <String, String>{
  'display': 'grid',
  'margin': '0 0 0.25rem',
  'list-style': 'none',
  'color': 'var(--muted-foreground)',
  'font': '0.6875rem/1.25rem var(--font-mono)',
};
