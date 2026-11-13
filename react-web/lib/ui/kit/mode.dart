/// Simple and Advanced. Simple shows what an admin checks every day;
/// Advanced adds the expert views (Metrics, internals, raw sections). One
/// switch for the whole app, kept per browser. It only hides surfaces: role
/// gating still decides what may be changed, and Simple never drops data it
/// does not show.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component, InheritedComponent;

import '../../state/fleet_manager.dart';

enum ReactorMode { simple, advanced }

/// The storage key the mode is kept under.
const String reactorModeStorageKey = 'reactor.mode';

/// A stored value as a mode; anything unknown (or nothing) is Simple.
ReactorMode parseReactorMode(String? stored) =>
    stored == ReactorMode.advanced.name
    ? ReactorMode.advanced
    : ReactorMode.simple;

/// The mode kept in [storage], Simple when none is (or storage is off).
ReactorMode loadReactorMode(FleetStorage? storage) {
  if (storage == null) return ReactorMode.simple;
  try {
    return parseReactorMode(storage.read(reactorModeStorageKey));
  } on Object {
    return ReactorMode.simple;
  }
}

/// Keeps [mode] for the next visit; blocked storage keeps it for this page.
void persistReactorMode(FleetStorage? storage, ReactorMode mode) {
  if (storage == null) return;
  try {
    storage.write(reactorModeStorageKey, mode.name);
  } on Object {
    // Private mode or storage switched off.
  }
}

/// Hands the current mode down the tree. The shell owns it.
class ModeScope extends InheritedComponent {
  const ModeScope({
    required this.mode,
    required this.onChanged,
    required super.child,
    super.key,
  });

  final ReactorMode mode;

  /// Switches the whole app (Settings and the top bar call it).
  final void Function(ReactorMode mode) onChanged;

  static ModeScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ModeScope>();

  /// The current mode; Simple outside a scope.
  static ReactorMode of(BuildContext context) =>
      maybeOf(context)?.mode ?? ReactorMode.simple;

  static bool advanced(BuildContext context) =>
      of(context) == ReactorMode.advanced;

  @override
  bool updateShouldNotify(ModeScope oldComponent) => oldComponent.mode != mode;
}

/// [child] in Advanced, [simple] (or nothing) in Simple.
class ModeGate extends StatelessWidget {
  const ModeGate({required this.child, this.simple, super.key});

  final Widget child;
  final Widget? simple;

  @override
  Widget build(BuildContext context) => ModeScope.advanced(context)
      ? child
      : (simple ?? const Component.fragment(<Widget>[]));
}
