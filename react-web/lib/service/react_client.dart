import 'dart:async' show TimeoutException;
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../model/action_descriptor.dart';
import '../model/config_tree.dart';
import '../model/control_item.dart';
import '../model/environment_info.dart';
import '../model/heatmap.dart';
import '../model/identity_info.dart';
import '../model/incident_status.dart';
import '../model/role_info.dart';
import '../model/sampler_sample.dart';
import '../model/server_capabilities.dart';
import '../model/server_credential.dart';
import '../model/server_snapshot.dart';
import '../model/world_settings.dart';
import '../state/memory_fleet_storage.dart';
import 'monotonic_counter.dart';
import 'react_exceptions.dart';

abstract interface class IMetricsClient {
  Future<ServerSnapshot> metrics();
}

abstract interface class IReactClient implements IMetricsClient {
  Future<IdentityInfo> identity();
}

abstract interface class IPingClient {
  Future<ServerCapabilities> ping();
}

abstract interface class IHeatmapClient {
  Future<List<HeatmapSummary>> heatmaps();
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerX,
    int? centerZ,
    int? radius,
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

abstract interface class IConsoleClient {
  Future<bool> executeConsole(String command);
}

abstract interface class IRoleClient {
  Future<RoleInfo> whoami();
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
        IControlClient,
        IOperateClient,
        IConsoleClient,
        IRoleClient {
  final ServerCredential cred;
  final http.Client _http;
  final MonotonicCounter _counter;

  ReactClient(this.cred, {http.Client? client, MonotonicCounter? counter})
    : _http = client ?? http.Client(),
      _counter = counter ?? MonotonicCounter(InMemoryFleetStorage());

  Map<String, String> get _headers => <String, String>{
    'Authorization': 'Bearer ${cred.bearer}',
    'Content-Type': 'application/json',
  };

  Future<http.Response> _get(String path, {bool authenticated = true}) async {
    final Uri uri = cred.directEndpoint('api/v1$path');
    try {
      final http.Response response = await _http
          .get(
            uri,
            headers: authenticated ? _headers : const <String, String>{},
          )
          .timeout(const Duration(seconds: 2));
      if (response.statusCode == 401) {
        throw const ReactAuthException();
      }
      return response;
    } on ReactAuthException {
      rethrow;
    } on http.ClientException catch (e) {
      throw ReactUnavailable(e.message);
    } on TimeoutException catch (e) {
      throw ReactUnavailable(e.message ?? 'Request timed out');
    } on Exception catch (e) {
      throw ReactUnavailable(e.toString());
    }
  }

  Map<String, dynamic> _decodeJson(String body) {
    try {
      final Object? decoded = jsonDecode(body);
      if (decoded is! Map<String, dynamic>) {
        throw const ReactUnavailable('Malformed response: expected an object');
      }
      return decoded;
    } on FormatException catch (e) {
      throw ReactUnavailable(e.message);
    }
  }

  Map<String, dynamic> _decodeData(String body) {
    final Map<String, dynamic> root = _decodeJson(body);
    final Object? data = root['data'];
    if (data is! Map<String, dynamic>) {
      throw const ReactUnavailable('Malformed response: missing data object');
    }
    return data;
  }

  String _errorMessage(String body) {
    try {
      final Object? decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        final Object? error = decoded['error'];
        if (error is Map<String, dynamic>) {
          final Object? message = error['message'];
          if (message is String) {
            return message;
          }
        }
      }
      return 'Request failed';
    } on Object {
      return 'Request failed';
    }
  }

