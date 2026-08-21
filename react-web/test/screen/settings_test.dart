library;

import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_scope.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_tags_store.dart';
import 'package:react_web/theme/reactor_theme.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

const String _alphaId = 'srv-alpha';
const String _betaId = 'srv-beta';

class _StubClient implements IReactClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: 'Stub',
    version: '1.0.0',
    folia: false,
    serverId: '127.0.0.1:9696',
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

Map<String, dynamic> _credJson({required String id, required String label}) =>
    <String, dynamic>{
      'id': id,
      'label': label,
      'host': 'localhost',
      'port': 7979,
      'bearer': 'tok.$id',
      'secure': false,
    };

class _FakeFleetController implements FleetController {
  final InMemoryFleetStorage _storage;
  late final FleetManager _fleetManager;
  late final AlertStore _alertStore;
  late final ServerTagsStore _tagsStore;

  _FakeFleetController(this._storage) {
    _storage.write(
      FleetManager.storageKey,
      jsonEncode(<Map<String, dynamic>>[
        _credJson(id: _alphaId, label: 'Alpha'),
        _credJson(id: _betaId, label: 'Beta'),
      ]),
    );
    _fleetManager = FleetManager(
      storage: _storage,
      clientFactory: (_) => _StubClient(),
    );
    _alertStore = AlertStore(_storage);
    _tagsStore = ServerTagsStore(_storage);
  }

  @override
  FleetManager get fleetManager => _fleetManager;

  @override
  AlertStore get alertStore => _alertStore;

  @override
  ServerTagsStore get tagsStore => _tagsStore;

  @override
  ConnectionManager? managerFor(String id) => null;

  @override
  String? labelFor(String id) => null;

  @override
  void trackPaired(String id) {}

  @override
  void removeServer(String id) {}

  @override
  void clearFleet() {
    _fleetManager.clearAll();
  }

  @override
  void importFleet(List<ServerCredential> creds) {
    _fleetManager.importReplace(creds);
  }
}

Widget _wrapSettings({required _FakeFleetController ctrl}) =>
    ArcaneThemeProvider(
      stylesheet: _sheet,
      child: ReactorThemeScope(
        brightness: Brightness.dark,
        onChanged: (Brightness _) {},
        child: FleetScope(
          controller: ctrl,
          revision: 0,
          child: const SettingsScreen(),
        ),
      ),
    );

void main() {
  group('SettingsScreen — appearance', () {
    testServer('renders dark and light theme choices', (
      ServerTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, equals(200));
      expect(response.body, contains('data-theme-choice="dark"'));
      expect(response.body, contains('data-theme-choice="light"'));
      expect(response.body, contains('aria-pressed="true"'));
      expect(response.body, contains('High-clarity workspace'));
    });
  });

  group('SettingsScreen — server labels', () {
    testServer('renders Alpha server label', (ServerTester tester) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Alpha'),
        isTrue,
        reason: 'Alpha server label must appear in settings body',
      );
    });

    testServer('renders Beta server label', (ServerTester tester) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Beta'),
        isTrue,
        reason: 'Beta server label must appear in settings body',
      );
    });

    testServer('renders role lookup failures as a warning state', (
      ServerTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(res.body, contains('Some roles are unavailable'));
      expect(res.body, contains('Unavailable'));
      expect(res.body, contains('reactor-notice is-warning'));
    });

    testServer('renders a dedicated empty fleet state', (
      ServerTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      ctrl.fleetManager.clearAll();
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(
        res.body,
        contains(ReactorText.settingsNoServersConfigured.english),
      );
      expect(res.body, contains('reactor-pane-state is-empty'));
    });
  });

  group('SettingsScreen — threshold defaults', () {
    testServer('renders default tpsWarn value 18', (ServerTester tester) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('18'),
        isTrue,
        reason: 'default tpsWarn=18.0 must appear in threshold inputs',
      );
    });

    testServer('renders default tpsCrit value 10', (ServerTester tester) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('10'),
        isTrue,
        reason: 'default tpsCrit=10.0 must appear in threshold inputs',
      );
    });

    testServer('renders Save thresholds button', (ServerTester tester) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.settingsSaveThresholds.english),
        isTrue,
        reason: 'Save thresholds button must appear in settings',
      );
    });

    testServer('renders Clear all control for saved servers', (
      ServerTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.settingsClearAll.english),
        isTrue,
        reason: 'settings must expose a fleet-wide clear action',
      );
    });
  });

  group('SettingsScreen — tags display', () {
    testServer('pre-seeded tag prod appears for alpha server', (
      ServerTester tester,
    ) async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final _FakeFleetController ctrl = _FakeFleetController(storage);
      ctrl.tagsStore.setTags(_alphaId, <String>['prod']);
      tester.pumpComponent(_wrapSettings(ctrl: ctrl));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('prod'),
        isTrue,
        reason: 'pre-seeded tag prod must appear for alpha server',
      );
    });
  });
}
