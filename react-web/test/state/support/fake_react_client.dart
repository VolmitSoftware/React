library;

import 'dart:async';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/environment_info.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/log_line.dart';
import 'package:react_web/model/plugin_api_pack.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_revisions.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

ControlItem controlItem(
  String id, {
  bool enabled = false,
  Map<String, Object?> knobs = const <String, Object?>{},
}) => ControlItem(
  id: id,
  name: id,
  category: 'World',
  enabled: enabled,
  description: '',
  knobs: <Knob>[
    for (final MapEntry<String, Object?> knob in knobs.entries)
      Knob(
        key: knob.key,
        label: knob.key,
        type: KnobType.intType,
        value: knob.value,
      ),
  ],
);

ServerSnapshot snapshotWith({
  int seq = 1,
  Map<RevisionKind, int>? revisions,
  double tps = 20,
}) => ServerSnapshot(
  byId: <String, SamplerSample>{
    'ticks-per-second': SamplerSample(
      id: 'ticks-per-second',
      name: 'TPS',
      suffix: '',
      value: tps,
      display: '$tps',
      min: tps,
      max: tps,
      history: const <double>[],
    ),
  },
  at: DateTime.fromMillisecondsSinceEpoch(1000 * seq),
  seq: seq,
  revisions: revisions == null ? null : ServerRevisions(revisions),
);

