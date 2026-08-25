library;

import 'dart:convert';

import '../model/action_descriptor.dart';
import '../model/config_tree.dart';
import '../model/control_item.dart';
import '../model/environment_info.dart';
import '../model/heatmap.dart';
import '../model/identity_info.dart';
import '../model/incident_status.dart';
import '../model/metric_history.dart';
import '../model/player_navigation.dart';
import '../model/relay_frame.dart';
import '../model/role_info.dart';
import '../model/server_capabilities.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import '../model/world_settings.dart';
import '../state/memory_fleet_storage.dart';
import 'monotonic_counter.dart';
import 'react_client.dart';
import 'react_exceptions.dart';
import 'relay_connection.dart';

class RelayReactClient
    implements
        IReactClient,
        IPingClient,
        IHeatmapClient,
        IPlayerClient,
        IControlClient,
        IOperateClient,
        IConsoleClient,
        IRoleClient,
        IHistoryClient {
  final IRelayConnection _connection;
  final ServerCredential _cred;
  final MonotonicCounter _counter;

  RelayReactClient(
    IRelayConnection connection,
    ServerCredential cred, {
    MonotonicCounter? counter,
  }) : _connection = connection,
       _cred = cred,
       _counter = counter ?? MonotonicCounter(InMemoryFleetStorage());

  Map<String, String> _headers({required bool mutating}) => <String, String>{
    'Authorization': 'Bearer ${_cred.bearer}',
    'Content-Type': 'application/json',
    if (mutating) 'X-React-Counter': _counter.next(_cred.id).toString(),
  };

  Future<RelayResponse> _request(
    String method,
    String path, {
    Map<String, Object?>? body,
  }) async {
    final bool mutating = method != 'GET';
    final RelayResponse response = await _connection.request(
      method: method,
      path: '/api/v1$path',
      headers: _headers(mutating: mutating),
      body: body == null ? null : jsonEncode(body),
    );
    switch (response.status) {
      case 200:
      case 202:
        return response;
      case 400:
        throw ReactBadRequest(_errorMessage(response.body));
      case 401:
        throw const ReactAuthException();
      case 403:
        throw ReactForbidden(_errorMessage(response.body));
      case 404:
        throw ReactNotFound(_errorMessage(response.body));
      case 409:
        throw ReactConflict(_errorMessage(response.body));
      default:
        throw ReactUnavailable(_errorMessage(response.body));
    }
  }

  Future<RelayResponse> _get(String path) => _request('GET', path);

  Future<Map<String, dynamic>> _putData(
    String path,
    Map<String, Object?> body,
  ) async {
    final RelayResponse response = await _request('PUT', path, body: body);
    return _decodeData(response.body);
  }

  Future<Map<String, dynamic>> _postData(
    String path,
    Map<String, Object?> body,
  ) async {
    final RelayResponse response = await _request('POST', path, body: body);
    return _decodeData(response.body);
  }

  String _errorMessage(Map<String, dynamic> body) {
    final Object? error = body['error'];
    if (error is Map<String, dynamic>) {
      final Object? message = error['message'];
      if (message is String) return message;
    }
    return 'Request failed';
  }

  Map<String, dynamic> _decodeData(Map<String, dynamic> body) {
    final Object? data = body['data'];
    if (data is! Map<String, dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data object');
    }
    return data;
  }

  List<dynamic> _decodeList(Map<String, dynamic> body) {
    final Object? data = body['data'];
    if (data is! List<dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data list');
    }
    return data;
  }

  Future<List<ControlItem>> _getItems(String path) async {
    final RelayResponse response = await _get(path);
    return _decodeList(response.body)
        .map(
          (dynamic entry) =>
              ControlItem.fromJson(entry as Map<String, dynamic>),
        )
        .toList();
  }

  @override
  Future<ServerCapabilities> ping() async {
    final RelayResponse response = await _get('/ping');
    return ServerCapabilities.fromJson(_decodeData(response.body));
  }

  @override
  Future<IdentityInfo> identity() async {
    final RelayResponse response = await _get('/identity');
    return IdentityInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<RoleInfo> whoami() async {
    final RelayResponse response = await _get('/whoami');
    return RoleInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<ServerSnapshot> metrics() async {
    final RelayResponse response = await _get('/metrics');
    return ServerSnapshot.fromJson(response.body);
  }

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async {
    final RelayResponse response = await _get('/metrics/catalog');
    return _decodeList(response.body)
        .map(
          (dynamic value) =>
              MetricHistoryDescriptor.fromJson(value as Map<String, dynamic>),
        )
        .toList();
  }

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) async {
    final String path;
    if (cursor != null) {
      path = '/metrics/history?cursor=${Uri.encodeQueryComponent(cursor)}';
    } else {
      if (ids == null || ids.isEmpty || from == null || to == null) {
        throw ArgumentError('ids, from, and to are required without a cursor');
      }
      final Map<String, String> query = <String, String>{
        'ids': ids.join(','),
        'from': from.millisecondsSinceEpoch.toString(),
        'to': to.millisecondsSinceEpoch.toString(),
        'maxPoints': maxPoints.toString(),
        'pageSize': pageSize.toString(),
      };
      path =
          '/metrics/history?${query.entries.map((MapEntry<String, String> entry) => '${Uri.encodeQueryComponent(entry.key)}=${Uri.encodeQueryComponent(entry.value)}').join('&')}';
    }
    final RelayResponse response = await _get(path);
    return MetricHistoryPage.fromJson(_decodeData(response.body));
  }

  @override
  Future<List<HeatmapSummary>> heatmaps() async {
    final RelayResponse response = await _get('/heatmaps');
    return _decodeList(response.body)
        .map(
          (dynamic entry) =>
              HeatmapSummary.fromJson(entry as Map<String, dynamic>),
        )
        .toList();
  }

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) async {
    final Map<String, String> query = <String, String>{
      'world': ?world,
      if (centerChunkX != null) 'centerChunkX': centerChunkX.toString(),
      if (centerChunkZ != null) 'centerChunkZ': centerChunkZ.toString(),
      if (radius != null) 'radius': radius.toString(),
    };
    final String suffix = query.isEmpty
        ? ''
        : '?${query.entries.map((MapEntry<String, String> entry) => '${Uri.encodeQueryComponent(entry.key)}=${Uri.encodeQueryComponent(entry.value)}').join('&')}';
    final RelayResponse response = await _get('/heatmaps/$id$suffix');
    return HeatmapGrid.fromJson(_decodeData(response.body));
  }

  @override
  Future<List<OnlinePlayerInfo>> players() async {
    final RelayResponse response = await _get('/players');
    return _decodeList(response.body)
        .map(
          (dynamic entry) =>
              OnlinePlayerInfo.fromJson(entry as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<PlayerTeleportResult> teleportPlayer(
    String playerId, {
    required String worldKey,
    required int blockX,
    required int blockZ,
  }) async {
    final Map<String, dynamic> data = await _postData(
      '/players/${Uri.encodeComponent(playerId)}/teleport',
      <String, Object?>{
        'worldKey': worldKey,
        'blockX': blockX,
        'blockZ': blockZ,
        'confirm': true,
      },
    );
    return PlayerTeleportResult.fromJson(data);
  }

  @override
  Future<List<ControlItem>> features() => _getItems('/features');

  @override
  Future<ControlItem> feature(String id) async {
    final RelayResponse response = await _get('/features/$id');
    return ControlItem.fromJson(_decodeData(response.body));
  }

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) async {
    final Map<String, dynamic> data = await _putData(
      '/features/$id',
      <String, Object?>{'enabled': enabled},
    );
    return ControlItem.fromJson(data);
  }

  @override
  Future<ControlItem> setFeatureConfig(
    String id,
    Map<String, Object?> knobs,
  ) async {
    final Map<String, dynamic> data = await _putData(
      '/features/$id/config',
      knobs,
    );
    return ControlItem.fromJson(data);
  }

  @override
  Future<List<ControlItem>> tweaks() => _getItems('/tweaks');

  @override
  Future<ControlItem> tweak(String id) async {
    final RelayResponse response = await _get('/tweaks/$id');
    return ControlItem.fromJson(_decodeData(response.body));
  }

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) async {
    final Map<String, dynamic> data = await _putData(
      '/tweaks/$id',
      <String, Object?>{'enabled': enabled},
    );
    return ControlItem.fromJson(data);
  }

  @override
  Future<ControlItem> setTweakConfig(
    String id,
    Map<String, Object?> knobs,
  ) async {
    final Map<String, dynamic> data = await _putData(
      '/tweaks/$id/config',
      knobs,
    );
    return ControlItem.fromJson(data);
  }

  @override
  Future<List<WorldSettings>> worlds() async {
    final RelayResponse response = await _get('/worlds');
    return _decodeList(response.body)
        .map(
          (dynamic entry) =>
              WorldSettings.fromJson(entry as Map<String, dynamic>),
        )
        .toList();
  }

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    final Map<String, dynamic> data = await _putData(
      '/worlds/${Uri.encodeComponent(name)}',
      <String, Object?>{
        'budgetMs': ?budgetMs,
        'panicMs': ?panicMs,
        'releaseMs': ?releaseMs,
      },
    );
    return WorldSettings.fromJson(data);
  }

  @override
  Future<List<ActionDescriptor>> actions() async {
    final RelayResponse response = await _get('/actions');
    return _decodeList(response.body)
        .map(
          (dynamic entry) =>
              ActionDescriptor.fromJson(entry as Map<String, dynamic>),
        )
        .toList();
  }

  @override
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  }) async {
    final Map<String, dynamic> data = await _postData(
      '/actions/$id/execute',
      <String, Object?>{'params': params, 'confirm': confirm},
    );
    return ActionTicket.fromJson(data);
  }

  @override
  Future<IncidentStatus> incidents() async {
    final RelayResponse response = await _get('/incidents');
    return IncidentStatus.fromJson(_decodeData(response.body));
  }

  @override
  Future<EnvironmentInfo> environment() async {
    final RelayResponse response = await _get('/environment');
    return EnvironmentInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<ConfigTree> config() async {
    final RelayResponse response = await _get('/config');
    return ConfigTree.fromJson(_decodeData(response.body));
  }

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async {
    final Map<String, dynamic> data = await _putData('/config', changes);
    return ConfigTree.fromJson(data);
  }

  @override
  Future<ConfigTree> applyPreset(String name) async {
    final Map<String, dynamic> data = await _postData(
      '/config/preset/${Uri.encodeComponent(name)}',
      const <String, Object?>{},
    );
    return ConfigTree.fromJson(data);
  }

  @override
  Future<List<String>> logs({int limit = 200}) async {
    final RelayResponse response = await _get('/logs?limit=$limit');
    return _decodeList(
      response.body,
    ).map((dynamic entry) => entry as String).toList();
  }

  @override
  Future<bool> executeConsole(String command) async {
    final Map<String, dynamic> data = await _postData(
      '/console/execute',
      <String, Object?>{'command': command},
    );
    final Object? dispatched = data['dispatched'];
    if (dispatched is! bool) {
      throw const ReactUnavailable(
        'Malformed console response: missing dispatched flag',
      );
    }
    return dispatched;
  }
}
