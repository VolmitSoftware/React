library;

import 'dart:io';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/jaspr_test.dart';

import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
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

  group('React Web accent', () {
    const ReactorStylesheet sheet = ReactorStylesheet();
    const int reactBlue = 0xFF38BDF8;

    test('primary is React blue in both themes', () {
      expect(sheet.lightSeed.primary, reactBlue);
      expect(sheet.darkSeed.primary, reactBlue);
    });

    test('text on primary meets 4.5:1 in both themes', () {
      for (final ThemeSeed seed in <ThemeSeed>[
        sheet.lightSeed,
        sheet.darkSeed,
      ]) {
        final ThemePalette palette = PaletteGenerator.generate(seed);
        expect(
          PaletteGenerator.contrastRatio(
            palette.primary,
            palette.primaryForeground,
          ),
          greaterThanOrEqualTo(4.5),
        );
      }
    });

    test('the ring is React blue in both themes, after the library ring', () {
      final String css = sheet.componentCss;
      final RegExp ring = RegExp(r'--ring:\s*([^;]+);');
      final List<String> rings = <String>[
        for (final RegExpMatch match in ring.allMatches(css)) match[1]!.trim(),
      ];
      // Light, then dark: the last two declarations win the cascade.
      expect(rings.length, greaterThanOrEqualTo(2));
      expect(rings.sublist(rings.length - 2), <String>['#38bdf8', '#38bdf8']);
      final String tail = css.substring(css.lastIndexOf('#38bdf8') - 200);
      expect(tail, contains('html.dark #arcane-root.arcane-theme-shadcn'));
    });

    test('light surfaces stay neutral', () {
      expect(sheet.lightSeed.secondary, 0xFFF5F5F5);
      expect(sheet.lightSeed.accent, 0xFFF5F5F5);
      expect(sheet.lightSeed.border, 0xFFE5E5E5);
    });
  });

  group('React Web theme assets', () {
    test('pre-paint script allowlists the saved theme', () {
      final String bootstrap = File('web/bootstrap.js').readAsStringSync();
      expect(bootstrap, contains("localStorage.getItem('reactor.theme')"));
      expect(bootstrap, contains("stored === 'light' || stored === 'dark'"));
    });
  });
}
