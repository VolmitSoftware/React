library;

import 'dart:js_interop';

import 'package:web/web.dart' as web;

import 'reactor_locale.dart';

List<String> browserReactorLocales() {
  final List<String> locales = web.window.navigator.languages.toDart
      .map((JSString language) => language.toDart)
      .where((String language) => language.isNotEmpty)
      .toList(growable: false);
  if (locales.isNotEmpty) return locales;
  final String fallback = web.window.navigator.language;
  return fallback.isEmpty ? const <String>[] : <String>[fallback];
}

bool consumeReactorEscape(Object event) {
  final JSObject jsEvent = event as JSObject;
  if (!jsEvent.isA<web.KeyboardEvent>()) return false;
  final web.KeyboardEvent keyboard = jsEvent as web.KeyboardEvent;
  if (keyboard.key != 'Escape') return false;
  keyboard.preventDefault();
  keyboard.stopPropagation();
  return true;
}

String reactorKeyboardKey(Object event) {
  final JSObject jsEvent = event as JSObject;
  if (!jsEvent.isA<web.KeyboardEvent>()) return '';
  return (jsEvent as web.KeyboardEvent).key;
}

void focusReactorElement(String elementId) {
  final web.Element? element = web.document.getElementById(elementId);
  if (element == null || !element.isA<web.HTMLElement>()) return;
  (element as web.HTMLElement).focus();
}

void focusReactorDialog(String elementId) {
  final web.Element? shell = web.document.getElementById(elementId);
  if (shell == null) return;
  final web.Element? action = shell.querySelector(
    '[role="dialog"] .arcane-dialog-footer button:not([disabled])',
  );
  final web.Element? fallback = shell.querySelector(
    '[role="dialog"] button:not([disabled]), '
    '[role="dialog"] [href], '
    '[role="dialog"] input:not([disabled]), '
    '[role="dialog"] select:not([disabled]), '
    '[role="dialog"] textarea:not([disabled]), '
    '[role="dialog"] [tabindex]:not([tabindex="-1"])',
  );
  final web.Element? target = action ?? fallback;
  if (target == null || !target.isA<web.HTMLElement>()) return;
  (target as web.HTMLElement).focus();
}

bool trapReactorDialogFocus(Object event, String elementId) {
  final JSObject jsEvent = event as JSObject;
  if (!jsEvent.isA<web.KeyboardEvent>()) return false;
  final web.KeyboardEvent keyboard = jsEvent as web.KeyboardEvent;
  if (keyboard.key != 'Tab') return false;
  final web.Element? shell = web.document.getElementById(elementId);
  if (shell == null) return false;
  final web.NodeList focusable = shell.querySelectorAll(
    '[role="dialog"] button:not([disabled]), '
    '[role="dialog"] [href], '
    '[role="dialog"] input:not([disabled]), '
    '[role="dialog"] select:not([disabled]), '
    '[role="dialog"] textarea:not([disabled]), '
    '[role="dialog"] [tabindex]:not([tabindex="-1"])',
  );
  if (focusable.length == 0) return false;
  final web.Element? active = web.document.activeElement;
  final web.Element? first = focusable.item(0) as web.Element?;
  final web.Element? last =
      focusable.item(focusable.length - 1) as web.Element?;
  final bool wrapBackward =
      keyboard.shiftKey &&
      (identical(active, first) || active == null || !shell.contains(active));
  final bool wrapForward = !keyboard.shiftKey && identical(active, last);
  if (!wrapBackward && !wrapForward) return false;
  final web.Element? target = wrapBackward ? last : first;
  if (target == null || !target.isA<web.HTMLElement>()) return false;
  keyboard.preventDefault();
  keyboard.stopPropagation();
  (target as web.HTMLElement).focus();
  return true;
}

void focusReactorLanguageMenu(String elementId) {
  final web.Element? menu = web.document.getElementById(elementId);
  if (menu == null) return;
  final web.Element? selected = menu.querySelector(
    '[role="menuitemradio"][aria-checked="true"]:not(:disabled)',
  );
  final web.Element? first = menu.querySelector(
    '[role="menuitemradio"]:not(:disabled)',
  );
  final web.Element target = selected ?? first ?? menu;
  if (!target.isA<web.HTMLElement>()) return;
  (target as web.HTMLElement).focus();
}

bool moveReactorLanguageMenuFocus(Object event, String elementId) {
  final String key = reactorKeyboardKey(event);
  final web.Element? menu = web.document.getElementById(elementId);
  if (menu == null) return false;
  final web.NodeList options = menu.querySelectorAll(
    '[role="menuitemradio"]:not(:disabled)',
  );
  final web.Element? active = web.document.activeElement;
  int current = -1;
  for (int index = 0; index < options.length; index += 1) {
    if (identical(options.item(index), active)) {
      current = index;
      break;
    }
  }
  final int target = reactorLanguageMenuTargetIndex(
    current: current,
    count: options.length,
    key: key,
  );
  if (target < 0) return false;
  final JSObject jsEvent = event as JSObject;
  final web.KeyboardEvent keyboard = jsEvent as web.KeyboardEvent;
  keyboard.preventDefault();
  keyboard.stopPropagation();
  (options.item(target) as web.HTMLElement?)?.focus();
  return true;
}

void updateReactorDocumentLocale({
  required String localeCode,
  required String languageTag,
  required bool rtl,
  required String title,
  required String description,
}) {
  final web.Element? root = web.document.documentElement;
  root?.setAttribute('lang', languageTag);
  root?.setAttribute('dir', rtl ? 'rtl' : 'ltr');
  web.document.title = title;
  _setMeta('meta[name="description"]', 'content', description);
  _setMeta('meta[property="og:title"]', 'content', title);
  _setMeta('meta[property="og:description"]', 'content', description);
  _setMeta('meta[name="twitter:title"]', 'content', title);
  _setMeta('meta[name="twitter:description"]', 'content', description);
  web.document
      .querySelector('link[rel="manifest"]')
      ?.setAttribute('href', '/manifests/$localeCode.webmanifest');
}

void _setMeta(String selector, String attribute, String value) {
  web.document.querySelector(selector)?.setAttribute(attribute, value);
}
