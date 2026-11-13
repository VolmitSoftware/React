/// A paired fleet for the server group, alerts, pairing and settings tests:
/// real stores over memory storage, stub clients, and finders for the kit's
/// buttons and dialogs.
library;

import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component, DomComponent;
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_live_scope.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';
import 'package:react_web/state/fleet_scope.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_tags_store.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/theme/reactor_theme.dart';
import 'package:react_web/ui/kit/confirm_dialog.dart';
import 'package:react_web/ui/kit/mode.dart';

const String testFingerprint =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

const RoleInfo adminRole = RoleInfo(
  role: 'admin',
  scopes: <String>['read', 'op:execute', 'admin'],
);
const RoleInfo viewerRole = RoleInfo(role: 'viewer', scopes: <String>['read']);

/// A server that answers whoami with [role] (or throws [roleError]) and
/// pings with [fingerprint] (or throws [pingError]).
class StubClient implements IReactClient, IRoleClient, IPingClient {
  StubClient({
    this.role = adminRole,
    this.roleError,
    this.identityError,
    this.pingError,
    this.fingerprint = testFingerprint,
    this.name = 'Stub',
  });

  final RoleInfo role;
  final Object? roleError;
  final Object? identityError;
  final Object? pingError;
  final String fingerprint;
  final String name;

  @override
  Future<RoleInfo> whoami() async {
    final Object? error = roleError;
    if (error != null) throw error;
    return role;
  }

  @override
  Future<IdentityInfo> identity() async {
    final Object? error = identityError;
    if (error != null) throw error;
    return IdentityInfo(serverName: name, folia: false, serverId: fingerprint);
  }

  @override
  Future<ServerCapabilities> ping() async {
    final Object? error = pingError;
    if (error != null) throw error;
    return ServerCapabilities(
      protocolVersion: 2,
      serverFingerprint: fingerprint,
      relayAvailable: false,
    );
  }

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  @override
  Future<void> close() async {}
}

ServerCredential testCredential(
  String id,
  String label, {
  String host = 'localhost',
  int port = 7979,
  String? relayUrl,
}) => ServerCredential(
  id: id,
  label: label,
  host: host,
  port: port,
  bearer: 'token.$id',
  relayUrl: relayUrl,
  fingerprint: testFingerprint,
);

/// A fleet controller over a real [FleetManager] in memory that records
/// what the screens ask of it.
class TestFleet implements FleetController {
  TestFleet({
    List<ServerCredential> servers = const <ServerCredential>[],
    StubClient Function(ServerCredential cred)? client,
  }) {
    if (servers.isNotEmpty) {
      storage.write(
        FleetManager.storageKey,
        jsonEncode(<Map<String, dynamic>>[
          for (final ServerCredential cred in servers) cred.toJson(),
        ]),
      );
    }
    fleetManager = FleetManager(
      storage: storage,
      clientFactory: client ?? (ServerCredential _) => StubClient(),
    );
    alertStore = AlertStore(storage);
    tagsStore = ServerTagsStore(storage);
  }

  final InMemoryFleetStorage storage = InMemoryFleetStorage();
  final List<String> removed = <String>[];
  final List<String> tracked = <String>[];
  int cleared = 0;

  @override
  late final FleetManager fleetManager;

  @override
  late final AlertStore alertStore;

  @override
  late final ServerTagsStore tagsStore;

  @override
  ConnectionManager? managerFor(String id) => null;

  @override
  String? labelFor(String id) => null;

  @override
  void trackPaired(String id) => tracked.add(id);

  @override
  void removeServer(String id) {
    removed.add(id);
    fleetManager.remove(id);
  }

  @override
  void clearFleet() {
    cleared++;
    fleetManager.clearAll();
  }

  @override
  void importFleet(List<ServerCredential> creds) =>
      fleetManager.importReplace(creds);
}

SamplerSample testSample(
  String id,
  double value, {
  String suffix = '',
  String? display,
  List<double>? history,
}) => SamplerSample(
  id: id,
  name: id,
  suffix: suffix,
  value: value,
  display: display ?? value.toString(),
  min: 0.0,
  max: 100.0,
  history: history ?? <double>[value],
);

/// A server as the live model reports it, with [samplers] in its snapshot.
FleetServerLive liveServer(
  String id,
  String name, {
  ConnState state = ConnState.live,
  Map<String, double>? samplers,
  String? role,
  ServerTransport? transport,
  ConnAccess access = ConnAccess.granted,
  String? fingerprint,
}) => FleetServerLive(
  id: id,
  name: name,
  state: state,
  snapshot: samplers == null
      ? null
      : ServerSnapshot(
          byId: <String, SamplerSample>{
            for (final MapEntry<String, double> each in samplers.entries)
              each.key: testSample(each.key, each.value),
          },
          at: DateTime.now(),
          seq: 1,
        ),
  lastSeen: DateTime.now(),
  role: role,
  transport: transport,
  access: access,
  fingerprint: fingerprint,
);

/// [child] inside the scopes the shell provides. [confirms] mounts the
/// host of `askToConfirm` questions; only component tests may: a host left
/// in a server-rendered page stays registered and breaks later tests.
Widget fleetHarness(
  Widget child, {
  TestFleet? fleet,
  bool confirms = false,
  List<FleetServerLive> servers = const <FleetServerLive>[],
  ReactorMode mode = ReactorMode.simple,
  void Function(ReactorMode mode)? onMode,
  Brightness brightness = Brightness.dark,
  void Function(Brightness brightness)? onTheme,
}) {
  final Widget inner = FleetLiveScope(
    servers: servers,
    revision: 1,
    child: confirms
        ? Component.fragment(<Widget>[child, const ConfirmHost()])
        : child,
  );
  return ArcaneThemeProvider(
    stylesheet: const ReactorStylesheet(),
    child: ReactorThemeScope(
      brightness: brightness,
      onChanged: onTheme ?? (Brightness _) {},
      child: ModeScope(
        mode: mode,
        onChanged: onMode ?? (ReactorMode _) {},
        child: fleet == null
            ? inner
            : FleetScope(controller: fleet, revision: 1, child: inner),
      ),
    ),
  );
}

/// The element with DOM id [id].
Finder byDomId(String id) => find.byComponentPredicate(
  (Component component) => component is DomComponent && component.id == id,
  description: 'element #$id',
);

/// The button whose text is [label].
Finder buttonLabeled(String label) =>
    find.ancestor(of: find.text(label), matching: find.tag('button'));

/// The button named [label] by its aria-label.
Finder buttonNamed(String label) => find.byComponentPredicate(
  (Component component) =>
      component is DomComponent &&
      component.tag == 'button' &&
      component.attributes?['aria-label'] == label,
  description: 'button "$label"',
);

/// The menu entry with text [label] (a row's "..." menu).
Finder menuEntry(String label) => find.ancestor(
  of: find.text(label),
  matching: find.byComponentPredicate(
    (Component component) =>
        component is DomComponent &&
        component.attributes?['role'] == 'menuitem',
    description: 'menu entry "$label"',
  ),
);

/// The confirm button of the open question.
Finder confirmButton() => find.byComponentPredicate(
  (Component component) =>
      component is DomComponent &&
      component.attributes?.containsKey('data-confirm') == true,
  description: 'confirm button',
);

/// The button with text [label] in the open question (an alert dialog).
Finder questionButton(String label) => find.descendant(
  of: find.byComponentPredicate(
    (Component component) =>
        component is DomComponent &&
        component.attributes?['role'] == 'alertdialog',
    description: 'open question',
  ),
  matching: buttonLabeled(label),
);
