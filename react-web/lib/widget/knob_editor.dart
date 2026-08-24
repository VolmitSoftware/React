library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/knob.dart';

class KnobEditor extends StatelessWidget {
  final Knob knob;
  final void Function(Object? value)? onChanged;
  final bool disabled;

  const KnobEditor({
    required this.knob,
    this.onChanged,
    this.disabled = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final Widget input = switch (knob.type) {
      KnobType.boolType => ArcaneToggleSwitch(
        value: knob.boolValue,
        label: knob.label,
        disabled: disabled,
        onChanged: disabled ? null : (bool b) => onChanged?.call(b),
      ),
      KnobType.intType => TextInput(
        label: knob.label,
        type: TextInputType.number,
        value: knob.intValue.toString(),
        disabled: disabled,
        onChange: disabled
            ? null
            : (String s) {
                final int? v = int.tryParse(s.trim());
                if (v != null) onChanged?.call(v);
              },
      ),
      KnobType.doubleType => TextInput(
        label: knob.label,
        type: TextInputType.number,
        value: knob.doubleValue.toString(),
        disabled: disabled,
        onChange: disabled
            ? null
            : (String s) {
                final double? v = double.tryParse(s.trim());
                if (v != null) onChanged?.call(v);
              },
      ),
      KnobType.stringType => TextInput(
        label: knob.label,
        type: TextInputType.text,
        value: knob.stringValue,
        disabled: disabled,
        onChange: disabled ? null : (String s) => onChanged?.call(s),
      ),
      KnobType.enumType => ArcaneSelect(
        size: ComponentSize.sm,
        label: knob.label,
        value: knob.stringValue,
        disabled: disabled,
        options: <ArcaneSelectOption>[
          for (final String o in knob.options)
            ArcaneSelectOption(label: o, value: o),
        ],
        onChange: disabled ? null : (String s) => onChanged?.call(s),
      ),
    };

    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.25rem',
        },
      ),
      [
        input,
        if (knob.doc.isNotEmpty)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'font-size': '0.75rem',
                'color': 'var(--muted-foreground)',
              },
            ),
            [Component.text(knob.doc)],
          ),
      ],
    );
  }
}
