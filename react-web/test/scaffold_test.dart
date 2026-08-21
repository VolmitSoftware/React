library;

import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/jaspr_test.dart';

void main() {
  group('scaffold', () {
    test('ShadcnTheme.midnight has correct dark background', () {
      const ShadcnTheme theme = ShadcnTheme.midnight;
      expect(theme.darkBackground, equals(0xFF050505));
    });

    test('ShadcnStylesheet id is shadcn', () {
      const ShadcnStylesheet stylesheet = ShadcnStylesheet();
      expect(stylesheet.id, equals('shadcn'));
    });

    test('reactor app uses the midnight ShadcnStylesheet', () {
      const ShadcnStylesheet stylesheet = ShadcnStylesheet(
        theme: ShadcnTheme.midnight,
      );
      expect(stylesheet.theme, equals(ShadcnTheme.midnight));
    });
  });
}
