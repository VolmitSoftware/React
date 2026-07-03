library;

import 'package:jaspr/client.dart';

import 'app/reactor_app.dart';
import 'main.client.options.dart';
import 'model/server_credential.dart';
import 'service/monotonic_counter.dart';
import 'service/react_client.dart';
import 'state/fleet_manager.dart';
import 'state/web_fleet_storage.dart';

void main() {
  Jaspr.initializeApp(options: defaultClientOptions);
  final WebFleetStorage storage = WebFleetStorage();
  final FleetManager fleet = FleetManager(
    storage: storage,
    clientFactory: (ServerCredential cred) =>
        ReactClient(cred, counter: MonotonicCounter(storage)),
  );
  runApp(ReactorApp(fleetManager: fleet));
}
