library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../ui/reactor_ui.dart';

const dom.Styles kSectionHeadingStyles = dom.Styles(
  raw: <String, String>{
    'font-size': '0.6875rem',
    'font-weight': '600',
    'color': 'var(--muted-foreground)',
    'text-transform': 'uppercase',
    'letter-spacing': '0',
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
