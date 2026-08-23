library;

import 'reactor_locale_platform_io.dart'
    if (dart.library.js_interop) 'reactor_locale_platform_web.dart'
    as platform;

List<String> browserReactorLocales() => platform.browserReactorLocales();

bool consumeReactorEscape(Object event) => platform.consumeReactorEscape(event);

String reactorKeyboardKey(Object event) => platform.reactorKeyboardKey(event);

void focusReactorElement(String elementId) =>
    platform.focusReactorElement(elementId);

void focusReactorLanguageMenu(String elementId) =>
    platform.focusReactorLanguageMenu(elementId);

bool moveReactorLanguageMenuFocus(Object event, String elementId) =>
    platform.moveReactorLanguageMenuFocus(event, elementId);

void updateReactorDocumentLocale({
  required String localeCode,
  required String languageTag,
  required bool rtl,
  required String title,
  required String description,
}) => platform.updateReactorDocumentLocale(
  localeCode: localeCode,
  languageTag: languageTag,
  rtl: rtl,
  title: title,
  description: description,
);
