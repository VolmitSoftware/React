/// A titled part of a page or card: a heading, its (i) tip and actions, then
/// the content with even spacing. No border and no background of its own.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import 'action_row.dart';
import 'info_tip.dart';

class PageSection extends StatelessWidget {
  const PageSection({
    required this.title,
    required this.children,
    this.tip,
    this.actions,
    super.key,
  });

  /// One to three words.
  final String title;
  final String? tip;

  /// A dense row of the section's own actions, at the heading's end.
  final ActionRow? actions;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) =>
      dom.section(classes: 'ui-section', <Widget>[
        dom.div(classes: 'ui-section-head', <Widget>[
          dom.h2(classes: 'ui-section-title', <Widget>[
            TipLabel(label: Component.text(title), tip: tip),
          ]),
          ?actions,
        ]),
        dom.div(classes: 'ui-section-body', children),
      ]);
}
