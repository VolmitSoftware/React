library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

enum ReactorStatus { healthy, warning, critical, info, neutral }

const String kReactorSuccess = 'var(--success)';
const String kReactorWarning = 'var(--warning)';
const String kReactorCritical = 'var(--destructive)';
const String kReactorInfo = 'var(--info)';
const String kReactorFg = 'var(--foreground)';
const String kReactorMuted = 'var(--muted-foreground)';
const String kReactorBorder = 'var(--border)';
const String kReactorHairline =
    'color-mix(in srgb, var(--border) 64%, transparent)';
const String kReactorPanel = 'var(--card)';
const String kReactorRadius = '0';

String reactorStatusColor(ReactorStatus status) => switch (status) {
  ReactorStatus.healthy => kReactorSuccess,
  ReactorStatus.warning => kReactorWarning,
  ReactorStatus.critical => kReactorCritical,
  ReactorStatus.info => kReactorInfo,
  ReactorStatus.neutral => kReactorMuted,
};

String reactorStatusSoft(ReactorStatus status, [int pct = 14]) =>
    'color-mix(in srgb, ${reactorStatusColor(status)} $pct%, transparent)';

Widget reactorBadge(String label, ReactorStatus status) => switch (status) {
  ReactorStatus.healthy => ArcaneStatusBadge.success(label, showPulse: false),
  ReactorStatus.warning => ArcaneStatusBadge.warning(label),
  ReactorStatus.critical => ArcaneStatusBadge.error(label),
  ReactorStatus.info => ArcaneStatusBadge.info(label),
  ReactorStatus.neutral => ArcaneStatusBadge.secondary(label),
};

Widget reactorEyebrow(String text) => dom.span(
  styles: const dom.Styles(
    raw: <String, String>{
      'font-size': '0.6875rem',
      'font-weight': '600',
      'letter-spacing': '0',
      'text-transform': 'uppercase',
      'color': 'var(--reactor-label)',
      'line-height': '1',
    },
  ),
  <Widget>[Component.text(text)],
);

Widget reactorStatusDot(
  ReactorStatus status, {
  double size = 8.0,
  String? label,
}) {
  final String color = reactorStatusColor(status);
  return dom.span(
    styles: dom.Styles(
      raw: <String, String>{
        'display': 'inline-block',
        'width': '${size}px',
        'height': '${size}px',
        'border-radius': '999px',
        'background': color,
        'box-shadow': '0 0 0 3px ${reactorStatusSoft(status, 18)}',
        'flex': '0 0 auto',
      },
    ),
    attributes: label == null
        ? null
        : <String, String>{'aria-label': label, 'title': label, 'role': 'img'},
    const <Widget>[],
  );
}

Widget reactorGrid({
  required List<Widget> children,
  String minWidth = '220px',
  String gap = '1rem',
}) {
  return dom.div(
    classes: 'reactor-grid',
    styles: dom.Styles(
      raw: <String, String>{
        'display': 'grid',
        'grid-template-columns': 'repeat(auto-fill, minmax($minWidth, 1fr))',
        'gap': gap,
      },
    ),
    children,
  );
}

class ReactorPage extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? actions;
  final Widget? leading;
  final List<Widget> children;
  final double gap;

  const ReactorPage({
    required this.title,
    this.subtitle,
    this.actions,
    this.leading,
    this.children = const <Widget>[],
    this.gap = 24,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return dom.div(
      classes: 'reactor-page',
      styles: dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '${gap}px',
          'width': '100%',
        },
      ),
      <Widget>[
        ReactorPageHeader(
          title: title,
          subtitle: subtitle,
          actions: actions,
          leading: leading,
        ),
        ...children,
      ],
    );
  }
}

class ReactorPageHeader extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? actions;
  final Widget? leading;

  const ReactorPageHeader({
    required this.title,
    this.subtitle,
    this.actions,
    this.leading,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final String? sub = subtitle;
    final Widget? lead = leading;
    final Widget? act = actions;
    return dom.div(
      classes: 'reactor-page-header',
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'flex-start',
          'justify-content': 'space-between',
          'gap': '1rem',
          'flex-wrap': 'wrap',
          'padding': '0.15rem 0 1.15rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.85rem',
              'min-width': '0',
            },
          ),
          <Widget>[
            ?lead,
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.3rem',
                  'min-width': '0',
                },
              ),
              <Widget>[
                dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '1.6rem',
                      'font-weight': '700',
                      'letter-spacing': '0',
                      'line-height': '1.1',
                      'color': kReactorFg,
                    },
                  ),
                  <Widget>[Component.text(title)],
                ),
                if (sub != null)
                  dom.div(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'font-size': '0.875rem',
                        'color': kReactorMuted,
                        'line-height': '1.4',
                      },
                    ),
                    <Widget>[Component.text(sub)],
                  ),
              ],
            ),
          ],
        ),
        if (act != null)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'gap': '0.5rem',
                'flex-wrap': 'wrap',
              },
            ),
            <Widget>[act],
          ),
      ],
    );
  }
}

