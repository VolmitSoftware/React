/// The field a pairing link is pasted into, with Pair: on the start page,
/// in the Pair a server dialog, on a card whose link is spent, and wherever
/// a pairing ended. A pasted link is taken at once; Enter or Pair takes
/// what was typed.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../service/clipboard.dart';
import '../../ui/kit/action_row.dart';
import '../../ui/kit/density.dart';
import '../../ui/kit/info_tip.dart';
import 'pair_words.dart';

class PairLinkForm extends StatefulWidget {
  const PairLinkForm({
    required this.onLink,
    this.id = 'pair-link',
    this.label,
    super.key,
  });

  /// Called with the link once the field holds one.
  final void Function(PairingLink link) onLink;

  /// The text box's id; the label names it.
  final String id;

  /// "Pairing link" when null; "New link" where a link was spent.
  final String? label;

  @override
  State<PairLinkForm> createState() => _PairLinkFormState();
}

class _PairLinkFormState extends State<PairLinkForm> {
  String _text = '';
  String? _error;

  /// Hands [link] on and empties the field, so the code does not linger
  /// on the page after the card took it.
  void _take(PairingLink link) {
    setState(() {
      _text = '';
      _error = null;
    });
    component.onLink(link);
  }

  void _changed(String text) {
    _text = text;
    final PairingLink? link = PairingLink.parse(text);
    if (link != null) {
      _take(link);
    } else if (_error != null) {
      setState(() => _error = null);
    }
  }

  void _submit([String? submitted]) {
    final String text = submitted ?? _text;
    final PairingLink? link = PairingLink.parse(text);
    if (link != null) {
      _take(link);
      return;
    }
    setState(() {
      _text = text;
      _error = text.trim().isEmpty
          ? reactorText(ReactorText.pairPasteLink)
          : reactorText(ReactorText.pairProblemNotALink);
    });
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final String label = component.label ?? reactorText(ReactorText.pairLink);
    return ArcaneFormField(
      controlId: component.id,
      invalid: _error != null,
      children: <Widget>[
        ArcaneFieldLabel(label),
        ActionRow(
          label: label,
          leading: <Widget>[
            TextInput(
              value: _text,
              placeholder: 'https://…/pair#RCT3.…',
              size: controlSize(DensityScope.of(context)),
              fullWidth: true,
              attributes: const <String, String>{
                'dir': 'ltr',
                'autocomplete': 'off',
                'spellcheck': 'false',
              },
              onChanged: _changed,
              onSubmitted: _submit,
            ),
          ],
          primary: RowAction(
            reactorText(ReactorText.pairPair),
            _submit,
            attributes: <String, String>{'data-testid': '${component.id}-pair'},
          ),
        ),
        ArcaneFieldError(_error),
      ],
    );
  }
}

/// The command to run on the server, read-only, with Copy.
class PairCommandField extends StatelessWidget {
  const PairCommandField({super.key});

  static const String fieldId = 'pair-command';

  Future<void> _copy() async {
    if (await writeClipboardText(pairCommand)) {
      ArcaneSonner.success(reactorText(ReactorText.pairCopied));
    } else {
      ArcaneSonner.error(
        reactorText(ReactorText.pairCopyFailed),
        description: pairCommand,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final String label = reactorText(ReactorText.pairCommand);
    return ArcaneFormField(
      controlId: fieldId,
      children: <Widget>[
        ArcaneFieldLabel(
          label,
          trailing: <Widget>[
            InfoTip(tip: reactorText(ReactorText.pairCommandTip)),
          ],
        ),
        ActionRow(
          label: label,
          leading: <Widget>[
            TextInput(
              value: pairCommand,
              readOnly: true,
              size: controlSize(DensityScope.of(context)),
              fullWidth: true,
              attributes: const <String, String>{'dir': 'ltr'},
              styles: const ArcaneStyleData(fontFamily: FontFamily.mono),
            ),
          ],
          secondary: <RowAction>[
            RowAction(
              reactorText(ReactorText.pairCopyCommand),
              _copy,
              icon: ArcaneIcon.copy(),
              iconOnly: true,
            ),
          ],
        ),
      ],
    );
  }
}
