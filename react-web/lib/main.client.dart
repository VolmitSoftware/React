library;

import 'package:jaspr/client.dart';

import 'app/reactor_app.dart';
import 'localization/reactor_locale.dart';
import 'localization/reactor_locale_platform.dart';
import 'localization/reactor_localizations.dart';
import 'localization/reactor_overlay_loader.dart';
import 'main.client.options.dart';
import 'model/server_credential.dart';
import 'service/monotonic_counter.dart';
import 'service/react_client.dart';
import 'service/relay_connection.dart';
import 'service/relay_react_client.dart';
import 'state/fleet_manager.dart';
import 'state/web_fleet_storage.dart';
import 'theme/reactor_theme_web.dart';

Future<void> main() async {
  Jaspr.initializeApp(options: defaultClientOptions);
  final WebFleetStorage storage = WebFleetStorage();
  final String initialLocale = resolveInitialReactorLocale(
    storage: storage,
    browserLocales: browserReactorLocales(),
    configuredLocale: configuredReactorLocale,
  );
  final ReactorLocaleManager localeManager = ReactorLocaleManager(
    initialLocale: reactorEnglishLocale,
    loader: loadReactorLocalizationOverlay,
    storage: storage,
  );
  await localeManager.switchTo(initialLocale);
  final ReactorLocaleDefinition locale = reactorLocaleDefinition(
    localeManager.locale,
  );
  updateReactorDocumentLocale(
    localeCode: locale.code,
    languageTag: locale.languageTag,
    rtl: locale.rtl,
    title: reactorText(ReactorText.appTitle),
    description: reactorText(ReactorText.appDescription),
  );
  final FleetManager fleet = FleetManager(
    storage: storage,
    clientFactory: (ServerCredential cred) =>
        ReactClient(cred, counter: MonotonicCounter(storage)),
    relayClientFactory: (ServerCredential cred) {
      final String? relayUrl = cred.relayUrl;
      final String? fingerprint = cred.fingerprint;
      if (relayUrl == null ||
          relayUrl.isEmpty ||
          fingerprint == null ||
          fingerprint.isEmpty) {
        return null;
      }
      return RelayReactClient(
        createRelayConnection(relayUrl, fingerprint),
        cred,
        counter: MonotonicCounter(storage),
      );
    },
  );
  runApp(
    ReactorApp(
      fleetManager: fleet,
      localeManager: localeManager,
      onThemeChanged: updateReactorThemeMetadata,
    ),
  );
}