class SectionPanel extends StatelessWidget {
  final String? label;
  final String? description;
  final Widget? trailing;
  final Widget? child;
  final List<Widget>? children;
  final double gap;
  final bool flush;

  const SectionPanel({
    this.label,
    this.description,
    this.trailing,
    this.child,
    this.children,
    this.gap = 16,
    this.flush = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final List<Widget> body = children ?? <Widget>[?child];
    final bool hasHeader =
        label != null || description != null || trailing != null;
    final bool hasBody = body.isNotEmpty;

    return Card.flat(
      fillWidth: true,
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.zero,
      child: dom.div(
        classes: 'reactor-panel',
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'overflow': 'hidden',
            'border-radius': kReactorRadius,
          },
        ),
        <Widget>[
          if (hasHeader) _header(hasBody),
          if (hasBody)
            dom.div(
              styles: dom.Styles(
                raw: <String, String>{
                  'padding': flush ? '0' : '1.15rem',
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '${gap}px',
                },
              ),
              body,
            ),
        ],
      ),
    );
  }

  Widget _header(bool hasBody) {
    final String? lbl = label;
    final String? desc = description;
    final Widget? trail = trailing;
    return dom.div(
      styles: dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'justify-content': 'space-between',
          'gap': '1rem',
          'padding': '0.85rem 1.15rem',
          if (hasBody) 'border-bottom': '1px solid $kReactorHairline',
          'background':
              'linear-gradient(90deg, color-mix(in srgb, var(--primary) 8%, transparent), transparent 72%)',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'gap': '0.25rem',
              'min-width': '0',
            },
          ),
          <Widget>[
            if (lbl != null) reactorEyebrow(lbl),
            if (desc != null)
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'font-size': '0.8rem',
                    'color': kReactorMuted,
                    'line-height': '1.4',
                  },
                ),
                <Widget>[Component.text(desc)],
              ),
          ],
        ),
        if (trail != null)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'gap': '0.5rem',
                'flex': '0 0 auto',
              },
            ),
            <Widget>[trail],
          ),
      ],
    );
  }
}

class MetricCard extends StatelessWidget {
  final String title;
  final String? value;
  final String? unit;
  final ReactorStatus status;
  final Widget? badge;
  final Widget child;

  const MetricCard({
    required this.title,
    this.value,
    this.unit,
    this.status = ReactorStatus.neutral,
    this.badge,
    required this.child,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final String? val = value;
    final String? u = unit;
    final Widget? bdg = badge;
    return Card.flat(
      fillWidth: true,
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.zero,
      child: dom.div(
        classes: 'reactor-metric-card',
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'overflow': 'hidden',
            'border-radius': kReactorRadius,
          },
        ),
        <Widget>[
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'flex-end',
                'justify-content': 'space-between',
                'gap': '1rem',
                'padding': '1rem 1.15rem 0.85rem',
              },
            ),
            <Widget>[
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'display': 'flex',
                    'flex-direction': 'column',
                    'gap': '0.45rem',
                  },
                ),
                <Widget>[
                  reactorEyebrow(title),
                  if (val != null)
                    dom.div(
                      styles: const dom.Styles(
                        raw: <String, String>{
                          'display': 'flex',
                          'align-items': 'baseline',
                          'gap': '0.3rem',
                        },
                      ),
                      <Widget>[
                        dom.span(
                          styles: const dom.Styles(
                            raw: <String, String>{
                              'font-size': '1.6rem',
                              'font-weight': '700',
                              'color': kReactorFg,
                              'line-height': '1',
                              'letter-spacing': '0',
                              'font-variant-numeric': 'tabular-nums',
                            },
                          ),
                          <Widget>[Component.text(val)],
                        ),
                        if (u != null)
                          dom.span(
                            styles: const dom.Styles(
                              raw: <String, String>{
                                'font-size': '0.85rem',
                                'color': kReactorMuted,
                              },
                            ),
                            <Widget>[Component.text(u)],
                          ),
                      ],
                    ),
                ],
              ),
              ?bdg,
            ],
          ),
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{'padding': '0.25rem 1.15rem 1.1rem'},
            ),
            <Widget>[child],
          ),
        ],
      ),
    );
  }
}

