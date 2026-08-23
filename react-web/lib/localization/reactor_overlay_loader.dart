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

String get configuredReactorLocale => _configuredLocale;

Future<String?> loadReactorLocalizationOverlay(String locale) async {
  final String canonicalLocale = canonicalReactorLocale(locale);
  final List<String> sources = await platform.loadReactorLocalizationSources(
    canonicalLocale,
    includeDeploymentOverride: usesConfiguredReactorOverride(
      canonicalLocale,
      configuredReactorLocale,
    ),
  );
  if (sources.isEmpty) {
    return null;
  }
  final ReactorOverlayResult bundledValidation = ReactorLocalizations()
      .installCompleteCatalogJson(sources.first);
  if (!bundledValidation.applied) {
    throw FormatException(
      bundledValidation.error ?? 'Bundled locale catalog is incomplete.',
    );
  }
  final Map<String, Object?> merged = <String, Object?>{};
  for (final String source in sources) {
    final Object? decoded = jsonDecode(source);
    if (decoded is! Map<String, Object?>) {
      throw const FormatException('Localization source must be a JSON object.');
    }
    merged.addAll(decoded);
  }
  return jsonEncode(merged);
}

bool usesConfiguredReactorOverride(
  String selectedLocale,
  String configuredLocale,
) {
  try {
    return canonicalReactorLocale(selectedLocale) ==
        canonicalReactorLocale(configuredLocale);
  } on ArgumentError {
    return false;
  }
}
