library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/state/alert_store.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/fleet_scope.dart';
import 'package:reactor/state/memory_fleet_storage.dart';
import 'package:reactor/state/server_tags_store.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

class _StoreProbe extends StatelessWidget {
  const _StoreProbe();

  @override
  Widget build(BuildContext context) {
    final FleetController? ctrl = FleetScope.of(context);
    final String alertLabel = ctrl?.alertStore != null
        ? 'alertStore:ok'
        : 'alertStore:null';
    final String tagsLabel = ctrl?.tagsStore != null
        ? 'tagsStore:ok'
        : 'tagsStore:null';
    return Text('$alertLabel $tagsLabel');
  }
}

class _FakeFleetController implements FleetController {
  final InMemoryFleetStorage _storage = InMemoryFleetStorage();

  @override
  FleetManager get fleetManager => throw UnimplementedError();

  @override
  ConnectionManager? managerFor(String id) => null;

  @override
  String? labelFor(String id) => null;

  @override
  void trackPaired(String id) {}

  @override
  void removeServer(String id) {}

  @override
  void clearFleet() {}

  @override
  void importFleet(List<ServerCredential> creds) {}

  @override
  AlertStore get alertStore => AlertStore(_storage);

  @override
  ServerTagsStore get tagsStore => ServerTagsStore(_storage);
}

void main() {
  group('FleetController alertStore + tagsStore wiring', () {
    test('AlertStore can be constructed from InMemoryFleetStorage', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final AlertStore store = AlertStore(storage);
      expect(store, isNotNull);
    });

    test('ServerTagsStore can be constructed from InMemoryFleetStorage', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final ServerTagsStore store = ServerTagsStore(storage);
      expect(store, isNotNull);
    });

    testServer(
      'FleetScope controller exposes non-null alertStore and tagsStore',
      (ServerTester tester) async {
        final _FakeFleetController ctrl = _FakeFleetController();
        tester.pumpComponent(
          _wrap(
            FleetScope(
              controller: ctrl,
              revision: 0,
              child: const _StoreProbe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('alertStore:ok'),
          isTrue,
          reason: 'FleetController.alertStore must be non-null',
        );
        expect(
          res.body.contains('tagsStore:ok'),
          isTrue,
          reason: 'FleetController.tagsStore must be non-null',
        );
      },
    );

    testServer('ReactorFleetObserver renders root route without throwing', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(const ReactorFleetObserver()));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, 200);
    });
  });
}
