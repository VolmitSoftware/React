/// A button that shows only an icon. Its name is mandatory: it is the
/// button's aria-label and the tooltip a pointer or focus shows. Square at
/// the height of the row it sits in (the surrounding [DensityScope]), with
/// the icon at 16 px, and outlined like the buttons beside it; [ghost] only
/// where it stands alone (the top bar).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import 'density.dart';

class AppIconButton extends StatelessWidget {
  const AppIconButton({
    required this.icon,
    required this.label,
    required this.onPressed,
    this.disabled = false,
    this.pressed,
    this.expanded,
    this.id,
    this.attributes = const <String, String>{},
    this.tipAtEnd = false,
    this.tipAtStart = false,
    this.ghost = false,
    super.key,
  });

  final ArcaneGlyph icon;

  /// What it does, in a few words ("Reset view").
  final String label;
  final void Function()? onPressed;
  final bool disabled;

  /// For a toggle: `aria-pressed`.
  final bool? pressed;

  /// For a disclosure or menu trigger: `aria-expanded`.
  final bool? expanded;
  final String? id;

  final Map<String, String> attributes;

  /// Opens the tooltip toward the start, for buttons at the end of a row.
  final bool tipAtEnd;

  /// Opens the tooltip toward the end, for a button at the window's edge
  /// (the phone menu button).
  final bool tipAtStart;

  /// No border: a lone button with no bordered neighbour (the top bar).
  final bool ghost;

  @override
  Widget build(BuildContext context) => ArcaneTooltip(
    text: label,
    position: tipAtEnd
        ? FloatingPosition.bottomEnd
        : tipAtStart
        ? FloatingPosition.bottomStart
        : FloatingPosition.bottom,
    child: Button(
      id: id,
      variant: ghost ? ButtonVariant.ghost : ButtonVariant.outline,
      icon: icon.sized(controlIconSize),
      size: iconButtonSize(DensityScope.of(context)),
      disabled: disabled || onPressed == null,
      attributes: <String, String>{
        ...attributes,
        'aria-label': label,
        if (pressed != null) 'aria-pressed': '$pressed',
        if (expanded != null) 'aria-expanded': '$expanded',
      },
      onPressed: onPressed,
    ),
  );
}
