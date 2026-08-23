library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../service/react_exceptions.dart';

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

String localizedReactorError(Object error) => switch (error) {
  ReactAuthException() => reactorText(ReactorText.errorAuthentication),
  ReactUnavailable() => reactorText(ReactorText.errorUnavailable),
  ReactBadRequest() => reactorText(ReactorText.errorBadRequest),
  ReactForbidden() => reactorText(ReactorText.errorForbidden),
  ReactNotFound() => reactorText(ReactorText.errorNotFound),
  ReactConflict() => reactorText(ReactorText.errorConflict),
  _ => reactorText(ReactorText.errorUnexpected),
};

String reactorStatusColor(ReactorStatus status) => switch (status) {
  ReactorStatus.healthy => kReactorSuccess,
  ReactorStatus.warning => kReactorWarning,
  ReactorStatus.critical => kReactorCritical,
  ReactorStatus.info => kReactorInfo,
  ReactorStatus.neutral => kReactorMuted,
};

String reactorStatusSoft(ReactorStatus status, [int pct = 14]) =>
    'color-mix(in srgb, ${reactorStatusColor(status)} $pct%, transparent)';

Widget reactorBadge(String label, ReactorStatus status) => dom.span(
  classes: 'reactor-status-label is-${status.name}',
  attributes: <String, String>{'role': 'status'},
  <Widget>[Component.text(label)],
);

Widget reactorEyebrow(String text) =>
    dom.span(classes: 'reactor-eyebrow', <Widget>[Component.text(text)]);

Widget reactorStatusDot(
  ReactorStatus status, {
  double size = 8.0,
  String? label,
}) {
  final String color = reactorStatusColor(status);
  final Widget icon = switch (status) {
    ReactorStatus.healthy => ArcaneIcon.check(size: IconSize.xs),
    ReactorStatus.warning => ArcaneIcon.triangleAlert(size: IconSize.xs),
    ReactorStatus.critical => ArcaneIcon.siren(size: IconSize.xs),
    ReactorStatus.info => ArcaneIcon.activity(size: IconSize.xs),
    ReactorStatus.neutral => ArcaneIcon.minus(size: IconSize.xs),
  };
  return dom.span(
    classes: 'reactor-state-symbol is-${status.name}',
    styles: dom.Styles(
      raw: <String, String>{
        'width': '${size + 6}px',
        'height': '${size + 6}px',
        'color': color,
      },
    ),
    attributes: label == null
        ? null
        : <String, String>{'aria-label': label, 'title': label, 'role': 'img'},
    <Widget>[icon],
  );
}

