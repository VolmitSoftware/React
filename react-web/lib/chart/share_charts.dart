/// The share charts: a donut of parts of a whole with a legend of names,
/// values and shares, and stacked bars that split each row by kind. A slice,
/// a legend name or a bar can link to its owner's page. Hovering or focusing
/// a slice names it in the middle of the ring and dims the others; every
/// mark has a title. Laid out inline (the app's CSS budget is spent), with
/// wrapping instead of breakpoints so a phone stacks the legend under the
/// ring.
library;

import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart';
import 'package:universal_web/web.dart' as web;

import 'share_layout.dart';

/// The series colours of the charts (`reactor-chart-series-N`), the last
/// one the muted "Other".
const List<String> kSeriesColors = <String>[
  'var(--info)',
  'var(--warning)',
  'var(--success)',
  'color-mix(in oklab, var(--info) 50%, var(--destructive))',
  'color-mix(in oklab, var(--warning) 50%, var(--destructive))',
  'var(--muted-foreground)',
];

String _percent(double share) =>
    '${(share * 100).toStringAsFixed(share >= 0.1 || share == 0 ? 1 : 2)}%';

Component _swatch(String color) => dom.span(
  classes: 'reactor-chart-tooltip-swatch',
  attributes: const <String, String>{'aria-hidden': 'true'},
  styles: dom.Styles(raw: <String, String>{'color': color}),
  const <Component>[],
);

const Map<String, String> _kClip = <String, String>{
  'display': 'block',
  'min-width': '0',
  'overflow': 'hidden',
  'text-overflow': 'ellipsis',
  'white-space': 'nowrap',
};

const Map<String, String> _kNumber = <String, String>{
  'font-variant-numeric': 'tabular-nums',
  'text-align': 'end',
  'white-space': 'nowrap',
};

const Map<String, String> _kList = <String, String>{
  'display': 'grid',
  'gap': '0.125rem',
  'min-width': '0',
  'margin': '0',
  'padding': '0',
  'list-style': 'none',
  'font-size': '0.8125rem',
};

class DonutChart extends StatefulComponent {
  const DonutChart({
    required this.slices,
    required this.label,
    required this.total,
    required this.valueText,
    this.href,
    this.colorOf,
    super.key,
  });

  final List<ShareSlice> slices;

  /// The chart's accessible name; also under the total in the ring.
  final String label;

  /// The whole, written as the ring's centre shows it.
  final String total;
  final String Function(double value) valueText;

  /// Where a slice leads; no link when null or for "Other".
  final String? Function(String id)? href;

  /// The CSS colour of a slice; its series colour when null.
  final String Function(ShareSlice slice)? colorOf;

  @override
  State<DonutChart> createState() => _DonutChartState();
}

class _DonutChartState extends State<DonutChart> {
  int? _hover;

  void _set(int? index) {
    if (index != _hover) setState(() => _hover = index);
  }

  String _color(ShareSlice slice) =>
      component.colorOf?.call(slice) ??
      kSeriesColors[slice.color % kSeriesColors.length];

  String? _link(ShareSlice slice) {
    final String? id = slice.id;
    return id == null ? null : component.href?.call(id);
  }

  Map<String, void Function(web.Event)> _hoverEvents(int index) =>
      <String, void Function(web.Event)>{
        'mouseenter': (web.Event _) => _set(index),
        'mouseleave': (web.Event _) => _set(null),
        'focusin': (web.Event _) => _set(index),
        'focusout': (web.Event _) => _set(null),
      };

