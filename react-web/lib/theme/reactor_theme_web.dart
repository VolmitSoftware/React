library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:web/web.dart' as web;

void updateReactorThemeMetadata(Brightness brightness) {
  final web.Element root = web.document.documentElement!;
  root.classList.remove('light');
  root.classList.remove('dark');
  root.classList.add(brightness == Brightness.light ? 'light' : 'dark');
  final web.Element? themeColor = web.document.getElementById(
    'reactor-theme-color',
  );
  themeColor?.setAttribute(
    'content',
    brightness == Brightness.light ? '#f5f5f6' : '#0a0a0b',
  );
}
