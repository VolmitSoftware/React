library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import './knob_editor.dart';
import './section_card.dart';

class ConfigSheetBody extends StatelessWidget {
  final ControlItem item;
  final void Function(String key, Object? value)? onKnobChanged;
  final bool disabled;

  const ConfigSheetBody({
    required this.item,
    this.onKnobChanged,
    this.disabled = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Collection(
      gap: 16,
      children: <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'gap': '0.25rem',
            },
          ),
          <Widget>[
            Text.heading2(item.name),
            if (item.description.isNotEmpty)
              dom.p(styles: kSectionHeadingStyles, <Widget>[
                Component.text(item.description),
              ]),
          ],
        ),
        if (item.knobs.isEmpty)
          ArcaneEmptyState.noData(
            title: reactorText(ReactorText.configNoOptions),
            description: reactorText(ReactorText.configNoTunableKnobs),
          )
        else
          for (Knob k in item.knobs)
            KnobEditor(
              knob: k,
              disabled: disabled,
              onChanged: disabled
                  ? null
                  : (Object? v) => onKnobChanged?.call(k.key, v),
            ),
      ],
    );
  }
}

class ConfigSheet extends StatelessWidget {
  final bool isOpen;
  final ControlItem? item;
  final void Function(String key, Object? value)? onKnobChanged;
  final VoidCallback? onClose;
  final bool disabled;

  const ConfigSheet({
    required this.isOpen,
    required this.item,
    this.onKnobChanged,
    this.onClose,
    this.disabled = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return ArcaneSheet.end(
      isOpen: isOpen,
      onClose: onClose,
      title: item?.name,
      child: item == null
          ? Component.fragment(const <Widget>[])
          : ConfigSheetBody(
              item: item!,
              disabled: disabled,
              onKnobChanged: disabled ? null : onKnobChanged,
            ),
    );
  }
}
