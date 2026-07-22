library;

import 'dart:convert';

import 'reactor_overlay_loader_io.dart'
    if (dart.library.js_interop) 'reactor_overlay_loader_web.dart'
    as platform;
import 'reactor_localizations.dart';

const String _configuredLocale = String.fromEnvironment(
  'REACTOR_LANGUAGE',
  defaultValue: reactorEnglishLocale,
);

String get configuredReactorLocale => canonicalReactorLocale(_configuredLocale);

Future<String?> loadReactorLocalizationOverlay() async {
  final List<String> sources = await platform.loadReactorLocalizationSources(
    configuredReactorLocale,
  );
  if (sources.isEmpty) {
    return null;
  }
  final Map<String, dynamic> merged = <String, dynamic>{};
  for (final String source in sources) {
    final Object? decoded = jsonDecode(source);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Localization source must be a JSON object.');
    }
    merged.addAll(decoded);
  }
  return jsonEncode(merged);
}
