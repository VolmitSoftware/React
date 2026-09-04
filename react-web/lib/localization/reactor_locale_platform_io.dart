library;

List<String> browserReactorLocales() => const <String>[];

bool consumeReactorEscape(Object event) => false;

String reactorKeyboardKey(Object event) => '';

void focusReactorElement(String elementId) {}

void focusReactorDialog(String elementId) {}

bool trapReactorDialogFocus(Object event, String elementId) => false;

void focusReactorLanguageMenu(String elementId) {}

bool moveReactorLanguageMenuFocus(Object event, String elementId) => false;

void updateReactorDocumentLocale({
  required String localeCode,
  required String languageTag,
  required bool rtl,
  required String title,
  required String description,
}) {}