/// One fake server behind every client interface the state layer uses.
/// Records each call as `METHOD path` in [calls].
class FakeReactClient
    implements
        IReactClient,
        IPingClient,
        IControlClient,
        IOperateClient,
        IActionTicketClient,
        ILogLineClient,
        IRoleClient,
        IPluginApiPackClient {
  final String name;
  final String fingerprint;
  final List<String> calls = <String>[];
  List<ControlItem> featureList;
  List<ControlItem> tweakList;
  List<WorldSettings> worldList;
  ConfigTree configTree;
  List<ActionDescriptor> actionList;
  final Map<String, List<ActionTicketStatus>> ticketScript =
      <String, List<ActionTicketStatus>>{};
  List<LogLine> logBacklog;
  int? logLastSeq;
  String role;
  Object? metricsError;
  Object? controlError;

  /// Thrown by every request for log lines while set.
  Object? logsError;
  ServerSnapshot metricsSnapshot = snapshotWith();
  Completer<void>? writeGate;
  bool closed = false;
  int _ticket = 0;

  FakeReactClient({
    this.name = 'Fake',
    this.fingerprint = 'fp-fake',
    List<ControlItem>? features,
    List<ControlItem>? tweaks,
    List<WorldSettings>? worlds,
    ConfigTree? config,
    List<ActionDescriptor>? actions,
    List<LogLine>? logs,
    this.role = 'admin',
  }) : featureList = features ?? <ControlItem>[],
       tweakList = tweaks ?? <ControlItem>[],
       worldList = worlds ?? <WorldSettings>[],
       configTree = config ?? const ConfigTree(),
       actionList = actions ?? <ActionDescriptor>[],
       logBacklog = logs ?? <LogLine>[];

  int count(String call) => calls.where((String c) => c == call).length;

  Future<void> _gate() async {
    final Completer<void>? gate = writeGate;
    if (gate != null) await gate.future;
    final Object? error = controlError;
    if (error != null) throw error;
  }

  ControlItem _set(List<ControlItem> list, String id, ControlItem next) {
    final int index = list.indexWhere((ControlItem i) => i.id == id);
    list[index] = next;
    return next;
  }

  @override
  Future<void> close() async => closed = true;

  @override
  Future<IdentityInfo> identity() async {
    calls.add('GET /identity');
    return IdentityInfo.fromJson(<String, dynamic>{
      'serverName': name,
      'folia': false,
      'serverId': fingerprint,
    });
  }

  @override
  Future<ServerCapabilities> ping() async {
    calls.add('GET /ping');
    return ServerCapabilities(
      protocolVersion: 2,
      serverFingerprint: fingerprint,
      relayAvailable: false,
    );
  }

  @override
  Future<RoleInfo> whoami() async {
    calls.add('GET /whoami');
    return RoleInfo(role: role, scopes: const <String>['read']);
  }

  @override
  Future<ServerSnapshot> metrics() async {
    calls.add('GET /metrics');
    final Object? error = metricsError;
    if (error != null) throw error;
    return metricsSnapshot;
  }

  @override
  Future<List<ControlItem>> features() async {
    calls.add('GET /features');
    return List<ControlItem>.of(featureList);
  }

  @override
  Future<ControlItem> feature(String id) async =>
      featureList.firstWhere((ControlItem i) => i.id == id);

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) async {
    calls.add('PUT /features/$id');
    await _gate();
    return _set(
      featureList,
      id,
      featureList
          .firstWhere((ControlItem i) => i.id == id)
          .copyWith(enabled: enabled),
    );
  }

  @override
  Future<ControlItem> setFeatureConfig(
    String id,
    Map<String, Object?> knobs,
  ) async {
    calls.add('PUT /features/$id/config ${knobs.keys.join(',')}');
    await _gate();
    ControlItem next = featureList.firstWhere((ControlItem i) => i.id == id);
    for (final MapEntry<String, Object?> knob in knobs.entries) {
      next = next.withKnobValue(knob.key, knob.value);
    }
    return _set(featureList, id, next);
  }

  @override
  Future<List<ControlItem>> tweaks() async {
    calls.add('GET /tweaks');
    return List<ControlItem>.of(tweakList);
  }

  @override
  Future<ControlItem> tweak(String id) async =>
      tweakList.firstWhere((ControlItem i) => i.id == id);

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) async {
    calls.add('PUT /tweaks/$id');
    await _gate();
    return _set(
      tweakList,
      id,
      tweakList
          .firstWhere((ControlItem i) => i.id == id)
          .copyWith(enabled: enabled),
    );
  }

  @override
  Future<ControlItem> setTweakConfig(
    String id,
    Map<String, Object?> knobs,
  ) async {
    calls.add('PUT /tweaks/$id/config ${knobs.keys.join(',')}');
    await _gate();
    ControlItem next = tweakList.firstWhere((ControlItem i) => i.id == id);
    for (final MapEntry<String, Object?> knob in knobs.entries) {
      next = next.withKnobValue(knob.key, knob.value);
    }
    return _set(tweakList, id, next);
  }

  @override
  Future<List<WorldSettings>> worlds() async {
    calls.add('GET /worlds');
    return List<WorldSettings>.of(worldList);
  }

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    calls.add('PUT /worlds/$name');
    await _gate();
    final int index = worldList.indexWhere((WorldSettings w) => w.name == name);
    worldList[index] = worldList[index].copyWith(
      budgetMs: budgetMs,
      panicMs: panicMs,
      releaseMs: releaseMs,
    );
    return worldList[index];
  }

  @override
  Future<List<ActionDescriptor>> actions() async {
    calls.add('GET /actions');
    return List<ActionDescriptor>.of(actionList);
  }

  @override
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  }) async {
    calls.add('POST /actions/$id/execute');
    final String ticketId = '$name-ticket-${++_ticket}';
    return ActionTicket(ticketId: ticketId, status: 'queued');
  }

  @override
  Future<ActionTicketStatus> actionTicket(String ticketId) async {
    calls.add('GET /actions/tickets/$ticketId');
    final List<ActionTicketStatus>? script = ticketScript[ticketId];
    if (script == null || script.isEmpty) {
      throw const ReactNotFound('Unknown ticket');
    }
    return script.length == 1 ? script.first : script.removeAt(0);
  }

  @override
  Future<IncidentStatus> incidents() async => throw UnimplementedError();

  @override
  Future<EnvironmentInfo> environment() async => throw UnimplementedError();

  @override
  Future<ConfigTree> config() async {
    calls.add('GET /config');
    return configTree;
  }

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async {
    calls.add('PUT /config');
    return configTree;
  }

  @override
  Future<ConfigTree> applyPreset(String name) async {
    calls.add('POST /config/preset/$name');
    return configTree;
  }

  @override
  Future<List<String>> logs({int limit = 200}) async =>
      (await logLines(limit: limit)).map((LogLine l) => l.text).toList();

  @override
  Future<List<LogLine>> logLines({int limit = 200, int? after}) async {
    calls.add('GET /logs${after == null ? '' : '?after=$after'}');
    final Object? error = logsError;
    if (error != null) throw error;
    final Iterable<LogLine> lines = after == null
        ? logBacklog
        : logBacklog.where((LogLine l) => l.seq == null || l.seq! > after);
    final List<LogLine> list = lines.toList();
    return list.length <= limit ? list : list.sublist(list.length - limit);
  }

  @override
  Future<PluginApiCatalog> pluginApiPacks() async {
    calls.add('GET /plugin-api-packs');
    return PluginApiCatalog.fromJson(<String, dynamic>{
      'folder': 'packs',
      'packs': <Object?>[],
      'errors': <Object?>[],
    });
  }

  @override
  Future<PluginApiValidationResult> validatePluginApiPack(String content) =>
      throw UnimplementedError();

  @override
  Future<PluginApiPack> installPluginApiPack(String id, String content) =>
      throw UnimplementedError();

  @override
  Future<PluginApiCatalog> removePluginApiPack(String id) =>
      throw UnimplementedError();
}
