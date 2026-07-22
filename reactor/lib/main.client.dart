library;

import 'package:jaspr/client.dart';

import 'app/reactor_app.dart';
import 'localization/reactor_localizations.dart';
import 'localization/reactor_overlay_loader.dart';
import 'main.client.options.dart';
import 'model/server_credential.dart';
import 'service/monotonic_counter.dart';
import 'service/react_client.dart';
import 'state/fleet_manager.dart';
import 'state/web_fleet_storage.dart';

Future<void> main() async {
  Jaspr.initializeApp(options: defaultClientOptions);
  await reactorLocalizations.loadOverlayOnce(loadReactorLocalizationOverlay);
  final WebFleetStorage storage = WebFleetStorage();
  final FleetManager fleet = FleetManager(
    storage: storage,
    clientFactory: (ServerCredential cred) =>
        ReactClient(cred, counter: MonotonicCounter(storage)),
  );
  runApp(ReactorApp(fleetManager: fleet));
}
