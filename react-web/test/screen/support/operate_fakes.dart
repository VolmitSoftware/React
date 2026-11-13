/// Fake servers and a scope harness for the console, actions, tuning and
/// plugins screen tests. Each fake server is a real `ServerSession` around
/// a recording client, so a test can tell which server a read or a write
/// went to.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/log_line.dart';
import 'package:react_web/model/plugin_api_pack.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_log_socket.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/role_scope.dart';
import 'package:react_web/state/server_scope.dart';
import 'package:react_web/state/server_session.dart';
import 'package:react_web/state/server_session_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/confirm_dialog.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:jaspr/jaspr.dart' show Component, Element;
import 'package:jaspr_test/jaspr_test.dart' show ComponentTester, find;

import '../../state/support/fake_react_client.dart' show FakeReactClient;

const ReactorStylesheet testSheet = ReactorStylesheet();

const RoleInfo viewerRole = RoleInfo(role: 'viewer', scopes: <String>['read']);
const RoleInfo operatorRole = RoleInfo(
  role: 'operator',
  scopes: <String>['read', 'op:execute'],
);
const RoleInfo adminRole = RoleInfo(
  role: 'admin',
  scopes: <String>[
    'read',
    'op:execute',
    'admin',
    'console:read',
    'console:execute',
  ],
);

ControlItem controlItem(
  String id, {
  String? name,
  String category = 'Runtime',
  bool enabled = false,
  String description = '',
}) => ControlItem(
  id: id,
  name: name ?? id,
  category: category,
  enabled: enabled,
  description: description,
);

WorldSettings worldSettings(String name, {double budget = 45}) => WorldSettings(
  key: 'minecraft:$name',
  name: name,
  pressureMode: PressureMode.normal,
  budgetMs: budget,
  panicMs: budget + 5,
  releaseMs: budget - 5,
);

PluginApiCatalog packCatalog(String name) => PluginApiCatalog(
  folder: '/plugins/React/plugin-apis',
  packs: <PluginApiPack>[
    PluginApiPack(
      id: 'example.$name',
      version: '1.0.0',
      name: name,
      authors: const <String>['Volmit Software'],
      targetPlugin: 'Adapt',
      targetVersion: '2.4.0',
      targetVersions: const <String>['*'],
      enabled: true,
      trusted: false,
      state: 'HEALTHY',
      detail: '',
      fileName: '$name.toml',
      rawContent: 'id = "example.$name"',
      metrics: const <PluginApiMetric>[
        PluginApiMetric(
          id: 'ops',
          samplerId: 'plugin-api-example-ops',
          displayName: 'Executions',
          sourceType: 'integration',
          available: true,
          availabilityReason: '',
          sampledAtMs: 0,
          sampleDurationMs: 1,
          acceptedSamples: 1,
          failedSamples: 0,
          quarantined: false,
        ),
      ],
    ),
  ],
  errors: const <PluginApiValidationError>[],
);

/// The recording client of one fake server, with a console and packs.
class OperateClient extends FakeReactClient implements IConsoleClient {
  OperateClient({
    super.name,
    super.features,
    super.tweaks,
    super.worlds,
    super.config,
    super.actions,
    List<String> logs = const <String>[],
    PluginApiCatalog? packs,
  }) : packs = packs ?? packCatalog('Pack'),
       super(logs: <LogLine>[for (final String line in logs) LogLine(line)]);

  final PluginApiCatalog packs;

  /// Console commands, in order.
  final List<String> commands = <String>[];

  /// Each action run: id, parameters and whether it was confirmed.
  final List<(String, Map<String, Object?>, bool)> runs =
      <(String, Map<String, Object?>, bool)>[];

  /// Calls that start with [prefix] ("PUT /features/").
  int writes(String prefix) =>
      calls.where((String call) => call.startsWith(prefix)).length;

  @override
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  }) {
    runs.add((id, params, confirm));
    return super.executeAction(id, params: params, confirm: confirm);
  }

  @override
  Future<bool> executeConsole(String command) async {
    calls.add('POST /console/execute');
    commands.add(command);
    return true;
  }

  @override
  Future<PluginApiCatalog> pluginApiPacks() async {
    calls.add('GET /plugin-api-packs');
    return packs;
  }

  @override
  Future<PluginApiValidationResult> validatePluginApiPack(
    String content,
  ) async {
    calls.add('POST /plugin-api-packs/validate');
    return const PluginApiValidationResult(
      valid: true,
      id: 'pack',
      metricCount: 1,
      message: '',
    );
  }

  @override
  Future<PluginApiPack> installPluginApiPack(String id, String content) async {
    calls.add('PUT /plugin-api-packs/$id');
    return packs.packs.first;
  }

  @override
  Future<PluginApiCatalog> removePluginApiPack(String id) async {
    calls.add('DELETE /plugin-api-packs/$id');
    return packs;
  }
}