  @override
  Component build(BuildContext context) {
    final List<ShareSlice> slices = component.slices;
    final ShareSlice? active = _hover == null || _hover! >= slices.length
        ? null
        : slices[_hover!];
    return dom.figure(
      classes: 'reactor-share',
      attributes: <String, String>{'aria-label': component.label},
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-wrap': 'wrap',
          'align-items': 'center',
          'justify-content': 'center',
          'gap': '1rem',
          'min-width': '0',
          'margin': '0',
        },
      ),
      <Component>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'position': 'relative',
              'flex': '0 0 9.5rem',
              'width': '9.5rem',
              'height': '9.5rem',
            },
          ),
          <Component>[
            dom.svg(
              viewBox: '0 0 100 100',
              attributes: const <String, String>{
                'role': 'list',
                'width': '100%',
                'height': '100%',
              },
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'block',
                  'overflow': 'visible',
                },
              ),
              <Component>[
                for (final (int index, ShareSlice slice) in slices.indexed)
                  _slice(index, slice),
              ],
            ),
            dom.div(
              attributes: const <String, String>{'aria-live': 'polite'},
              styles: const dom.Styles(
                raw: <String, String>{
                  'position': 'absolute',
                  'inset': '22%',
                  'display': 'grid',
                  'place-content': 'center',
                  'text-align': 'center',
                  'pointer-events': 'none',
                },
              ),
              <Component>[
                dom.strong(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '1rem',
                      'font-variant-numeric': 'tabular-nums',
                    },
                  ),
                  <Component>[
                    Component.text(
                      active == null ? component.total : _percent(active.share),
                    ),
                  ],
                ),
                dom.span(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      ..._kClip,
                      'color': 'var(--muted-foreground)',
                      'font-size': '0.6875rem',
                    },
                  ),
                  <Component>[Component.text(active?.label ?? component.label)],
                ),
              ],
            ),
          ],
        ),
        dom.ul(
          styles: const dom.Styles(
            raw: <String, String>{..._kList, 'flex': '1 1 14rem'},
          ),
          <Component>[
            for (final (int index, ShareSlice slice) in slices.indexed)
              _legendRow(index, slice),
          ],
        ),
      ],
    );
  }

  Component _slice(int index, ShareSlice slice) {
    final String title =
        '${slice.label}: ${component.valueText(slice.value)} (${_percent(slice.share)})';
    final bool dimmed = _hover != null && _hover != index;
    final Component path = Component.element(
      tag: 'path',
      attributes: <String, String>{
        'd': donutArcPath(slice.start, slice.end),
        'fill': _color(slice),
        'stroke': 'var(--card)',
        'stroke-width': '1.5',
        'opacity': dimmed ? '0.5' : '1',
        'role': 'listitem',
        'aria-label': title,
      },
      children: <Component>[
        Component.element(
          tag: 'title',
          children: <Component>[Component.text(title)],
        ),
      ],
    );
    final String? link = _link(slice);
    return Component.element(
      tag: link == null ? 'g' : 'a',
      attributes: <String, String>{
        if (link == null) 'tabindex': '0' else 'href': link,
        'data-slice': slice.id ?? '',
      },
      styles: const dom.Styles(raw: <String, String>{'cursor': 'pointer'}),
      events: _hoverEvents(index),
      children: <Component>[path],
    );
  }

  Component _legendRow(int index, ShareSlice slice) {
    final String? link = _link(slice);
    final Component name = dom.span(
      styles: const dom.Styles(raw: _kClip),
      <Component>[Component.text(slice.label)],
    );
    return dom.li(
      styles: dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'grid-template-columns': '0.5rem minmax(0, 1fr) auto 3.25rem',
          'align-items': 'center',
          'gap': '0.5rem',
          'min-height': '1.75rem',
          'padding': '0 0.375rem',
          'border-radius': 'var(--radius-sm)',
          if (_hover == index) 'background': 'var(--muted)',
        },
      ),
      events: <String, void Function(web.Event)>{
        'mouseenter': (web.Event _) => _set(index),
        'mouseleave': (web.Event _) => _set(null),
      },
      <Component>[
        _swatch(_color(slice)),
        if (link == null)
          name
        else
          dom.a(
            href: link,
            styles: const dom.Styles(
              raw: <String, String>{'min-width': '0', 'color': 'inherit'},
            ),
            <Component>[name],
          ),
        dom.span(styles: const dom.Styles(raw: _kNumber), <Component>[
          Component.text(component.valueText(slice.value)),
        ]),
        dom.span(
          styles: const dom.Styles(
            raw: <String, String>{
              ..._kNumber,
              'color': 'var(--muted-foreground)',
            },
          ),
          <Component>[Component.text(_percent(slice.share))],
        ),
      ],
    );
  }
}

/// One kind in [StackedBars]: its key, name and CSS colour.
class BarKind {
  const BarKind(this.key, this.label, this.color);

  final String key;
  final String label;
  final String color;
}

/// One bar: a name, where it leads and its value per kind.
class BarRow {
  const BarRow({
    required this.id,
    required this.label,
    required this.values,
    this.href,
  });

