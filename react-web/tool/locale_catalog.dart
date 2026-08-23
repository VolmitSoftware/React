import 'dart:convert';
import 'dart:io';

import 'package:react_web/localization/reactor_locale.dart';
import 'package:react_web/localization/reactor_localizations.dart';

const JsonEncoder _encoder = JsonEncoder.withIndent('  ');

void main(List<String> arguments) {
  const Set<String> supportedArguments = <String>{
    '--check',
    '--write-template',
    '--write-manifests',
  };
  if (arguments.any(
    (String argument) => !supportedArguments.contains(argument),
  )) {
    stderr.writeln(
      'Usage: dart run tool/locale_catalog.dart '
      '[--check] [--write-template] [--write-manifests]',
    );
    exitCode = 64;
    return;
  }
  final bool writeTemplate = arguments.contains('--write-template');
  final bool writeManifests =
      writeTemplate || arguments.contains('--write-manifests');
  if (writeTemplate) {
    _writeEnglishTemplate();
  }
  if (writeManifests) {
    _writeInstallManifests();
  }
  _validateCatalogs();
}

void _writeEnglishTemplate() {
  final Map<String, String> messages = <String, String>{
    for (final ReactorText key in ReactorText.values) key.id: key.english,
  };
  File(
    'web/languages/en_US.json',
  ).writeAsStringSync('${_encoder.convert(messages)}\n');
}

void _writeInstallManifests() {
  final Directory directory = Directory('web/manifests');
  directory.createSync(recursive: true);
  bool failed = false;
  for (final ReactorLocaleDefinition locale in reactorLocales) {
    final File catalogFile = File('web/languages/${locale.code}.json');
    try {
      final Map<String, String>? messages = _decodeStringCatalog(catalogFile);
      if (messages == null) {
        stderr.writeln('${locale.code}: catalog contains a non-string value');
        failed = true;
        continue;
      }
      final String? output = _encodeInstallManifest(locale, messages);
      if (output == null) {
        stderr.writeln('${locale.code}: catalog lacks install metadata');
        failed = true;
        continue;
      }
      File(
        '${directory.path}/${locale.code}.webmanifest',
      ).writeAsStringSync(output);
    } on FileSystemException catch (error) {
      stderr.writeln('${locale.code}: ${error.message}');
      failed = true;
    } on FormatException catch (error) {
      stderr.writeln('${locale.code}: invalid JSON: ${error.message}');
      failed = true;
    }
  }
  if (failed) exitCode = 1;
}

void _validateCatalogs() {
  final Set<String> expected = ReactorText.values
      .map((ReactorText key) => key.id)
      .toSet();
  bool failed = false;
  for (final String locale in reactorSupportedLocales) {
    final File file = File('web/languages/$locale.json');
    if (!file.existsSync()) {
      stderr.writeln('$locale: catalog is missing');
      failed = true;
      continue;
    }
    try {
      final Object? decoded = jsonDecode(file.readAsStringSync());
      if (decoded is! Map<String, Object?>) {
        stderr.writeln('$locale: catalog must be a JSON object');
        failed = true;
        continue;
      }
      final Set<String> actual = decoded.keys.toSet();
      final Set<String> missing = expected.difference(actual);
      final Set<String> unknown = actual.difference(expected);
      if (missing.isNotEmpty || unknown.isNotEmpty) {
        stderr.writeln(
          '$locale: missing ${missing.toList()..sort()}, '
          'unknown ${unknown.toList()..sort()}',
        );
        failed = true;
        continue;
      }
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorOverlayResult result = localizations.installOverlayJson(
        jsonEncode(decoded),
      );
      if (!result.applied || result.messageCount != expected.length) {
        stderr.writeln('$locale: ${result.error ?? 'incomplete catalog'}');
        failed = true;
        continue;
      }
      final Map<String, String> messages = <String, String>{
        for (final MapEntry<String, Object?> entry in decoded.entries)
          entry.key: entry.value! as String,
      };
      final ReactorLocaleDefinition metadata = reactorLocaleDefinition(locale);
      final String? manifest = _encodeInstallManifest(metadata, messages);
      final File manifestFile = File('web/manifests/$locale.webmanifest');
      if (manifest == null ||
          !manifestFile.existsSync() ||
          manifestFile.readAsStringSync() != manifest) {
        stderr.writeln('$locale: install manifest is missing or stale');
        failed = true;
      }
    } on FormatException catch (error) {
      stderr.writeln('$locale: invalid JSON: ${error.message}');
      failed = true;
    }
  }
  final Directory manifestDirectory = Directory('web/manifests');
  final Set<String> expectedManifestNames = <String>{
    for (final String locale in reactorSupportedLocales) '$locale.webmanifest',
  };
  final Set<String> actualManifestNames = manifestDirectory.existsSync()
      ? manifestDirectory
            .listSync()
            .whereType<File>()
            .map((File file) => file.uri.pathSegments.last)
            .where((String name) => name.endsWith('.webmanifest'))
            .toSet()
      : <String>{};
  final List<String> extraManifests =
      actualManifestNames.difference(expectedManifestNames).toList()..sort();
  if (extraManifests.isNotEmpty) {
    stderr.writeln(
      'web/manifests: unexpected files ${extraManifests.join(', ')}',
    );
    failed = true;
  }
  if (failed) {
    exitCode = 1;
    return;
  }
  stdout.writeln(
    '${reactorSupportedLocales.length} locale catalogs and install manifests '
    'are valid '
    '(${expected.length} keys each).',
  );
}

Map<String, String>? _decodeStringCatalog(File file) {
  final Object? decoded = jsonDecode(file.readAsStringSync());
  if (decoded is! Map<String, Object?> ||
      decoded.values.any((Object? value) => value is! String)) {
    return null;
  }
  return <String, String>{
    for (final MapEntry<String, Object?> entry in decoded.entries)
      entry.key: entry.value! as String,
  };
}

String? _encodeInstallManifest(
  ReactorLocaleDefinition locale,
  Map<String, String> messages,
) {
  final String? title = messages[ReactorText.appTitle.id];
  final String? description = messages[ReactorText.appDescription.id];
  if (title == null || description == null) return null;
  final Map<String, Object> manifest = <String, Object>{
    'name': title,
    'short_name': 'React',
    'description': description,
    'lang': locale.languageTag,
    'dir': locale.rtl ? 'rtl' : 'ltr',
    'id': '/',
    'start_url': '/',
    'scope': '/',
    'display': 'standalone',
    'background_color': '#0a0a0b',
    'theme_color': '#0a0a0b',
    'categories': <String>['developer', 'utilities'],
  };
  return '${_encoder.convert(manifest)}\n';
}