class ReactorStat extends StatelessWidget {
  final String label;
  final String value;
  final String? unit;
  final String? caption;
  final ReactorStatus status;

  const ReactorStat({
    required this.label,
    required this.value,
    this.unit,
    this.caption,
    this.status = ReactorStatus.neutral,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final String? u = unit;
    final String? cap = caption;
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.4rem',
          'min-width': '0',
        },
      ),
      <Widget>[
        reactorEyebrow(label),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'baseline',
              'gap': '0.3rem',
            },
          ),
          <Widget>[
            dom.span(
              styles: const dom.Styles(
                raw: <String, String>{
                  'font-size': '1.75rem',
                  'font-weight': '700',
                  'color': kReactorFg,
                  'line-height': '1',
                  'letter-spacing': '0',
                  'font-variant-numeric': 'tabular-nums',
                },
              ),
              <Widget>[Component.text(value)],
            ),
            if (u != null)
              dom.span(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'font-size': '0.85rem',
                    'color': kReactorMuted,
                  },
                ),
                <Widget>[Component.text(u)],
              ),
          ],
        ),
        if (cap != null)
          dom.div(
            styles: dom.Styles(
              raw: <String, String>{
                'font-size': '0.75rem',
                'color': status == ReactorStatus.neutral
                    ? kReactorMuted
                    : reactorStatusColor(status),
                'font-weight': '500',
              },
            ),
            <Widget>[Component.text(cap)],
          ),
      ],
    );
  }
}

class ReactorSparkline extends StatelessWidget {
  final List<double> values;
  final String color;
  final int height;
  final bool area;

  const ReactorSparkline({
    required this.values,
    this.color = kReactorMuted,
    this.height = 40,
    this.area = true,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final List<double> v = values;
    if (v.length < 2) {
      return dom.div(
        styles: dom.Styles(raw: <String, String>{'height': '${height}px'}),
        const <Widget>[],
      );
    }
    double lo = v.first;
    double hi = v.first;
    for (final double d in v) {
      if (d < lo) lo = d;
      if (d > hi) hi = d;
    }
    final double span = (hi - lo).abs() < 1e-9 ? 1.0 : (hi - lo);
    const double w = 100.0;
    const double h = 100.0;
    const double pad = 6.0;
    final double step = w / (v.length - 1);
    final StringBuffer line = StringBuffer();
    for (int i = 0; i < v.length; i++) {
      final double x = i * step;
      final double norm = (v[i] - lo) / span;
      final double y = h - pad - norm * (h - pad * 2);
      line.write(i == 0 ? 'M ' : 'L ');
      line.write('${x.toStringAsFixed(2)} ${y.toStringAsFixed(2)} ');
    }
    final String areaPath = '$line L ${w.toStringAsFixed(2)} $h L 0 $h Z';
    final String gradId = 'spark-${identityHashCode(this)}';

    return Component.element(
      tag: 'svg',
      attributes: <String, String>{
        'width': '100%',
        'height': '$height',
        'viewBox': '0 0 100 100',
        'preserveAspectRatio': 'none',
        'aria-hidden': 'true',
      },
      children: <Component>[
        Component.element(
          tag: 'defs',
          children: <Component>[
            Component.element(
              tag: 'linearGradient',
              attributes: <String, String>{
                'id': gradId,
                'x1': '0',
                'y1': '0',
                'x2': '0',
                'y2': '1',
              },
              children: <Component>[
                Component.element(
                  tag: 'stop',
                  attributes: <String, String>{
                    'offset': '0%',
                    'stop-color': color,
                    'stop-opacity': '0.28',
                  },
                ),
                Component.element(
                  tag: 'stop',
                  attributes: <String, String>{
                    'offset': '100%',
                    'stop-color': color,
                    'stop-opacity': '0',
                  },
                ),
              ],
            ),
          ],
        ),
        if (area)
          Component.element(
            tag: 'path',
            attributes: <String, String>{
              'd': areaPath,
              'fill': 'url(#$gradId)',
              'stroke': 'none',
            },
          ),
        Component.element(
          tag: 'path',
          attributes: <String, String>{
            'd': line.toString().trim(),
            'fill': 'none',
            'stroke': color,
            'stroke-width': '2',
            'stroke-linecap': 'round',
            'stroke-linejoin': 'round',
            'vector-effect': 'non-scaling-stroke',
          },
        ),
      ],
    );
  }
}
