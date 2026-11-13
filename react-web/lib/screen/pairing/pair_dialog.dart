/// Pair a server: the dialog the server switcher, the server group and
/// Settings > Servers open. It holds the Pairing link field and the command
/// to run; a pasted link turns into the pairing card.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show InheritedComponent;
import 'package:jaspr_router/jaspr_router.dart';

import '../../app/paths.dart';
import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../ui/kit/browser.dart';
import '../../ui/kit/dialog.dart';
import 'pair_link_form.dart';
import 'pairing_card.dart';

/// Opens the shell's Pair a server dialog from anywhere below the shell,
/// empty or with a link already pasted elsewhere (a New link field).
typedef PairDialogOpener = void Function([PairingLink? link]);

class PairDialogScope extends InheritedComponent {
  const PairDialogScope({required this.open, required super.child, super.key});

  final PairDialogOpener open;

  /// The opener, or null outside the shell (tests that render one screen).
  static PairDialogOpener? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<PairDialogScope>()?.open;

  @override
  bool updateShouldNotify(PairDialogScope oldComponent) => false;
}

class PairDialog extends StatefulWidget {
  const PairDialog({
    required this.open,
    required this.onClose,
    this.link,
    super.key,
  });

  final bool open;
  final void Function() onClose;

  /// A link pasted before the dialog opened.
  final PairingLink? link;

  @override
  State<PairDialog> createState() => _PairDialogState();
}

class _PairDialogState extends State<PairDialog> {
  late PairingLink? _link = component.link;

  @override
  void didUpdateWidget(PairDialog oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (component.open && !oldComponent.open) _link = component.link;
    if (!component.open) _link = null;
  }

  void _paired(String id) {
    final RouterState? router = Router.maybeOf(context);
    component.onClose();
    afterFrame(() => router?.push(serverPath(id)));
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final PairingLink? link = _link;
    return AppDialog(
      id: 'ui-pair-dialog',
      open: component.open,
      title: reactorText(ReactorText.pairTitle),
      onClose: component.onClose,
      children: <Widget>[
        if (component.open && link == null) ...<Widget>[
          PairLinkForm(
            id: 'pair-dialog-link',
            onLink: (PairingLink next) => setState(() => _link = next),
          ),
          const PairCommandField(),
        ] else if (component.open && link != null)
          PairingCard(
            key: ValueKey<String>(link.code),
            link: link,
            framed: false,
            onCancel: () => setState(() => _link = null),
            onPaired: _paired,
          ),
      ],
    );
  }
}
