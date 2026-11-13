/// One setting of a feature, tweak or action as a library field: a switch
/// beside its label, or a number or text box or a select under it, with its
/// documentation behind an (i). Numbers and text are committed on Enter or
/// when the box loses focus, never per keystroke.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/density.dart';
import '../ui/kit/info_tip.dart';

class KnobEditor extends StatefulWidget {
  final Knob knob;
  final void Function(Object? value)? onChanged;
  final bool disabled;

  /// The server changed this setting while it held an unsaved edit here.
  final bool changedOnServer;

  const KnobEditor({
    required this.knob,
    this.onChanged,
    this.disabled = false,
    this.changedOnServer = false,
    super.key,
  });

  @override
  State<KnobEditor> createState() => _KnobEditorState();
}

class _KnobEditorState extends State<KnobEditor> {
  /// What the box holds while it is being edited; null shows the knob.
  String? _draft;

  Knob get knob => component.knob;

  String get _id =>
      'knob-${knob.key.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '-')}';

  String get _shown => switch (knob.type) {
    KnobType.intType => knob.intValue.toString(),
    KnobType.doubleType => knob.doubleValue.toString(),
    _ => knob.stringValue,
  };

  @override
  void didUpdateWidget(KnobEditor oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (oldComponent.knob.key != component.knob.key) _draft = null;
  }

  /// Sends the draft when it parses and differs from the knob's value.
  void _commit([String? text]) {
    final String? draft = text ?? _draft;
    _draft = null;
    if (draft == null || draft.trim() == _shown) return;
    final String value = draft.trim();
    final Object? parsed = switch (knob.type) {
      KnobType.intType => int.tryParse(value),
      KnobType.doubleType => double.tryParse(value),
      _ => draft,
    };
    if (parsed != null) component.onChanged?.call(parsed);
  }

  Widget _box(TextInputType type, ComponentSize size) => TextInput(
    type: type,
    size: size,
    value: _draft ?? _shown,
    onChanged: component.disabled ? null : (String s) => _draft = s,
    onBlur: component.disabled ? null : _commit,
    onSubmitted: component.disabled ? null : _commit,
  );

  @override
  Widget build(BuildContext context) {
    final bool disabled = component.disabled;
    final ComponentSize size = controlSize(DensityScope.of(context));
    final String doc = knob.doc;
    final Widget label = ArcaneFieldLabel(
      knob.label,
      trailing: <Widget>[if (doc.isNotEmpty) InfoTip(tip: doc)],
    );
    final Widget control = switch (knob.type) {
      KnobType.boolType => ArcaneToggleSwitch(
        value: knob.boolValue,
        size: size,
        onChanged: disabled ? null : (bool b) => component.onChanged?.call(b),
      ),
      KnobType.intType ||
      KnobType.doubleType => _box(TextInputType.number, size),
      KnobType.enumType => ArcaneSelect(
        size: size,
        value: knob.stringValue,
        fullWidth: true,
        options: <ArcaneSelectOption>[
          for (final String o in knob.options)
            ArcaneSelectOption(label: o, value: o),
        ],
        onChanged: disabled ? null : (String s) => component.onChanged?.call(s),
      ),
      _ => _box(TextInputType.text, size),
    };
    return ArcaneFormField(
      controlId: _id,
      orientation: knob.type == KnobType.boolType
          ? FieldOrientation.horizontal
          : FieldOrientation.vertical,
      disabled: disabled,
      children: <Widget>[
        label,
        control,
        if (component.changedOnServer)
          AppAlert(
            tone: AlertTone.warning,
            message: reactorText(ReactorText.optimizationChangedOnServer),
          ),
      ],
    );
  }
}
