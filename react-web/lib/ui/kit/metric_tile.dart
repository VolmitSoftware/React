/// One number with its label, unit and an optional trend line or meter. On
/// the page it is shadcn's stat card and sits in a [MetricGrid]; inside a
/// card it draws flat, so it is never a card in a card.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../../chart/chart_scale.dart';
import '../../chart/sparkline.dart';
import '../../model/units.dart';
import 'badge.dart';
import 'surface.dart';

class MetricTile extends StatelessWidget {
  const MetricTile({
    required this.label,
    required this.value,
    this.unit,
    this.caption,
    this.note,
    this.tone = BadgeTone.neutral,
    this.history = const <double>[],
    this.meter,
    super.key,
  });

  /// One to three words ("Tick time").
  final String label;

  /// The reading as shown ("19.9", "--" while unknown).
  final String value;
  final String? unit;

  /// A short line under the value ("of 16 GB"), or, when [tone] colours
  /// the tile, the state in words ("Critical"), shown as a badge in that
  /// tone.
  final String? caption;

  /// A muted line under the value whatever the state ("/srv · of 2 TB").
  final String? note;

  /// Colours the state badge, and the trend line and meter once the value
  /// is past a warning (a healthy value draws them in the neutral colour).
  final BadgeTone tone;

  /// Recent values, oldest first; a trend line from two values on.
  final List<double> history;

  /// A fill from 0 to 1 (memory used of max); drawn as a progress bar.
  final double? meter;

  String get _color => switch (tone) {
    BadgeTone.warning => 'var(--warning)',
    BadgeTone.error => 'var(--destructive)',
    BadgeTone.ok ||
    BadgeTone.info ||
    BadgeTone.neutral => 'var(--muted-foreground)',
  };

  Widget? _footer() {
    final double? fill = meter;
    if (fill != null) {
      final double clamped = fill.clamp(0.0, 1.0);
      return dom.div(
        classes: 'ui-meter',
        attributes: <String, String>{
          'role': 'meter',
          'aria-label': label,
          'aria-valuemin': '0',
          'aria-valuemax': '100',
          'aria-valuenow': (clamped * 100).toStringAsFixed(0),
          'aria-valuetext': value,
        },
        <Widget>[
          ArcaneProgressBar(
            value: clamped,
            size: ComponentSize.sm,
            variant: switch (tone) {
              BadgeTone.warning => ProgressVariant.warning,
              BadgeTone.error => ProgressVariant.error,
              BadgeTone.ok ||
              BadgeTone.info ||
              BadgeTone.neutral => ProgressVariant.primary,
            },
          ),
        ],
      );
    }
    // A flat history says nothing the number does not.
    if (!isFlat(history)) return Sparkline(values: history, color: _color);
    return null;
  }

  /// The caption as a state badge when the tile has a tone.
  bool get _stateCaption =>
      caption != null && tone != BadgeTone.neutral && tone != BadgeTone.info;

  @override
  Widget build(BuildContext context) {
    final String value = withoutNegativeZero(this.value);
    // "Handles/tick" over "/t" reads "Handles 52 /tick".
    final String label = labelWithoutUnit(this.label, unit);
    final String? shownUnit = value == '--' ? null : unitBeside(label, unit);
    final String shown = withUnit(value, shownUnit);
    final Widget? trend = _footer();
    final Widget? badge = _stateCaption
        ? StatusBadge(caption!, tone: tone)
        : null;
    final Widget? footer = badge == null
        ? trend
        : trend == null
        ? badge
        : dom.div(classes: 'ui-metric-foot', <Widget>[badge, trend]);
    if (SurfaceScope.inCard(context)) {
      return dom.div(
        classes: 'ui-stat',
        attributes: <String, String>{'data-tone': tone.name},
        <Widget>[
          dom.span(classes: 'ui-stat-label', <Widget>[Component.text(label)]),
          dom.span(classes: 'ui-stat-value', <Widget>[Component.text(shown)]),
          if ((badge == null ? (caption ?? note) : note) case final String line)
            dom.span(classes: 'ui-stat-caption', <Widget>[
              Component.text(line),
            ]),
          ?footer,
        ],
      );
    }
    return dom.div(
      classes: 'ui-metric',
      attributes: <String, String>{'data-tone': tone.name},
      <Widget>[
        StatCard(
          label: label,
          value: value,
          unit: shownUnit,
          description: badge == null ? (caption ?? note) : note,
          footer: footer,
          labelLines: 3,
        ),
      ],
    );
  }
}

/// A row of [MetricTile]s: four columns on wide pages, two below that, so
/// tiles are the same width on every page; three tiles take three columns
/// rather than leave a fourth empty.
class MetricGrid extends StatelessWidget {
  const MetricGrid({required this.children, super.key});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => dom.div(
    classes: 'ui-metric-grid',
    attributes: <String, String>{'data-tiles': '${children.length}'},
    children,
  );
}
