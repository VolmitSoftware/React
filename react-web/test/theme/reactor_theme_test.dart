library;

import 'dart:io';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/jaspr_test.dart';

import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/theme/reactor_theme.dart';

class _UnavailableStorage implements FleetStorage {
  @override
  String? read(String key) => throw StateError('Storage unavailable');

  @override
  void remove(String key) => throw StateError('Storage unavailable');

  @override
  void write(String key, String value) =>
      throw StateError('Storage unavailable');
}

void main() {
  group('React Web theme preference', () {
    test('defaults to dark without a saved preference', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      expect(loadReactorBrightness(storage), Brightness.dark);
    });

    test('round-trips light and dark preferences', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();

      persistReactorBrightness(storage, Brightness.light);
      expect(storage.read(reactorThemeStorageKey), 'light');
      expect(loadReactorBrightness(storage), Brightness.light);

      persistReactorBrightness(storage, Brightness.dark);
      expect(storage.read(reactorThemeStorageKey), 'dark');
      expect(loadReactorBrightness(storage), Brightness.dark);
    });

    test('ignores unknown or unavailable storage values', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write(reactorThemeStorageKey, 'sepia');

      expect(loadReactorBrightness(storage), Brightness.dark);
      expect(loadReactorBrightness(_UnavailableStorage()), Brightness.dark);
      expect(
        () => persistReactorBrightness(_UnavailableStorage(), Brightness.light),
        returnsNormally,
      );
    });
  });

  group('React Web theme assets', () {
    test('pre-paint script allowlists the saved theme', () {
      final String html = File('web/index.html').readAsStringSync();
      expect(html, contains("localStorage.getItem('reactor.theme')"));
      expect(html, contains("stored === 'light' || stored === 'dark'"));
    });

    test('ships separate accessible light and dark palettes', () {
      final String css = File('web/styles/react-web.css').readAsStringSync();
      expect(css, contains('html.dark #arcane-root'));
      expect(css, contains('html.light #arcane-root'));
      expect(css, contains('--reactor-danger: #ff8a8d;'));
      expect(css, contains('--destructive-foreground: #000000 !important;'));
      expect(css, contains('--reactor-danger: #b4232c;'));
      expect(css, contains('--destructive-foreground: #ffffff !important;'));
    });
  });
}
