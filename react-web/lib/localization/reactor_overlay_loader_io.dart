library;

import 'dart:io';

const String _configuredPath = String.fromEnvironment(
  'REACTOR_LANGUAGE_FILE',
  defaultValue: 'reactor-language.json',
);

Future<List<String>> loadReactorLocalizationSources(String locale) async {
  final List<String> sources = <String>[];
  if (locale != 'en_US') {
    final List<File> bundledCandidates = <File>[
      File('web/languages/$locale.json'),
      File('languages/$locale.json'),
    ];
    final File? bundled = await _firstExisting(bundledCandidates);
    if (bundled == null) {
      throw StateError('Bundled Reactor locale is missing: $locale');
    }
    sources.add(await bundled.readAsString());
  }

  final List<File> overrideCandidates = <File>[
    File(_configuredPath),
    if (!File(_configuredPath).isAbsolute) File('web/$_configuredPath'),
  ];
  final File? override = await _firstExisting(overrideCandidates);
  if (override != null) {
    sources.add(await override.readAsString());
  }
  return sources;
}

Future<File?> _firstExisting(List<File> candidates) async {
  for (final File candidate in candidates) {
    if (await candidate.exists()) {
      return candidate;
    }
  }
  return null;
}
