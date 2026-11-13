import 'dart:async' show TimeoutException;
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../model/action_descriptor.dart';
import '../model/config_tree.dart';
import '../model/control_item.dart';
import '../model/environment_info.dart';
import '../model/heatmap.dart';
import '../model/identity_info.dart';
import '../model/range_history.dart';
import '../model/log_line.dart';
import '../model/incident_status.dart';
import '../model/metric_history.dart';
import '../model/player_navigation.dart';
import '../model/plugin_api_pack.dart';
import '../model/role_info.dart';
import '../model/server_capabilities.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import '../model/server_summary.dart';
import '../model/world_settings.dart';
import '../state/memory_fleet_storage.dart';
import 'monotonic_counter.dart';
import 'react_exceptions.dart';
import 'react_status.dart';

abstract interface class IMetricsClient {
  Future<ServerSnapshot> metrics();
}

abstract interface class IHistoryClient {
  Future<List<MetricHistoryDescriptor>> historyCatalog();
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints,
    int pageSize,
    String? cursor,
  });

  /// One stored range of the chart samplers ([kChartSamplerIds] unless
  /// [ids] narrows it), in one response.
  Future<RangeHistory> metricRange(ChartRange range, {List<String>? ids});
}

abstract interface class IReactClient implements IMetricsClient {
  Future<IdentityInfo> identity();
  Future<void> close();
}

abstract interface class IPingClient {
  Future<ServerCapabilities> ping();
}

abstract interface class IHeatmapClient {
  Future<List<HeatmapSummary>> heatmaps();
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  });
}

abstract interface class IPlayerClient {
  Future<List<OnlinePlayerInfo>> players();
  Future<PlayerTeleportResult> teleportPlayer(
    String playerId, {
    required String worldKey,
    required int blockX,
    required int blockZ,
  });
}

abstract interface class IControlClient {
  Future<List<ControlItem>> features();
  Future<ControlItem> feature(String id);
  Future<ControlItem> setFeatureEnabled(String id, bool enabled);
  Future<ControlItem> setFeatureConfig(String id, Map<String, Object?> knobs);
  Future<List<ControlItem>> tweaks();
  Future<ControlItem> tweak(String id);
  Future<ControlItem> setTweakEnabled(String id, bool enabled);
  Future<ControlItem> setTweakConfig(String id, Map<String, Object?> knobs);
  Future<List<WorldSettings>> worlds();
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  });
}

abstract interface class IActionClient {
  Future<List<ActionDescriptor>> actions();
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  });
}

/// Ticket status for a queued action. Old plugins answer 404.
abstract interface class IActionTicketClient {
  Future<ActionTicketStatus> actionTicket(String ticketId);
}

abstract interface class IIncidentClient {
  Future<IncidentStatus> incidents();
}

abstract interface class IEnvironmentClient {
  Future<EnvironmentInfo> environment();
}

abstract interface class IConfigClient {
  Future<ConfigTree> config();
  Future<ConfigTree> applyConfig(Map<String, Object?> changes);
  Future<ConfigTree> applyPreset(String name);
}

abstract interface class ILogClient {
  Future<List<String>> logs({int limit});
}

/// Sequenced log lines. [after] asks for lines with a higher seq; servers
/// that ignore it return the tail.
abstract interface class ILogLineClient {
  Future<List<LogLine>> logLines({int limit, int? after});
}

abstract interface class IConsoleClient {
  Future<bool> executeConsole(String command);
}

abstract interface class IRoleClient {
  Future<RoleInfo> whoami();
}

/// `GET /summary`: the start page's row for one server.
abstract interface class ISummaryClient {
  Future<ServerSummary> summary();
}

abstract interface class IPluginApiPackClient {
  Future<PluginApiCatalog> pluginApiPacks();
  Future<PluginApiValidationResult> validatePluginApiPack(String content);
  Future<PluginApiPack> installPluginApiPack(String id, String content);
  Future<PluginApiCatalog> removePluginApiPack(String id);
}

abstract interface class IOperateClient
    implements
        IActionClient,
        IIncidentClient,
        IEnvironmentClient,
        IConfigClient,
        ILogClient {}

