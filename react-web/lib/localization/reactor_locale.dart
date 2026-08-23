library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../state/fleet_manager.dart';
import 'reactor_localizations.dart';

const String reactorLocaleStorageKey = 'reactor.locale';

final class ReactorLocaleDefinition {
  final String code;
  final String languageTag;
  final String nativeName;
  final bool rtl;

  const ReactorLocaleDefinition({
    required this.code,
    required this.languageTag,
    required this.nativeName,
    this.rtl = false,
  });
}

const List<ReactorLocaleDefinition> reactorLocales = <ReactorLocaleDefinition>[
  ReactorLocaleDefinition(
    code: 'en_US',
    languageTag: 'en-US',
    nativeName: 'English',
  ),
  ReactorLocaleDefinition(
    code: 'de_DE',
    languageTag: 'de-DE',
    nativeName: 'Deutsch',
  ),
  ReactorLocaleDefinition(
    code: 'es_ES',
    languageTag: 'es-ES',
    nativeName: 'Español',
  ),
  ReactorLocaleDefinition(
    code: 'fi_FI',
    languageTag: 'fi-FI',
    nativeName: 'Suomi',
  ),
  ReactorLocaleDefinition(
    code: 'fr_FR',
    languageTag: 'fr-FR',
    nativeName: 'Français',
  ),
  ReactorLocaleDefinition(
    code: 'he_IL',
    languageTag: 'he-IL',
    nativeName: 'עברית',
    rtl: true,
  ),
  ReactorLocaleDefinition(
    code: 'it_IT',
    languageTag: 'it-IT',
    nativeName: 'Italiano',
  ),
  ReactorLocaleDefinition(
    code: 'ja-JP',
    languageTag: 'ja-JP',
    nativeName: '日本語',
  ),
  ReactorLocaleDefinition(
    code: 'ko_KR',
    languageTag: 'ko-KR',
    nativeName: '한국어',
  ),
  ReactorLocaleDefinition(
    code: 'lt_LT',
    languageTag: 'lt-LT',
    nativeName: 'Lietuvių',
  ),
  ReactorLocaleDefinition(
    code: 'nl_NL',
    languageTag: 'nl-NL',
    nativeName: 'Nederlands',
  ),
  ReactorLocaleDefinition(
    code: 'pl_PL',
    languageTag: 'pl-PL',
    nativeName: 'Polski',
  ),
  ReactorLocaleDefinition(
    code: 'pt_PT',
    languageTag: 'pt-PT',
    nativeName: 'Português',
  ),
  ReactorLocaleDefinition(
    code: 'ru_RU',
    languageTag: 'ru-RU',
    nativeName: 'Русский',
  ),
  ReactorLocaleDefinition(
    code: 'tr_TR',
    languageTag: 'tr-TR',
    nativeName: 'Türkçe',
  ),
  ReactorLocaleDefinition(
    code: 'vi_VI',
    languageTag: 'vi-VN',
    nativeName: 'Tiếng Việt',
  ),
  ReactorLocaleDefinition(
    code: 'zh_CN',
    languageTag: 'zh-CN',
    nativeName: '简体中文',
  ),
  ReactorLocaleDefinition(
    code: 'zh_TW',
    languageTag: 'zh-TW',
    nativeName: '繁體中文',
  ),
];

int reactorLanguageMenuTargetIndex({
  required int current,
  required int count,
  required String key,
}) {
  if (count <= 0) return -1;
  return switch (key) {
    'Home' => 0,
    'End' => count - 1,
    'ArrowUp' => current <= 0 ? count - 1 : current - 1,
    'ArrowDown' => current < 0 || current >= count - 1 ? 0 : current + 1,
    _ => -1,
  };
}

ReactorLocaleDefinition reactorLocaleDefinition(String locale) {
  final String canonical = canonicalReactorLocale(locale);
  return reactorLocales.firstWhere(
    (ReactorLocaleDefinition definition) => definition.code == canonical,
  );
}

String? matchReactorLocale(String? locale) {
  if (locale == null || locale.trim().isEmpty) return null;
  try {
    return canonicalReactorLocale(locale);
  } on ArgumentError {
    final String normalized = locale.trim().replaceAll('_', '-').toLowerCase();
    final List<String> parts = normalized.split('-');
    final String language = parts.first;
    if (language == 'zh') {
      final bool traditional = parts.any(
        (String part) =>
            part == 'tw' || part == 'hk' || part == 'mo' || part == 'hant',
      );
      return traditional ? 'zh_TW' : 'zh_CN';
    }
    for (final ReactorLocaleDefinition definition in reactorLocales) {
      if (definition.languageTag.toLowerCase().split('-').first == language) {
        return definition.code;
      }
    }
    return null;
  }
}

