/// The one bordered surface: shadcn's card with an optional heading row.
/// Everything inside draws flat ([SurfaceScope]); split the inside with
/// headings, spacing and separators, never with another box.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import 'action_row.dart';
import 'info_tip.dart';
import 'surface.dart';

class AppCard extends StatelessWidget {
  const AppCard({
    required this.children,
    this.title,
    this.tip,
    this.actions,
    this.attributes = const <String, String>{},
    super.key,
  });

  /// The heading, one to three words.
  final String? title;
  final String? tip;

  /// A dense row at the heading's end (filters, a range, one action).
  final ActionRow? actions;
  final List<Widget> children;

  /// Extra attributes on the card (`data-*`).
  final Map<String, String> attributes;

  @override
  Widget build(BuildContext context) => Card(
    classes: 'ui-card ui-stack',
    attributes: attributes,
    fillWidth: true,
    children: <Widget>[
      if (title != null || actions != null)
        dom.div(classes: 'ui-card-head', <Widget>[
          if (title != null)
            dom.h2(classes: 'ui-card-title', <Widget>[
              TipLabel(label: Component.text(title!), tip: tip),
            ]),
          ?actions,
        ]),
      if (children.isNotEmpty)
        SurfaceScope(child: dom.div(classes: 'ui-card-body', children)),
    ],
  );
}

/// A chart in its frame: the card is the frame. The chart inside scales to
/// the card's width and never scrolls.
class ChartCard extends StatelessWidget {
  const ChartCard({
    required this.title,
    required this.child,
    this.tip,
    this.actions,
    super.key,
  });

  final String title;
  final String? tip;

  /// A dense row at the heading's end (a range switch, series toggles).
  final ActionRow? actions;

  /// The chart (a `TimeseriesChart` or another `role="img"` drawing).
  final Widget child;

  @override
  Widget build(BuildContext context) => AppCard(
    title: title,
    tip: tip,
    actions: actions,
    attributes: const <String, String>{'data-chart-card': ''},
    children: <Widget>[child],
  );
}