class ReactClient
    implements
        IReactClient,
        IPingClient,
        IHeatmapClient,
        IPlayerClient,
        IControlClient,
        IOperateClient,
        IActionTicketClient,
        ILogLineClient,
        IConsoleClient,
        IRoleClient,
        ISummaryClient,
        IHistoryClient,
        IPluginApiPackClient {
  final ServerCredential cred;
  final http.Client _http;
  final MonotonicCounter _counter;
  final Future<void> Function(Duration) _sleep;
  bool _closed = false;

  static const Duration _timeout = Duration(seconds: 2);

  ReactClient(
    this.cred, {
    http.Client? client,
    MonotonicCounter? counter,
    Future<void> Function(Duration)? sleep,
  }) : _http = client ?? http.Client(),
       _counter = counter ?? MonotonicCounter(InMemoryFleetStorage()),
       _sleep = sleep ?? ((Duration d) => Future<void>.delayed(d));

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _http.close();
  }

  void _ensureOpen() {
    if (_closed) throw const ReactUnavailable('Client closed');
  }

  /// Sends one request and maps every non-2xx status to a typed error. A GET
  /// answered 429 is retried at most twice; a mutation answered with a
  /// counter conflict is re-based and retried once.
  Future<Map<String, dynamic>?> _send(
    String method,
    String path, {
    Map<String, Object?>? body,
    bool authenticated = true,
  }) async {
    final bool mutating = method != 'GET';
    int rateLimitRetries = 0;
    bool rebased = false;
    while (true) {
      final http.Response response = await _transport(
        method,
        path,
        body: body,
        authenticated: authenticated,
        mutating: mutating,
      );
      final Map<String, dynamic>? decoded = _tryDecode(response.body);
      final Exception? error = reactErrorFor(
        response.statusCode,
        decoded,
        retryAfter: parseRetryAfter(response.headers['retry-after']),
      );
      if (error == null) {
        if (decoded == null) {
          throw const ReactUnavailable(
            'Malformed response: expected an object',
          );
        }
        return decoded;
      }
      if (error is ReactCounterConflict && mutating && !rebased) {
        rebased = true;
        _counter.rebase(cred.id, error.lastCounter);
        continue;
      }
      if (error is ReactRateLimited &&
          !mutating &&
          rateLimitRetries < kMaxRateLimitRetries) {
        rateLimitRetries++;
        await _sleep(rateLimitDelay(error.retryAfter));
        _ensureOpen();
        continue;
      }
      throw error;
    }
  }

  Future<http.Response> _transport(
    String method,
    String path, {
    required Map<String, Object?>? body,
    required bool authenticated,
    required bool mutating,
  }) async {
    _ensureOpen();
    final Uri uri = cred.directEndpoint('api/v1$path');
    final Map<String, String> headers = <String, String>{
      if (authenticated) 'Authorization': 'Bearer ${cred.bearer}',
      if (authenticated) 'Content-Type': 'application/json',
      if (mutating) 'X-React-Counter': _counter.next(cred.id).toString(),
    };
    try {
      final Future<http.Response> pending = switch (method) {
        'GET' => _http.get(uri, headers: headers),
        'PUT' => _http.put(uri, headers: headers, body: jsonEncode(body)),
        'POST' => _http.post(uri, headers: headers, body: jsonEncode(body)),
        'DELETE' => _http.delete(uri, headers: headers),
        _ => throw ArgumentError.value(method, 'method'),
      };
      final http.Response response = await pending.timeout(_timeout);
      _ensureOpen();
      return response;
    } on ReactUnavailable {
      rethrow;
    } on http.ClientException catch (e) {
      throw ReactUnavailable(e.message);
    } on TimeoutException catch (e) {
      throw ReactUnavailable(e.message ?? 'Request timed out');
    } on Exception catch (e) {
      throw ReactUnavailable(e.toString());
    }
  }

  Map<String, dynamic>? _tryDecode(String body) {
    try {
      final Object? decoded = jsonDecode(body);
      return decoded is Map<String, dynamic> ? decoded : null;
    } on FormatException {
      return null;
    }
  }

  Future<Map<String, dynamic>> _getData(
    String path, {
    bool authenticated = true,
  }) async =>
      _dataObject(await _send('GET', path, authenticated: authenticated));

  Future<List<dynamic>> _getList(String path) async {
    final Object? data = (await _send('GET', path))?['data'];
    if (data is! List<dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data list');
    }
    return data;
  }

  Map<String, dynamic> _dataObject(Map<String, dynamic>? root) {
    final Object? data = root?['data'];
    if (data is! Map<String, dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data object');
    }
    return data;
  }

  Future<Map<String, dynamic>> _putData(
    String path,
    Map<String, Object?> body,
  ) async => _dataObject(await _send('PUT', path, body: body));

  Future<Map<String, dynamic>> _postData(
    String path,
    Map<String, Object?> body,
  ) async => _dataObject(await _send('POST', path, body: body));

  Future<Map<String, dynamic>> _deleteData(String path) async =>
      _dataObject(await _send('DELETE', path));

  Future<List<ControlItem>> _getItems(String path) async =>
      (await _getList(path))
          .map((dynamic e) => ControlItem.fromJson(e as Map<String, dynamic>))
          .toList();

  static String _query(Map<String, String> params) => params.entries
      .map(
        (MapEntry<String, String> e) =>
            '${Uri.encodeQueryComponent(e.key)}=${Uri.encodeQueryComponent(e.value)}',
      )
      .join('&');

  @override
  Future<ServerCapabilities> ping() async {
    final Map<String, dynamic> data = await _getData(
      '/ping',
      authenticated: false,
    );
    try {
      return ServerCapabilities.fromJson(data);
    } on FormatException catch (error) {
      throw ReactUnavailable(error.message);
    }
  }

  @override
  Future<IdentityInfo> identity() async =>
      IdentityInfo.fromJson(await _getData('/identity'));

  @override
  Future<ServerSummary> summary() async =>
      ServerSummary.fromJson(await _getData('/summary'));

  @override
  Future<RoleInfo> whoami() async =>
      RoleInfo.fromJson(await _getData('/whoami'));

  @override
  Future<ServerSnapshot> metrics() async {
    final Map<String, dynamic>? root = await _send('GET', '/metrics?compact=1');
    try {
      return ServerSnapshot.fromJson(root!);
    } on TypeError {
      throw const ReactUnavailable('Malformed metrics response');
    }
  }

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      (await _getList('/metrics/catalog'))
          .map(
            (dynamic value) =>
                MetricHistoryDescriptor.fromJson(value as Map<String, dynamic>),
          )
          .toList();

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
      path = '/metrics/history?${_query(<String, String>{'cursor': cursor})}';
    } else {
      if (ids == null || ids.isEmpty || from == null || to == null) {
        throw ArgumentError('ids, from, and to are required without a cursor');
      }
      path =
          '/metrics/history?${_query(<String, String>{'ids': ids.join(','), 'from': from.millisecondsSinceEpoch.toString(), 'to': to.millisecondsSinceEpoch.toString(), 'maxPoints': maxPoints.toString(), 'pageSize': pageSize.toString()})}';
    }
    return MetricHistoryPage.fromJson(await _getData(path));
  }

  @override
  Future<RangeHistory> metricRange(
    ChartRange range, {
    List<String>? ids,
  }) async => RangeHistory.fromJson(
    await _getData(
      '/metrics/range?${_query(<String, String>{'range': range.id, if (ids != null) 'ids': ids.join(',')})}',
    ),
  );

  @override
  Future<List<HeatmapSummary>> heatmaps() async => (await _getList('/heatmaps'))
      .map((dynamic e) => HeatmapSummary.fromJson(e as Map<String, dynamic>))
      .toList();

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) async {
    final Map<String, String> params = <String, String>{
      'world': ?world,
      if (centerChunkX != null) 'centerChunkX': centerChunkX.toString(),
      if (centerChunkZ != null) 'centerChunkZ': centerChunkZ.toString(),
      if (radius != null) 'radius': radius.toString(),
    };
    final String query = params.isEmpty ? '' : '?${_query(params)}';
    return HeatmapGrid.fromJson(await _getData('/heatmaps/$id$query'));
  }

  @override
  Future<List<OnlinePlayerInfo>> players() async => (await _getList('/players'))
      .map(
        (dynamic value) =>
            OnlinePlayerInfo.fromJson(value as Map<String, dynamic>),
      )
      .toList(growable: false);

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
  Future<ControlItem> feature(String id) async =>
      ControlItem.fromJson(await _getData('/features/$id'));

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) async =>
      ControlItem.fromJson(
        await _putData('/features/$id', <String, Object?>{'enabled': enabled}),
      );

  @override
  Future<ControlItem> setFeatureConfig(
    String id,
    Map<String, Object?> knobs,
  ) async =>
      ControlItem.fromJson(await _putData('/features/$id/config', knobs));

  @override
  Future<List<ControlItem>> tweaks() => _getItems('/tweaks');

  @override
  Future<ControlItem> tweak(String id) async =>
      ControlItem.fromJson(await _getData('/tweaks/$id'));

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) async =>
      ControlItem.fromJson(
        await _putData('/tweaks/$id', <String, Object?>{'enabled': enabled}),
      );

  @override
  Future<ControlItem> setTweakConfig(
    String id,
    Map<String, Object?> knobs,
  ) async => ControlItem.fromJson(await _putData('/tweaks/$id/config', knobs));

  @override
  Future<List<WorldSettings>> worlds() async =>
      (await _getList('/worlds'))
          .map((dynamic e) => WorldSettings.fromJson(e as Map<String, dynamic>))
          .toList();

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    final Map<String, dynamic> data = await _putData(
      '/worlds/$name',
      <String, Object?>{
        'budgetMs': ?budgetMs,
        'panicMs': ?panicMs,
        'releaseMs': ?releaseMs,
      },
    );
    return WorldSettings.fromJson(data);
  }

  @override
  Future<List<ActionDescriptor>> actions() async => (await _getList('/actions'))
      .map((dynamic e) => ActionDescriptor.fromJson(e as Map<String, dynamic>))
      .toList();

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
  Future<ActionTicketStatus> actionTicket(String ticketId) async {
    final Map<String, dynamic>? root = await _send(
      'GET',
      '/actions/tickets/${Uri.encodeComponent(ticketId)}',
    );
    final Object? data = root?['data'];
    return ActionTicketStatus.fromJson(
      data is Map<String, dynamic> ? data : root!,
    );
  }

  @override
  Future<IncidentStatus> incidents() async =>
      IncidentStatus.fromJson(await _getData('/incidents'));

  @override
  Future<EnvironmentInfo> environment() async =>
      EnvironmentInfo.fromJson(await _getData('/environment'));

  @override
  Future<ConfigTree> config() async =>
      ConfigTree.fromJson(await _getData('/config'));

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async =>
      ConfigTree.fromJson(await _putData('/config', changes));

  @override
  Future<ConfigTree> applyPreset(String name) async => ConfigTree.fromJson(
    await _postData('/config/preset/$name', const <String, Object?>{}),
  );

  @override
  Future<List<String>> logs({int limit = 200}) async =>
      (await logLines(limit: limit)).map((LogLine l) => l.text).toList();

  @override
  Future<List<LogLine>> logLines({int limit = 200, int? after}) async {
    final Map<String, String> params = <String, String>{
      'limit': limit.toString(),
      if (after != null) 'after': after.toString(),
    };
    final Map<String, dynamic>? root = await _send(
      'GET',
      '/logs?${_query(params)}',
    );
    final Object? data = root?['data'];
    if (data is! List<dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data list');
    }
    return LogLine.withTrailingSeq(
      LogLine.listFromJson(data),
      root?['lastSeq'],
    );
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

  @override
  Future<PluginApiCatalog> pluginApiPacks() async =>
      PluginApiCatalog.fromJson(await _getData('/plugin-api-packs'));

  @override
  Future<PluginApiValidationResult> validatePluginApiPack(
    String content,
  ) async => PluginApiValidationResult.fromJson(
    await _postData('/plugin-api-packs/validate', <String, Object?>{
      'content': content,
    }),
  );

  @override
  Future<PluginApiPack> installPluginApiPack(String id, String content) async =>
      PluginApiPack.fromJson(
        await _putData(
          '/plugin-api-packs/${Uri.encodeComponent(id)}',
          <String, Object?>{'content': content},
        ),
      );

  @override
  Future<PluginApiCatalog> removePluginApiPack(String id) async =>
      PluginApiCatalog.fromJson(
        await _deleteData('/plugin-api-packs/${Uri.encodeComponent(id)}'),
      );
}