  Future<List<ControlItem>> _getItems(String path) async {
    final http.Response response = await _get(path);
    final Map<String, dynamic> root = _decodeJson(response.body);
    final Object? data = root['data'];
    if (data is! List) {
      throw const ReactUnavailable('Malformed response: missing data list');
    }
    return data
        .map((dynamic e) => ControlItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Map<String, dynamic>> _putData(
    String path,
    Map<String, Object?> body,
  ) async {
    final Uri uri = cred.directEndpoint('api/v1$path');
    final Map<String, String> headers = <String, String>{
      'Authorization': 'Bearer ${cred.bearer}',
      'Content-Type': 'application/json',
      'X-React-Counter': _counter.next(cred.id).toString(),
    };
    try {
      final http.Response response = await _http
          .put(uri, headers: headers, body: jsonEncode(body))
          .timeout(const Duration(seconds: 2));
      switch (response.statusCode) {
        case 200:
          return _decodeData(response.body);
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
    } on ReactAuthException {
      rethrow;
    } on ReactBadRequest {
      rethrow;
    } on ReactForbidden {
      rethrow;
    } on ReactNotFound {
      rethrow;
    } on ReactConflict {
      rethrow;
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

  Future<Map<String, dynamic>> _postData(
    String path,
    Map<String, Object?> body,
  ) async {
    final Uri uri = cred.directEndpoint('api/v1$path');
    final Map<String, String> headers = <String, String>{
      'Authorization': 'Bearer ${cred.bearer}',
      'Content-Type': 'application/json',
      'X-React-Counter': _counter.next(cred.id).toString(),
    };
    try {
      final http.Response response = await _http
          .post(uri, headers: headers, body: jsonEncode(body))
          .timeout(const Duration(seconds: 2));
      switch (response.statusCode) {
        case 200:
        case 202:
          return _decodeData(response.body);
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
    } on ReactAuthException {
      rethrow;
    } on ReactBadRequest {
      rethrow;
    } on ReactForbidden {
      rethrow;
    } on ReactNotFound {
      rethrow;
    } on ReactConflict {
      rethrow;
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

  @override
  Future<ServerCapabilities> ping() async {
    final http.Response response = await _get('/ping', authenticated: false);
    if (response.statusCode != 200) {
      throw ReactUnavailable(_errorMessage(response.body));
    }
    try {
      return ServerCapabilities.fromJson(_decodeData(response.body));
    } on ReactUnavailable {
      rethrow;
    } on FormatException catch (error) {
      throw ReactUnavailable(error.message);
    }
  }

  @override
  Future<IdentityInfo> identity() async {
    final http.Response response = await _get('/identity');
    return IdentityInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<RoleInfo> whoami() async {
    final http.Response response = await _get('/whoami');
    return RoleInfo.fromJson(_decodeData(response.body));
  }

  @override
  Future<ServerSnapshot> metrics() async {
    final http.Response response = await _get('/metrics');
    return ServerSnapshot.fromJson(_decodeJson(response.body));
  }

  Future<SamplerSample> history(String id) async {
    final http.Response response = await _get('/metrics/$id/history');
    return SamplerSample.fromJson(_decodeData(response.body));
  }

  @override
  Future<List<HeatmapSummary>> heatmaps() async {
    final http.Response r = await _get('/heatmaps');
    final Map<String, dynamic> root = _decodeJson(r.body);
    final Object? data = root['data'];
    if (data is! List) {
      throw const ReactUnavailable('Malformed heatmaps response');
    }
    return data
        .map((dynamic e) => HeatmapSummary.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerX,
    int? centerZ,
    int? radius,
  }) async {
    final Map<String, String> params = <String, String>{};
    if (world != null) params['world'] = world;
    if (centerX != null) params['centerX'] = centerX.toString();
    if (centerZ != null) params['centerZ'] = centerZ.toString();
    if (radius != null) params['radius'] = radius.toString();
    final String query = params.isEmpty
        ? ''
        : '?${params.entries.map((MapEntry<String, String> e) => '${Uri.encodeQueryComponent(e.key)}=${Uri.encodeQueryComponent(e.value)}').join('&')}';
    final String path = '/heatmaps/$id$query';
    final http.Response r = await _get(path);
    if (r.statusCode == 404) {
      throw ReactUnavailable('Unknown heatmap: $id');
    }
    return HeatmapGrid.fromJson(_decodeData(r.body));
  }

  @override
  Future<List<ControlItem>> features() => _getItems('/features');

  @override
  Future<ControlItem> feature(String id) async {
    final http.Response response = await _get('/features/$id');
    if (response.statusCode == 404) {
      throw ReactNotFound(_errorMessage(response.body));
    }
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
    final http.Response response = await _get('/tweaks/$id');
    if (response.statusCode == 404) {
      throw ReactNotFound(_errorMessage(response.body));
    }
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
    final http.Response response = await _get('/worlds');
    final Map<String, dynamic> root = _decodeJson(response.body);
    final Object? data = root['data'];
    if (data is! List) {
      throw const ReactUnavailable('Malformed worlds response');
    }
    return data
        .map((dynamic e) => WorldSettings.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    final Map<String, Object?> body = <String, Object?>{
      'budgetMs': ?budgetMs,
      'panicMs': ?panicMs,
      'releaseMs': ?releaseMs,
    };
    final Map<String, dynamic> data = await _putData('/worlds/$name', body);
    return WorldSettings.fromJson(data);
  }

  @override
  Future<List<ActionDescriptor>> actions() async {
    final http.Response r = await _get('/actions');
    final Map<String, dynamic> root = _decodeJson(r.body);
    final Object? data = root['data'];
    if (data is! List) {
      throw const ReactUnavailable('Malformed actions response');
    }
    return data
        .map(
          (dynamic e) => ActionDescriptor.fromJson(e as Map<String, dynamic>),
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
    final http.Response r = await _get('/incidents');
    return IncidentStatus.fromJson(_decodeData(r.body));
  }

  @override
  Future<EnvironmentInfo> environment() async {
    final http.Response r = await _get('/environment');
    return EnvironmentInfo.fromJson(_decodeData(r.body));
  }

  @override
  Future<ConfigTree> config() async {
    final http.Response r = await _get('/config');
    return ConfigTree.fromJson(_decodeData(r.body));
  }

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async {
    final Map<String, dynamic> data = await _putData('/config', changes);
    return ConfigTree.fromJson(data);
  }

  @override
  Future<ConfigTree> applyPreset(String name) async {
    final Map<String, dynamic> data = await _postData(
      '/config/preset/$name',
      const <String, Object?>{},
    );
    return ConfigTree.fromJson(data);
  }

  @override
  Future<List<String>> logs({int limit = 200}) async {
    final http.Response r = await _get('/logs?limit=$limit');
    final Map<String, dynamic> root = _decodeJson(r.body);
    final Object? data = root['data'];
    if (data is! List) {
      throw const ReactUnavailable('Malformed logs response');
    }
    return data.map((dynamic e) => e as String).toList();
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
