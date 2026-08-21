library;

import 'package:http/http.dart' as http;

const String _configuredUrl = String.fromEnvironment(
  'REACTOR_LANGUAGE_URL',
  defaultValue: 'reactor-language.json',
);

Future<List<String>> loadReactorLocalizationSources(String locale) async {
  final List<String> sources = <String>[];
  if (locale != 'en_US') {
    final http.Response bundled = await http.get(
      Uri.base.resolve('languages/$locale.json'),
    );
    if (bundled.statusCode < 200 || bundled.statusCode >= 300) {
      throw StateError(
        'Bundled localization request failed with HTTP ${bundled.statusCode}.',
      );
    }
    sources.add(bundled.body);
  }

  final http.Response response = await http.get(
    Uri.base.resolve(_configuredUrl),
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