String? matchReactorLocales(Iterable<String> locales) {
  for (final String locale in locales) {
    final String? match = matchReactorLocale(locale);
    if (match != null) return match;
  }
  return null;
}

String resolveInitialReactorLocale({
  FleetStorage? storage,
  Iterable<String> browserLocales = const <String>[],
  String? configuredLocale,
}) {
  String? persisted;
  try {
    persisted = storage?.read(reactorLocaleStorageKey);
  } catch (_) {}
  return matchReactorLocale(persisted) ??
      matchReactorLocales(browserLocales) ??
      matchReactorLocale(configuredLocale) ??
      reactorEnglishLocale;
}

void persistReactorLocale(FleetStorage? storage, String locale) {
  if (storage == null) return;
  try {
    storage.write(reactorLocaleStorageKey, canonicalReactorLocale(locale));
  } catch (_) {}
}

final class ReactorLocaleSwitchResult {
  final bool applied;
  final bool superseded;
  final String locale;
  final String? error;

  const ReactorLocaleSwitchResult({
    required this.applied,
    required this.superseded,
    required this.locale,
    this.error,
  });
}

final class ReactorLocaleManager {
  final FleetStorage? storage;
  final ReactorLocalizations localizations;
  final Future<String?> Function(String locale) loader;

  String _locale;
  int _request = 0;

  ReactorLocaleManager({
    required String initialLocale,
    required this.loader,
    this.storage,
    ReactorLocalizations? localizations,
  }) : _locale = canonicalReactorLocale(initialLocale),
       localizations = localizations ?? reactorLocalizations;

  String get locale => _locale;

  Future<ReactorLocaleSwitchResult> switchTo(String requestedLocale) async {
    final String locale;
    try {
      locale = canonicalReactorLocale(requestedLocale);
    } on ArgumentError catch (error) {
      return ReactorLocaleSwitchResult(
        applied: false,
        superseded: false,
        locale: _locale,
        error: error.message?.toString() ?? 'Unsupported locale.',
      );
    }

    final int request = ++_request;
    try {
      final String? source = await loader(locale);
      if (request != _request) {
        return ReactorLocaleSwitchResult(
          applied: false,
          superseded: true,
          locale: _locale,
        );
      }
      if (source == null || source.trim().isEmpty) {
        return ReactorLocaleSwitchResult(
          applied: false,
          superseded: false,
          locale: _locale,
          error: 'The locale catalog is missing.',
        );
      }
      final ReactorOverlayResult result = localizations
          .installCompleteCatalogJson(source);
      if (!result.applied) {
        return ReactorLocaleSwitchResult(
          applied: false,
          superseded: false,
          locale: _locale,
          error: result.error ?? 'The locale catalog could not be applied.',
        );
      }
      _locale = locale;
      persistReactorLocale(storage, locale);
      return ReactorLocaleSwitchResult(
        applied: true,
        superseded: false,
        locale: locale,
      );
    } catch (error) {
      if (request != _request) {
        return ReactorLocaleSwitchResult(
          applied: false,
          superseded: true,
          locale: _locale,
        );
      }
      return ReactorLocaleSwitchResult(
        applied: false,
        superseded: false,
        locale: _locale,
        error: error.toString(),
      );
    }
  }
}

class ReactorLocaleScope extends InheritedWidget {
  final String locale;
  final bool loading;
  final Future<void> Function(String locale) onChanged;

  const ReactorLocaleScope({
    required this.locale,
    required this.loading,
    required this.onChanged,
    required super.child,
    super.key,
  });

  static ReactorLocaleScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ReactorLocaleScope>();

  static ReactorLocaleScope of(BuildContext context) {
    final ReactorLocaleScope? scope = maybeOf(context);
    if (scope == null) {
      throw StateError('No ReactorLocaleScope found in this context.');
    }
    return scope;
  }

  ReactorLocaleDefinition get definition => reactorLocaleDefinition(locale);

  @override
  bool updateShouldNotify(ReactorLocaleScope oldComponent) =>
      locale != oldComponent.locale ||
      loading != oldComponent.loading ||
      onChanged != oldComponent.onChanged;
}

ReactorLocaleScope? dependOnReactorLocale(BuildContext context) =>
    ReactorLocaleScope.maybeOf(context);