/// A log socket that stays quiet until a test adds a line.
class FakeLogSocket implements ILogSocket {
  final StreamController<LogBatch> controller = StreamController<LogBatch>();
  bool closed = false;

  void add(String line) =>
      controller.add(LogBatch(<LogLine>[LogLine(line)], dropped: 0));

  @override
  Stream<LogBatch> get batches => controller.stream;

  @override
  Future<void> close() async {
    closed = true;
  }
}

ServerSnapshot snapshotWith(Map<String, double> values, {int seq = 1}) =>
    ServerSnapshot(
      byId: <String, SamplerSample>{
        for (final MapEntry<String, double> entry in values.entries)
          entry.key: SamplerSample(
            id: entry.key,
            name: entry.key,
            suffix: 'ms',
            value: entry.value,
            display: entry.value.toString(),
            min: entry.value,
            max: entry.value,
            history: <double>[entry.value * 0.9, entry.value],
          ),
      },
      at: DateTime(2026),
      seq: seq,
    );

int _servers = 0;

/// One fake server: its recording client and the session a server page
/// reads its controllers from.
class FakeServer {
  FakeServer({OperateClient? client, this.socket, this.snapshot})
    : client = client ?? OperateClient() {
    final String id = 'server-${++_servers}';
    final FakeLogSocket? logs = socket;
    session = ServerSession(
      credential: ServerCredential(
        id: id,
        label: id,
        host: '127.0.0.1',
        port: 9696,
        bearer: '$id.sig',
      ),
      client: this.client,
      connection: ConnectionManager(this.client),
      logSocketFactory: logs == null ? null : () => logs,
    );
  }

  final OperateClient client;
  final FakeLogSocket? socket;
  final ServerSnapshot? snapshot;
  late final ServerSession session;
}

/// [child] inside a server page's scopes for [server].
Widget serverScopes(
  FakeServer? server,
  Widget child, {
  RoleInfo? role = adminRole,
  ConnState state = ConnState.live,
  ReactorMode mode = ReactorMode.advanced,
}) {
  final Widget page = RoleScope(
    role: role,
    child: ServerScope(snapshot: server?.snapshot, state: state, child: child),
  );
  return ArcaneThemeProvider(
    stylesheet: testSheet,
    child: ModeScope(
      mode: mode,
      onChanged: (ReactorMode _) {},
      child: server == null
          ? page
          : ServerSessionScope(session: server.session, child: page),
    ),
  );
}

/// Shows one of several servers and switches between them, as the address
/// moving to another server does. The scope is not keyed here, so a screen
/// passes only when it reads its server in build.
class ServerSwitcher extends StatefulWidget {
  const ServerSwitcher({
    required this.servers,
    required this.child,
    this.role = adminRole,
    this.mode = ReactorMode.advanced,
    super.key,
  });

  final List<FakeServer> servers;
  final Widget child;
  final RoleInfo? role;
  final ReactorMode mode;

  @override
  State<ServerSwitcher> createState() => ServerSwitcherState();
}

class ServerSwitcherState extends State<ServerSwitcher> {
  int _shown = 0;

  void show(int index) => setState(() => _shown = index);

  @override
  Widget build(BuildContext context) => serverScopes(
    component.servers[_shown],
    component.child,
    role: component.role,
    mode: component.mode,
  );
}

/// [child] with the host that shows `askToConfirm` questions, as the shell
/// mounts it.
Widget withConfirmHost(Widget child) => Component.fragment(<Widget>[
  child,
  const ArcaneThemeProvider(stylesheet: testSheet, child: ConfirmHost()),
]);

/// Answers every question on screen and lets the answers land. Questions
/// asked earlier with no host on screen stay queued in the kit, so they are
/// answered too (they are already settled, the answer does nothing).
Future<void> answerConfirm(ComponentTester tester, {required bool yes}) async {
  for (int round = 0; round < 8; round++) {
    final List<Element> dialogs = find
        .byType(ConfirmDialog)
        .evaluate()
        .toList();
    if (dialogs.isEmpty) return;
    final ConfirmDialog dialog = dialogs.first.component as ConfirmDialog;
    yes ? dialog.onConfirm() : dialog.onCancel();
    await tester.pump();
    await tester.pump();
  }
}

/// Takes the page down, so its confirm host leaves with it.
Future<void> unmount(ComponentTester tester) async {
  tester.pumpComponent(const Component.fragment(<Widget>[]));
  await tester.pump();
}
