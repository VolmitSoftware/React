/// The app root: the shadcn Midnight stylesheet, the language, direction,
/// theme and mode scopes, the paired servers and the toasts. The shell,
/// routes and server scopes live beside it in lib/app.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_locale_platform.dart';
import '../localization/reactor_localizations.dart';
import '../localization/reactor_overlay_loader.dart';
import '../state/fleet_manager.dart';
import '../theme/reactor_stylesheet.dart';
import '../theme/reactor_theme.dart';
import '../ui/kit/mode.dart';
import 'fleet_observer.dart';

class ReactorApp extends StatefulWidget {
  final FleetManager? fleetManager;
  final ReactorLocaleManager? localeManager;
  final ValueChanged<Brightness>? onThemeChanged;

  const ReactorApp({
    this.fleetManager,
    this.localeManager,
    this.onThemeChanged,
    super.key,
  });

  @override
  State<ReactorApp> createState() => _ReactorAppState();
}

class _ReactorAppState extends State<ReactorApp> {
  late Brightness _brightness;
  late ReactorMode _mode;
  late ReactorLocaleManager _localeManager;
  bool _localeLoading = false;

  @override
  void initState() {
    super.initState();
    _brightness = loadReactorBrightness(component.fleetManager?.storage);
    _mode = loadReactorMode(component.fleetManager?.storage);
    _localeManager =
        component.localeManager ??
        ReactorLocaleManager(
          initialLocale: reactorEnglishLocale,
          loader: loadReactorLocalizationOverlay,
          storage: component.fleetManager?.storage,
        );
  }

  void _setBrightness(Brightness brightness) {
    if (_brightness == brightness) return;
    persistReactorBrightness(component.fleetManager?.storage, brightness);
    component.onThemeChanged?.call(brightness);
    setState(() => _brightness = brightness);
  }

  void _setMode(ReactorMode mode) {
    if (_mode == mode) return;
    persistReactorMode(component.fleetManager?.storage, mode);
    setState(() => _mode = mode);
  }

  Future<void> _setLocale(String locale) async {
    if (_localeLoading || locale == _localeManager.locale) return;
    setState(() => _localeLoading = true);
    final ReactorLocaleSwitchResult result = await _localeManager.switchTo(
      locale,
    );
    if (!mounted) return;
    if (result.applied) {
      final ReactorLocaleDefinition definition = reactorLocaleDefinition(
        result.locale,
      );
      updateReactorDocumentLocale(
        localeCode: definition.code,
        languageTag: definition.languageTag,
        rtl: definition.rtl,
        title: reactorText(ReactorText.appTitle),
        description: reactorText(ReactorText.appDescription),
      );
    } else if (!result.superseded) {
      ArcaneSonner.error(reactorText(ReactorText.languageLoadFailed));
    }
    setState(() => _localeLoading = false);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return ArcaneApp(
      brightness: _brightness,
      stylesheet: const ReactorStylesheet(),
      home: ReactorLocaleScope(
        locale: _localeManager.locale,
        loading: _localeLoading,
        onChanged: _setLocale,
        child: ArcaneDirection(
          value: reactorLocaleDefinition(_localeManager.locale).rtl
              ? ArcaneDirectionValue.rtl
              : ArcaneDirectionValue.ltr,
          children: <Widget>[
            ReactorThemeScope(
              brightness: _brightness,
              onChanged: _setBrightness,
              child: ModeScope(
                mode: _mode,
                onChanged: _setMode,
                child: Component.fragment(<Widget>[
                  ReactorFleetObserver(fleetManager: component.fleetManager),
                  // Top centre is the one place no page puts a primary
                  // action at 1600, 768 or 390 px: sheet and dialog
                  // footers, Run and Pair sit low or to the right. One
                  // toast at a time, the newest: a second one below it
                  // reached the page tabs at 768 px.
                  // shadcn's place for toasts, clear of the page header.
                  const ArcaneSonner(
                    position: ToastPosition.bottomRight,
                    maxVisible: 1,
                  ),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
