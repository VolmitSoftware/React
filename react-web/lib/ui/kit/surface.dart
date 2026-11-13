/// Marks the inside of a card. A page has one level of bordered surface, so
/// kit parts that draw a box of their own (an alert, a list, a metric tile)
/// draw flat inside a card instead.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show InheritedComponent;

class SurfaceScope extends InheritedComponent {
  const SurfaceScope({required super.child, super.key});

  /// Whether [context] is inside a card.
  static bool inCard(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<SurfaceScope>() != null;

  @override
  bool updateShouldNotify(SurfaceScope oldComponent) => false;
}
