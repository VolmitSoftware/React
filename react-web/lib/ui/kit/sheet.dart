/// A side sheet on shadcn's sheet: a settings panel from the right, the nav
/// drawer from the left. Full screen at 760 px and below.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import 'action_row.dart';
import 'modal_focus.dart';
import 'info_tip.dart';
import 'surface.dart';

int _nextSheetId = 0;

class Sheet extends StatefulWidget {
  const Sheet({
    required this.open,
    required this.title,
    required this.onClose,
    required this.child,
    this.tip,
    this.footer,
    this.fromStart = false,
    this.id,
    super.key,
  });

  final bool open;
  final String title;
  final void Function() onClose;
  final Widget child;

  /// A longer explanation, behind an (i) on the title's line.
  final String? tip;

  /// The buttons at the bottom of the sheet, under a rule so a body that
  /// scrolls under them reads as scrolled, not cut.
  final DialogFooter? footer;

  /// From the left (the nav drawer) instead of the right.
  final bool fromStart;
  final String? id;

  @override
  State<Sheet> createState() => _SheetState();
}

class _SheetState extends State<Sheet> with ModalFocus<Sheet> {
  late final String _id = component.id ?? 'ui-sheet-${_nextSheetId++}';

  @override
  String get modalId => _id;

  @override
  bool get modalOpen => component.open;

  @override
  void modalClose() => component.onClose();

  @override
  void didUpdateWidget(Sheet oldComponent) {
    super.didUpdateWidget(oldComponent);
    followOpen(oldComponent.open);
  }

  @override
  Widget build(BuildContext context) => dom.div(
    id: _id,
    classes: 'ui-sheet${component.fromStart ? ' is-start' : ''}',
    <Widget>[
      ArcaneSheet(
        isOpen: component.open,
        position: component.fromStart ? SheetPosition.start : SheetPosition.end,
        size: SheetSize.md,
        title: component.title,
        titleTrailing: component.tip == null
            ? null
            : InfoTip(tip: component.tip!),
        onClose: component.onClose,
        footer: component.footer == null
            ? null
            : dom.div(classes: 'ui-stack', <Widget>[
                const ArcaneSeparator(),
                component.footer!,
              ]),
        showDragHandle: false,
        child: SurfaceScope(
          child: dom.div(classes: 'ui-stack', <Widget>[component.child]),
        ),
      ),
    ],
  );
}
