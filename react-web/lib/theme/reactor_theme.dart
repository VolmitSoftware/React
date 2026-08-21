library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../state/fleet_manager.dart';

const String reactorThemeStorageKey = 'reactor.theme';

Brightness loadReactorBrightness(FleetStorage? storage) {
  if (storage == null) return Brightness.dark;
  try {
    final String? stored = storage.read(reactorThemeStorageKey);
    if (stored == 'light') return Brightness.light;
    if (stored == 'dark') return Brightness.dark;
  } catch (_) {}
  return Brightness.dark;
}

void persistReactorBrightness(FleetStorage? storage, Brightness brightness) {
  if (storage == null) return;
  try {
    storage.write(
      reactorThemeStorageKey,
      brightness == Brightness.light ? 'light' : 'dark',
    );
  } catch (_) {}
}

class ReactorThemeScope extends InheritedWidget {
  final Brightness brightness;
  final ValueChanged<Brightness> onChanged;

  const ReactorThemeScope({
    required this.brightness,
    required this.onChanged,
    required super.child,
    super.key,
  });

  static ReactorThemeScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ReactorThemeScope>();

  static ReactorThemeScope of(BuildContext context) {
    final ReactorThemeScope? scope = maybeOf(context);
    if (scope == null) {
      throw StateError('No ReactorThemeScope found in this context.');
    }
    return scope;
  }

  bool get isDark => brightness == Brightness.dark;

  void toggle() => onChanged(
    brightness == Brightness.dark ? Brightness.light : Brightness.dark,
  );

  @override
  bool updateShouldNotify(ReactorThemeScope oldComponent) =>
      brightness != oldComponent.brightness ||
      onChanged != oldComponent.onChanged;
}