  final String id;
  final String label;
  final Map<String, double> values;
  final String? href;

  double get total =>
      values.values.fold(0, (double sum, double value) => sum + value);
}

/// Horizontal bars on one scale (the biggest row fills the width), each
/// split into [kinds] in their order, with a legend of the kinds above.
class StackedBars extends StatelessComponent {
  const StackedBars({
    required this.rows,
    required this.kinds,
    required this.label,
    required this.valueText,
    super.key,
  });

  final List<BarRow> rows;
  final List<BarKind> kinds;
  final String label;
  final String Function(double value) valueText;

  @override
  Component build(BuildContext context) {
    double scale = 0;
    for (final BarRow row in rows) {
      if (row.total > scale) scale = row.total;
    }
    final Map<String, BarKind> byKey = <String, BarKind>{
      for (final BarKind kind in kinds) kind.key: kind,
    };
    final List<String> order = <String>[
      for (final BarKind kind in kinds) kind.key,
    ];
    return dom.figure(
      classes: 'reactor-bars',
      attributes: <String, String>{'aria-label': label},
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'gap': '0.75rem',
          'min-width': '0',
          'margin': '0',
        },
      ),
      <Component>[
        dom.ul(
          classes: 'reactor-chart-legend',
          styles: const dom.Styles(
            raw: <String, String>{'margin': '0', 'padding': '0'},
          ),
          <Component>[
            for (final BarKind kind in kinds)
              dom.li(<Component>[
                _swatch(kind.color),
                Component.text(kind.label),
              ]),
          ],
        ),
        dom.ul(styles: const dom.Styles(raw: _kList), <Component>[
          for (final BarRow row in rows)
            _row(
              row,
              stackSegments(row.values, order: order, scale: scale),
              byKey,
            ),
        ]),
      ],
    );
  }

  Component _row(
    BarRow row,
    List<BarSegment> segments,
    Map<String, BarKind> byKey,
  ) {
    String name(String key) => byKey[key]?.label ?? key;
    final String summary = <String>[
      for (final BarSegment segment in segments)
        '${name(segment.key)} ${valueText(segment.value)}',
    ].join(', ');
    final Component title = dom.span(
      styles: const dom.Styles(raw: _kClip),
      <Component>[Component.text(row.label)],
    );
    final String? href = row.href;
    return dom.li(
      attributes: <String, String>{'aria-label': '${row.label}: $summary'},
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'grid-template-columns':
              'minmax(0, clamp(5rem, 22vw, 8rem)) minmax(0, 1fr) auto',
          'align-items': 'center',
          'gap': '0.5rem',
          'min-height': '1.75rem',
        },
      ),
      <Component>[
        if (href == null)
          title
        else
          dom.a(
            href: href,
            styles: const dom.Styles(
              raw: <String, String>{'min-width': '0', 'color': 'inherit'},
            ),
            <Component>[title],
          ),
        dom.span(
          attributes: <String, String>{'title': summary},
          styles: const dom.Styles(
            raw: <String, String>{'position': 'relative', 'height': '0.75rem'},
          ),
          <Component>[
            for (final (int index, BarSegment segment) in segments.indexed)
              dom.span(
                attributes: <String, String>{
                  'title': '${name(segment.key)}: ${valueText(segment.value)}',
                },
                styles: dom.Styles(
                  raw: <String, String>{
                    'position': 'absolute',
                    'top': '0',
                    'bottom': '0',
                    'left': '${(segment.x * 100).toStringAsFixed(3)}%',
                    'width': '${(segment.width * 100).toStringAsFixed(3)}%',
                    'background':
                        byKey[segment.key]?.color ?? kSeriesColors.last,
                    if (index < segments.length - 1)
                      'box-shadow': 'inset -2px 0 0 var(--card)',
                    'border-radius': segments.length == 1
                        ? '4px'
                        : index == 0
                        ? '4px 0 0 4px'
                        : index == segments.length - 1
                        ? '0 4px 4px 0'
                        : '0',
                  },
                ),
                const <Component>[],
              ),
          ],
        ),
        dom.span(
          styles: const dom.Styles(
            raw: <String, String>{
              ..._kNumber,
              'color': 'var(--muted-foreground)',
            },
          ),
          <Component>[Component.text(valueText(row.total))],
        ),
      ],
    );
  }
}
