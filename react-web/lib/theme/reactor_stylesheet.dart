/// shadcn Midnight as shipped, with React blue (the React colour of the docs
/// theme) as primary and ring and Geist as the font. `ShadcnTheme` is an
/// enum, so the blue goes in through the seeds, and the ring after the
/// library's own CSS (Midnight sets its own ring on the root). Text on the
/// blue is black in both themes (9.8:1). Keep it const: the base CSS is
/// cached per instance.
library;

import 'package:arcane_jaspr/theme/index.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';

class ReactorStylesheet extends ShadcnStylesheet {
  const ReactorStylesheet() : super(theme: ShadcnTheme.midnight);

  static const int reactBlue = 0xFF38BDF8;

  static const String _reactBlueCss = '#38bdf8';

  @override
  ThemeSeed get lightSeed => super.lightSeed.copyWith(
    primary: reactBlue,
    // Neutral seeds stop the palette tinting secondary, accent and border.
    secondary: 0xFFF5F5F5,
    accent: 0xFFF5F5F5,
    border: 0xFFE5E5E5,
  );

  @override
  ThemeSeed get darkSeed => super.darkSeed.copyWith(primary: reactBlue);

  @override
  FontConfig get fonts => const FontConfig(
    sans: "'Geist', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
    heading: "'Geist', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
    mono: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
  );

  @override
  String get fontFaces => '''
@font-face { font-family: 'Geist'; src: url('/assets/fonts/geist/Geist-Regular.woff2') format('woff2'); font-weight: 400; font-display: swap; }
@font-face { font-family: 'Geist'; src: url('/assets/fonts/geist/Geist-Medium.woff2') format('woff2'); font-weight: 500; font-display: swap; }
@font-face { font-family: 'Geist'; src: url('/assets/fonts/geist/Geist-SemiBold.woff2') format('woff2'); font-weight: 600; font-display: swap; }
@font-face { font-family: 'Geist'; src: url('/assets/fonts/geist/Geist-Bold.woff2') format('woff2'); font-weight: 700; font-display: swap; }
''';

  @override
  String get componentCss =>
      '''
${super.componentCss}
#arcane-root.arcane-theme-shadcn {
  --ring: $_reactBlueCss;
}
html.dark #arcane-root.arcane-theme-shadcn,
#arcane-root.dark.arcane-theme-shadcn {
  --ring: $_reactBlueCss;
}
''';
}
