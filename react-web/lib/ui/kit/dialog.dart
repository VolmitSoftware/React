/// A modal window on shadcn's dialog: Escape and the scrim close it, focus
/// moves in and goes back to the opener. Its body is a surface of its own:
/// kit parts inside draw flat.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import 'action_row.dart';
import 'modal_focus.dart';
import 'surface.dart';

int _nextAppDialogId = 0;

class AppDialog extends StatefulWidget {
  const AppDialog({
    required this.open,
    required this.title,
    required this.onClose,
    required this.children,
    this.footer,
    this.wide = false,
    this.alert = false,
    this.id,
    super.key,
  });

  final bool open;
  final String title;
  final void Function() onClose;
  final List<Widget> children;

  /// The buttons under the content: Cancel, then the primary action.
  final DialogFooter? footer;

  /// 720 px instead of 480 px, for lists and tables.
  final bool wide;

  /// A question that needs an answer: an alert dialog to assistive
  /// technology.
  final bool alert;
  final String? id;

  @override
  State<AppDialog> createState() => _AppDialogState();
}

class _AppDialogState extends State<AppDialog> with ModalFocus<AppDialog> {
  late final String _id = component.id ?? 'ui-dialog-${_nextAppDialogId++}';

  @override
  String get modalId => _id;

  @override
  bool get modalOpen => component.open;

  @override
  void modalClose() => component.onClose();

  @override
  void didUpdateWidget(AppDialog oldComponent) {
    super.didUpdateWidget(oldComponent);
    followOpen(oldComponent.open);
  }

  @override
  Widget build(BuildContext context) => dom.div(
    id: _id,
    classes: 'ui-dialog',
    <Widget>[
      ArcaneDialog(
        isOpen: component.open,
        title: component.title,
        onClose: component.onClose,
        maxWidth: component.wide ? 720 : 480,
        alert: component.alert,
        actions: component.footer == null ? null : <Widget>[component.footer!],
        children: <Widget>[
          SurfaceScope(child: dom.div(classes: 'ui-stack', component.children)),
        ],
      ),
    ],
  );
}