Widget reactorGrid({
  required List<Widget> children,
  String minWidth = '180px',
  String gap = '0',
}) {
  return dom.div(
    classes: 'reactor-grid reactor-metric-bank',
    styles: dom.Styles(
      raw: <String, String>{
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
    this.gap = 0,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(classes: 'reactor-page', <Widget>[
      ReactorPageHeader(
        title: title,
        subtitle: subtitle,
        actions: actions,
        leading: leading,
      ),
      dom.div(
        classes: 'reactor-page-body',
        styles: dom.Styles(raw: <String, String>{'gap': '${gap}px'}),
        children,
      ),
    ]);
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
    dependOnReactorLocale(context);
    final String? sub = subtitle;
    final Widget? lead = leading;
    final Widget? act = actions;
    return dom.div(classes: 'reactor-page-header', <Widget>[
      dom.div(classes: 'reactor-page-heading', <Widget>[
        ?lead,
        dom.div(classes: 'reactor-page-heading-copy', <Widget>[
          dom.h1(<Widget>[Component.text(title)]),
          if (sub != null) dom.p(<Widget>[Component.text(sub)]),
        ]),
      ]),
      if (act != null) dom.div(classes: 'reactor-page-actions', <Widget>[act]),
    ]);
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
    this.gap = 0,
    this.flush = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<Widget> body = children ?? <Widget>[?child];
    final bool hasHeader =
        label != null || description != null || trailing != null;
    final bool hasBody = body.isNotEmpty;
    return dom.section(classes: 'reactor-panel', <Widget>[
      if (hasHeader) _header(hasBody),
      if (hasBody)
        dom.div(
          classes: 'reactor-panel-body${flush ? ' is-flush' : ''}',
          styles: dom.Styles(raw: <String, String>{'gap': '${gap}px'}),
          body,
        ),
    ]);
  }

  Widget _header(bool hasBody) {
    final String? lbl = label;
    final String? desc = description;
    final Widget? trail = trailing;
    return dom.div(
      classes: 'reactor-panel-header${hasBody ? ' has-body' : ''}',
      <Widget>[
        dom.div(classes: 'reactor-panel-heading', <Widget>[
          if (lbl != null) reactorEyebrow(lbl),
          if (desc != null)
            dom.div(classes: 'reactor-panel-description', <Widget>[
              Component.text(desc),
            ]),
        ]),
        if (trail != null)
          dom.div(classes: 'reactor-panel-actions', <Widget>[trail]),
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
    dependOnReactorLocale(context);
    final String? val = value;
    final String? u = unit;
    final Widget? bdg = badge;
    return dom.section(
      classes: 'reactor-metric-card reactor-metric-cell is-${status.name}',
      <Widget>[
        dom.div(classes: 'reactor-metric-header', <Widget>[
          dom.div(classes: 'reactor-metric-heading', <Widget>[
            reactorEyebrow(title),
            if (val != null)
              dom.div(classes: 'reactor-metric-reading', <Widget>[
                dom.span(classes: 'reactor-metric-value', <Widget>[
                  Component.text(val),
                ]),
                if (u != null)
                  dom.span(classes: 'reactor-metric-unit', <Widget>[
                    Component.text(u),
                  ]),
              ]),
          ]),
          ?bdg,
        ]),
        dom.div(classes: 'reactor-metric-content', <Widget>[child]),
      ],
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
    dependOnReactorLocale(context);
    final String? u = unit;
    final String? cap = caption;
    return dom.div(classes: 'reactor-stat is-${status.name}', <Widget>[
      reactorEyebrow(label),
      dom.div(classes: 'reactor-stat-reading', <Widget>[
        dom.span(classes: 'reactor-stat-value', <Widget>[
          Component.text(value),
        ]),
        if (u != null)
          dom.span(classes: 'reactor-stat-unit', <Widget>[Component.text(u)]),
      ]),
      if (cap != null)
        dom.div(
          classes: 'reactor-stat-caption',
          styles: dom.Styles(
            raw: <String, String>{
              'color': status == ReactorStatus.neutral
                  ? kReactorMuted
                  : reactorStatusColor(status),
            },
          ),
          <Widget>[Component.text(cap)],
        ),
    ]);
  }
}

class ReactorEmptyState extends StatelessWidget {
  final String title;
  final String description;
  final Widget? action;
  final Widget? icon;

  const ReactorEmptyState({
    required this.title,
    required this.description,
    this.action,
    this.icon,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final Widget? act = action;
    return dom.div(
      classes: 'reactor-pane-state is-empty',
      attributes: const <String, String>{'role': 'status'},
      <Widget>[
        dom.div(classes: 'reactor-pane-state-icon', <Widget>[
          icon ?? ArcaneIcon.minus(size: IconSize.sm),
        ]),
        dom.div(classes: 'reactor-pane-state-copy', <Widget>[
          dom.strong(<Widget>[Component.text(title)]),
          dom.span(<Widget>[Component.text(description)]),
        ]),
        if (act != null)
          dom.div(classes: 'reactor-pane-state-action', <Widget>[act]),
      ],
    );
  }
}

class ReactorNotice extends StatelessWidget {
  final String title;
  final String message;
  final ReactorStatus status;
  final Widget? action;

  const ReactorNotice({
    required this.title,
    required this.message,
    this.status = ReactorStatus.info,
    this.action,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final Widget? act = action;
    return dom.div(
      classes: 'reactor-notice is-${status.name}',
      attributes: <String, String>{
        'role': status == ReactorStatus.critical ? 'alert' : 'status',
      },
      <Widget>[
        reactorStatusDot(status, label: title),
        dom.div(classes: 'reactor-notice-copy', <Widget>[
          dom.strong(<Widget>[Component.text(title)]),
          dom.span(<Widget>[Component.text(message)]),
        ]),
        if (act != null)
          dom.div(classes: 'reactor-notice-action', <Widget>[act]),
      ],
    );
  }
}

class ReactorLoadingState extends StatelessWidget {
  final String? label;

  const ReactorLoadingState({this.label, super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(
      classes: 'reactor-pane-state is-loading',
      attributes: const <String, String>{
        'role': 'status',
        'aria-live': 'polite',
      },
      <Widget>[
        dom.div(classes: 'reactor-pane-state-icon', <Widget>[
          ArcaneIcon.activity(size: IconSize.sm),
        ]),
        dom.div(classes: 'reactor-pane-state-copy', <Widget>[
          dom.strong(<Widget>[
            Component.text(
              label ?? reactorText(ReactorText.loadingWaitingLiveData),
            ),
          ]),
        ]),
      ],
    );
  }
}

class ReactorSparkline extends StatelessWidget {
  final List<double> values;
  final String color;
  final int height;

  const ReactorSparkline({
    required this.values,
    this.color = kReactorMuted,
    this.height = 40,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<double> v = values;
    if (v.length < 2) {
      return dom.div(
        classes: 'reactor-sparkline is-empty',
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
    const double width = 100.0;
    const double chartHeight = 100.0;
    const double pad = 6.0;
    final double step = width / (v.length - 1);
    final StringBuffer line = StringBuffer();
    for (int i = 0; i < v.length; i++) {
      final double x = i * step;
      final double norm = (v[i] - lo) / span;
      final double y = chartHeight - pad - norm * (chartHeight - pad * 2);
      line.write(i == 0 ? 'M ' : 'L ');
      line.write('${x.toStringAsFixed(2)} ${y.toStringAsFixed(2)} ');
    }
    return Component.element(
      tag: 'svg',
      classes: 'reactor-sparkline',
      attributes: <String, String>{
        'width': '100%',
        'height': '$height',
        'viewBox': '0 0 100 100',
        'preserveAspectRatio': 'none',
        'aria-hidden': 'true',
      },
      children: <Component>[
        Component.element(
          tag: 'path',
          attributes: <String, String>{
            'd': line.toString().trim(),
            'fill': 'none',
            'stroke': color,
            'stroke-width': '1.5',
            'stroke-linecap': 'square',
            'stroke-linejoin': 'miter',
            'vector-effect': 'non-scaling-stroke',
          },
        ),
      ],
    );
  }
}
