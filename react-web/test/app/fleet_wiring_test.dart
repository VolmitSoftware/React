library;

import 'dart:async';
import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_scope.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_tags_store.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

const ServerCredential _credential = ServerCredential(
  id: 'server-1',
  label: 'Paper',
  host: 'localhost',
  port: 8111,
  bearer: 'token',
);

class _StubReactClient implements IReactClient {
  final Completer<ServerSnapshot> _snapshot = Completer<ServerSnapshot>();

  @override
  Future<IdentityInfo> identity() async => const IdentityInfo(
    serverName: 'Paper',
    version: '1.21.11',
    folia: false,
    serverId: 'server-1',
  );

  @override
  Future<ServerSnapshot> metrics() => _snapshot.future;
}

FleetManager _fleetManager(InMemoryFleetStorage storage) => FleetManager(
  storage: storage,
  clientFactory: (ServerCredential _) => _StubReactClient(),
);

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

    testComponents('removing a tracked server clears its persisted state', (
      ComponentTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write(
        FleetManager.storageKey,
        jsonEncode(<Map<String, dynamic>>[_credential.toJson()]),
      );
      ServerTagsStore(storage).setTags(_credential.id, <String>['survival']);
      final FleetManager fleet = _fleetManager(storage);
      final GlobalStateKey<ReactorFleetObserverState> key =
          GlobalStateKey<ReactorFleetObserverState>();

      tester.pumpComponent(
        _wrap(ReactorFleetObserver(key: key, fleetManager: fleet)),
      );
      await tester.pump();
      final ReactorFleetObserverState state = key.currentState!;
      state.alertStore.ack('${_credential.id}/tps');
      state.alertStore.resolve('${_credential.id}/mspt');
      state.removeServer(_credential.id);
      await tester.pump();

      expect(fleet.servers, isEmpty);
      expect(jsonDecode(storage.read(FleetManager.storageKey)!), isEmpty);
      expect(ServerTagsStore(storage).tagsFor(_credential.id), isEmpty);
      expect(AlertStore(storage).isAcked('${_credential.id}/tps'), isFalse);
      expect(AlertStore(storage).isResolved('${_credential.id}/mspt'), isFalse);
    });

    testComponents(
      'removing an untracked server still clears its persisted state',
      (ComponentTester tester) async {
        final InMemoryFleetStorage storage = InMemoryFleetStorage();
        final FleetManager fleet = _fleetManager(storage);
        final GlobalStateKey<ReactorFleetObserverState> key =
            GlobalStateKey<ReactorFleetObserverState>();

        tester.pumpComponent(
          _wrap(ReactorFleetObserver(key: key, fleetManager: fleet)),
        );
        await tester.pump();
        await fleet.add(_credential);
        ServerTagsStore(storage).setTags(_credential.id, <String>['survival']);
        final ReactorFleetObserverState state = key.currentState!;
        state.alertStore.ack('${_credential.id}/tps');
        state.alertStore.resolve('${_credential.id}/mspt');

        state.removeServer(_credential.id);
        await tester.pump();

        expect(fleet.servers, isEmpty);
        expect(jsonDecode(storage.read(FleetManager.storageKey)!), isEmpty);
        expect(ServerTagsStore(storage).tagsFor(_credential.id), isEmpty);
        expect(AlertStore(storage).isAcked('${_credential.id}/tps'), isFalse);
        expect(
          AlertStore(storage).isResolved('${_credential.id}/mspt'),
          isFalse,
        );
      },
    );
  });
}
