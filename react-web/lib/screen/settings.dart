/// Settings > General: theme, language and Simple or Advanced, kept in this
/// browser. Each applies at once.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../theme/reactor_theme.dart';
import '../ui/kit/card.dart';
import '../ui/kit/density.dart';
import '../ui/kit/info_tip.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/toggle_choice.dart';

/// The id of the language picker's button.
const String languagePickerId = 'settings-language';

class SettingsGeneralTab extends StatelessWidget {
  const SettingsGeneralTab({super.key});

  @override
  Widget build(BuildContext context) {
    final ReactorLocaleScope? locale = dependOnReactorLocale(context);
    final ReactorThemeScope? theme = ReactorThemeScope.maybeOf(context);
    final ModeScope? mode = ModeScope.maybeOf(context);
    return AppCard(
      children: <Widget>[
        dom.div(classes: 'reactor-field-grid', <Widget>[
          if (theme != null)
            ArcaneFormField(
              children: <Widget>[
                ArcaneFieldTitle(reactorText(ReactorText.settingsTheme)),
                ToggleChoice<Brightness>(
                  label: reactorText(ReactorText.settingsTheme),
                  value: theme.brightness,
                  options: <ChoiceOption<Brightness>>[
                    ChoiceOption<Brightness>(
                      Brightness.dark,
                      reactorText(ReactorText.settingsDarkTheme),
                    ),
                    ChoiceOption<Brightness>(
                      Brightness.light,
                      reactorText(ReactorText.settingsLightTheme),
                    ),
                  ],
                  onChanged: theme.onChanged,
                ),
              ],
            ),
          if (locale != null)
            ArcaneFormField(
              controlId: languagePickerId,
              children: <Widget>[
                ArcaneFieldLabel(reactorText(ReactorText.settingsLanguage)),
                ArcaneCombobox<String>(
                  id: '$languagePickerId-list',
                  triggerId: languagePickerId,
                  size: DensityScope.isDense(context)
                      ? ComboboxSize.sm
                      : ComboboxSize.md,
                  value: locale.locale,
                  disabled: locale.loading,
                  searchPlaceholder: reactorText(ReactorText.languageSelect),
                  options: <ComboboxOption<String>>[
                    for (final ReactorLocaleDefinition each in reactorLocales)
                      ComboboxOption<String>(
                        value: each.code,
                        label: each.nativeName,
                      ),
                  ],
                  onChanged: (String? code) {
                    if (code != null && code != locale.locale) {
                      locale.onChanged(code);
                    }
                  },
                ),
              ],
            ),
          if (mode != null)
            ArcaneFormField(
              children: <Widget>[
                ArcaneFieldTitle(
                  reactorText(ReactorText.settingsMode),
                  trailing: <Widget>[
                    InfoTip(tip: reactorText(ReactorText.settingsModeTip)),
                  ],
                ),
                ToggleChoice<ReactorMode>(
                  label: reactorText(ReactorText.settingsMode),
                  value: mode.mode,
                  options: <ChoiceOption<ReactorMode>>[
                    ChoiceOption<ReactorMode>(
                      ReactorMode.simple,
                      reactorText(ReactorText.modeSimple),
                    ),
                    ChoiceOption<ReactorMode>(
                      ReactorMode.advanced,
                      reactorText(ReactorText.modeAdvanced),
                    ),
                  ],
                  onChanged: mode.onChanged,
                ),
              ],
            ),
        ]),
      ],
    );
  }
}
