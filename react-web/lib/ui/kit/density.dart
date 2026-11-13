/// How tall the controls in a part of the page are. Two sizes, the
/// library's own: regular (36 px: page headers, forms, dialog and form
/// footers) and dense (32 px: the top bar, filter bars, card headers, list
/// rows). A [DensityScope] sets it for everything inside; kit controls read
/// it, so a screen never picks a height.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show InheritedComponent;

enum Density { regular, dense }

class DensityScope extends InheritedComponent {
  const DensityScope({required this.density, required super.child, super.key});

  /// Dense controls for everything inside [child].
  const DensityScope.dense({required super.child, super.key})
    : density = Density.dense;

  final Density density;

  static Density of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<DensityScope>()?.density ??
      Density.regular;

  static bool isDense(BuildContext context) => of(context) == Density.dense;

  @override
  bool updateShouldNotify(DensityScope oldComponent) =>
      oldComponent.density != density;
}

/// A box whose controls are all dense.
class DenseBox extends StatelessWidget {
  const DenseBox({
    required this.children,
    this.classes,
    this.attributes,
    super.key,
  });

  final List<Widget> children;
  final String? classes;
  final Map<String, String>? attributes;

  @override
  Widget build(BuildContext context) => DensityScope.dense(
    child: dom.div(
      classes: classes == null ? 'ui-dense' : 'ui-dense $classes',
      attributes: attributes,
      children,
    ),
  );
}

/// The one icon size inside controls (16 px, shadcn's).
const IconSize controlIconSize = IconSize.sm;

/// A button with text at [density].
ButtonSize buttonSize(Density density) =>
    density == Density.dense ? ButtonSize.sm : ButtonSize.md;

/// A square icon button at [density], as tall as the buttons beside it.
ButtonSize iconButtonSize(Density density) =>
    density == Density.dense ? ButtonSize.iconSm : ButtonSize.iconMd;

/// Inputs, selects and toggle groups at [density].
ComponentSize controlSize(Density density) =>
    density == Density.dense ? ComponentSize.sm : ComponentSize.md;
