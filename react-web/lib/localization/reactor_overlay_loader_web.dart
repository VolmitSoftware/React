library;

import 'package:http/http.dart' as http;

import 'reactor_asset_uri.dart';

const String _configuredUrl = String.fromEnvironment(
  'REACTOR_LANGUAGE_URL',
  defaultValue: '/reactor-language.json',
);

Future<List<String>> loadReactorLocalizationSources(
  String locale, {
  required bool includeDeploymentOverride,
}) async {
  final List<String> sources = <String>[];
  final http.Response bundled = await http.get(
    resolveReactorWebAssetUri(Uri.base, '/languages/$locale.json'),
  );
  if (bundled.statusCode < 200 || bundled.statusCode >= 300) {
    throw StateError(
      'Bundled localization request failed with HTTP ${bundled.statusCode}.',
    );
  }
  sources.add(bundled.body);

  if (!includeDeploymentOverride) {
    return sources;
  }
  final http.Response response = await http.get(
    resolveReactorWebAssetUri(Uri.base, _configuredUrl),
  );
  if (response.statusCode == 404) {
    return sources;
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw StateError(
      'Localization overlay request failed with HTTP ${response.statusCode}.',
    );
  }
  sources.add(response.body);
  return sources;
}
