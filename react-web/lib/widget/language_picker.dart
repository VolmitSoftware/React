library;

import 'dart:async' show Timer, unawaited;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;

import '../localization/reactor_locale.dart';
import '../localization/reactor_locale_platform.dart';
import '../localization/reactor_localizations.dart';

class ReactorLanguagePicker extends StatefulWidget {
  const ReactorLanguagePicker({super.key});

  @override
  State<ReactorLanguagePicker> createState() => _ReactorLanguagePickerState();
}

class _ReactorLanguagePickerState extends State<ReactorLanguagePicker> {
  bool _open = false;

  void _toggle() {
    if (_open) {
      _close();
      return;
    }
    setState(() => _open = true);
    context.binding.addPostFrameCallback(() {
      if (mounted) focusReactorLanguageMenu('reactor-language-options');
    });
  }

  void _close({bool restoreFocus = true}) {
    if (!_open) return;
    setState(() => _open = false);
    if (!restoreFocus) return;
    context.binding.addPostFrameCallback(() {
      if (mounted) focusReactorElement('reactor-language-trigger');
    });
  }

  void _onKeyDown(Object event) {
    if (!_open) return;
    final String key = reactorKeyboardKey(event);
    if (key == 'Escape') {
      if (consumeReactorEscape(event)) _close();
      return;
    }
    if (key == 'Tab') {
      Timer.run(() {
        if (mounted && _open) _close(restoreFocus: false);
      });
      return;
    }
    moveReactorLanguageMenuFocus(event, 'reactor-language-options');
  }

  void _select(ReactorLocaleScope? scope, String locale) {
    _close();
    if (scope == null || scope.loading || scope.locale == locale) return;
    unawaited(scope.onChanged(locale));
  }

  @override
  Widget build(BuildContext context) {
    final ReactorLocaleScope? scope = dependOnReactorLocale(context);
    final String activeLocale = scope?.locale ?? reactorEnglishLocale;
    final ReactorLocaleDefinition active = reactorLocaleDefinition(
      activeLocale,
    );
    final String triggerLabel = reactorText(
      _open ? ReactorText.languageClose : ReactorText.languageOpen,
      _open
          ? const <String, Object?>{}
          : <String, Object?>{'language': active.nativeName},
    );
    return dom.div(
      id: 'reactor-language-picker',
      classes: 'reactor-language-picker',
      events: <String, EventCallback>{'keydown': _onKeyDown},
      <Widget>[
        dom.button(
          id: 'reactor-language-trigger',
          classes: _open
              ? 'reactor-bar-button is-compact is-active'
              : 'reactor-bar-button is-compact',
          attributes: <String, String>{
            'type': 'button',
            'aria-label': triggerLabel,
            'aria-haspopup': 'menu',
            'aria-expanded': _open.toString(),
            'aria-controls': 'reactor-language-options',
          },
          events: <String, EventCallback>{'click': (_) => _toggle()},
          <Widget>[ArcaneIcon.languages(size: IconSize.sm)],
        ),
        if (_open) ...<Widget>[
          dom.div(
            id: 'reactor-language-backdrop',
            classes: 'reactor-language-backdrop',
            attributes: const <String, String>{'aria-hidden': 'true'},
            events: <String, EventCallback>{
              'click': (_) => _close(restoreFocus: false),
            },
            const <Widget>[],
          ),
          dom.div(
            id: 'reactor-language-options',
            classes: 'reactor-language-options',
            attributes: <String, String>{
              'role': 'menu',
              'aria-label': reactorText(ReactorText.languageSelect),
              'dir': active.rtl ? 'rtl' : 'ltr',
              'tabindex': '-1',
            },
            <Widget>[
              dom.div(classes: 'reactor-language-heading', <Widget>[
                Component.text(reactorText(ReactorText.languageSelect)),
              ]),
              for (final ReactorLocaleDefinition locale in reactorLocales)
                dom.button(
                  id: 'reactor-language-${locale.code}',
                  classes: locale.code == activeLocale
                      ? 'reactor-language-option is-active'
                      : 'reactor-language-option',
                  disabled: scope?.loading ?? false,
                  attributes: <String, String>{
                    'type': 'button',
                    'role': 'menuitemradio',
                    'aria-checked': (locale.code == activeLocale).toString(),
                    'tabindex': '-1',
                    'lang': locale.languageTag,
                    'dir': locale.rtl ? 'rtl' : 'ltr',
                  },
                  events: <String, EventCallback>{
                    'click': (_) => _select(scope, locale.code),
                  },
                  <Widget>[
                    dom.span(classes: 'reactor-language-option-name', <Widget>[
                      Component.text(locale.nativeName),
                    ]),
                    dom.span(
                      classes: 'reactor-language-option-check',
                      attributes: const <String, String>{'aria-hidden': 'true'},
                      <Widget>[
                        if (locale.code == activeLocale)
                          ArcaneIcon.check(size: IconSize.sm),
                      ],
                    ),
                  ],
                ),
            ],
          ),
        ],
      ],
    );
  }
}
