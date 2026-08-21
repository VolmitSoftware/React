library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../ui/reactor_ui.dart';

const dom.Styles kSectionHeadingStyles = dom.Styles(
  raw: <String, String>{
    'font-size': '0.64rem',
    'font-weight': '650',
    'color': 'var(--muted-foreground)',
    'text-transform': 'uppercase',
    'letter-spacing': '0.085em',
  },
);

Widget statGrid(List<Widget> tiles) => reactorGrid(children: tiles);

Widget sectionCard({
  required String label,
  required Widget child,
  String? description,
  Widget? trailing,
  bool flush = false,
}) {
  return SectionPanel(
    label: label,
    description: description,
    trailing: trailing,
    flush: flush,
    child: child,
  );
}
