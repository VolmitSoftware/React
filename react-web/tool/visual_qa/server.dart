import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'fixtures.dart';

final class VisualQaServer {
  static const int defaultPort = 19696;
  static const String defaultAllowedOrigin = 'http://127.0.0.1:8080';

  final HttpServer _server;
  final String allowedOrigin;
  final Map<String, _VisualQaSession> _sessions;
  final Map<WebSocket, VisualQaProfile> _metricsSockets =
      <WebSocket, VisualQaProfile>{};
  final Map<WebSocket, VisualQaProfile> _logSockets =
      <WebSocket, VisualQaProfile>{};
  late final StreamSubscription<HttpRequest> _requests;
  late final Timer _metricsPulse;
  bool _closed = false;

  VisualQaServer._(this._server, this.allowedOrigin)
    : _sessions = <String, _VisualQaSession>{
        for (final VisualQaProfile profile in VisualQaProfile.values)
          profile.bearer: _VisualQaSession(profile),
      };

  static Future<VisualQaServer> start({
    int port = defaultPort,
    String allowedOrigin = defaultAllowedOrigin,
  }) async {
    final HttpServer httpServer = await HttpServer.bind(
      InternetAddress.loopbackIPv4,
      port,
      shared: false,
    );
    final VisualQaServer server = VisualQaServer._(httpServer, allowedOrigin);
    server._requests = httpServer.listen(server._handleRequest);
    server._metricsPulse = Timer.periodic(
      const Duration(seconds: 1),
      (Timer _) => server._broadcastMetrics(),
    );
    return server;
  }

  int get port => _server.port;

  InternetAddress get address => _server.address;

  Uri get baseUri => Uri.parse('http://127.0.0.1:$port');

  String pairingCode(VisualQaProfile profile) => profile.pairingCode(port);

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _metricsPulse.cancel();
    final List<WebSocket> sockets = <WebSocket>[
      ..._metricsSockets.keys,
      ..._logSockets.keys,
    ];
    _metricsSockets.clear();
    _logSockets.clear();
    for (final WebSocket socket in sockets) {
      await socket.close(WebSocketStatus.goingAway, 'Visual QA shutdown');
    }
    await _server.close(force: true);
    await _requests.cancel();
  }

  Future<void> _handleRequest(HttpRequest request) async {
    try {
      final String? origin = request.headers.value('Origin');
      if (origin != null && origin != allowedOrigin) {
        await _writeError(request, HttpStatus.forbidden, 'Origin not allowed');
        return;
      }
      if (origin == allowedOrigin) {
        _applyCors(request.response);
      }
      if (request.method == 'OPTIONS') {
        request.response.statusCode = HttpStatus.noContent;
        await request.response.close();
        return;
      }
      if (WebSocketTransformer.isUpgradeRequest(request)) {
        await _handleWebSocket(request);
        return;
      }
      if (request.uri.path == '/__qa/health') {
        await _writeJson(request, <String, Object?>{
          'status': 'ready',
          'address': _server.address.address,
          'port': port,
          'allowedOrigin': allowedOrigin,
        });
        return;
      }
      if (request.uri.path == '/__qa/codes') {
        await _writeJson(request, <String, Object?>{
          'servers': <Map<String, Object?>>[
            for (final VisualQaProfile profile in VisualQaProfile.values)
              <String, Object?>{
                'name': profile.label,
                'bearer': profile.bearer,
                'pairingCode': pairingCode(profile),
              },
          ],
        });
        return;
      }

      final List<String> segments = request.uri.pathSegments;
      if (segments.length < 3 || segments[0] != 'api' || segments[1] != 'v1') {
        await _writeError(request, HttpStatus.notFound, 'Route not found');
        return;
      }
      final _VisualQaSession? session = _sessionForAuthorization(request);
      if (session == null) {
        await _writeError(request, HttpStatus.unauthorized, 'Invalid QA token');
        return;
      }
      await _handleApi(request, session, segments.sublist(2));
    } on FormatException catch (error) {
      await _writeError(request, HttpStatus.badRequest, error.message);
    } on Object catch (error, stackTrace) {
      stderr.writeln('Visual QA request failed: $error');
      stderr.writeln(stackTrace);
      await _writeError(
        request,
        HttpStatus.internalServerError,
        'Visual QA request failed',
      );
    }
  }

  Future<void> _handleApi(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    final String resource = segments.first;
    if (resource == 'ping' && segments.length == 1) {
      await _requireGetAndWrite(
        request,
        VisualQaFixtures.capabilities(session.profile),
      );
      return;
    }
    if (resource == 'identity' && segments.length == 1) {
      await _requireGetAndWrite(
        request,
        VisualQaFixtures.identity(session.profile),
      );
      return;
    }
    if (resource == 'whoami' && segments.length == 1) {
      await _requireGetAndWrite(
        request,
        VisualQaFixtures.role(session.profile),
      );
      return;
    }
    if (resource == 'metrics') {
      await _handleMetrics(request, session, segments);
      return;
    }
    if (resource == 'heatmaps') {
      await _handleHeatmaps(request, session, segments);
      return;
    }
    if (resource == 'features') {
      await _handleControls(request, session.features, segments);
      return;
    }
    if (resource == 'tweaks') {
      await _handleControls(request, session.tweaks, segments);
      return;
    }
    if (resource == 'worlds') {
      await _handleWorlds(request, session, segments);
      return;
    }
    if (resource == 'actions') {
      await _handleActions(request, session, segments);
      return;
    }
    if (resource == 'incidents' && segments.length == 1) {
      await _requireGetAndWrite(
        request,
        VisualQaFixtures.incidents(session.profile),
      );
      return;
    }
    if (resource == 'environment' && segments.length == 1) {
      await _requireGetAndWrite(
        request,
        VisualQaFixtures.environment(session.profile),
      );
      return;
    }
    if (resource == 'config') {
      await _handleConfig(request, session, segments);
      return;
    }
    if (resource == 'logs' && segments.length == 1) {
      if (!await _isMethod(request, 'GET')) return;
      final int requestedLimit =
          int.tryParse(request.uri.queryParameters['limit'] ?? '') ?? 200;
      final int start = (session.logs.length - requestedLimit).clamp(
        0,
        session.logs.length,
      );
      await _writeData(request, session.logs.sublist(start));
      return;
    }
    if (resource == 'console' &&
        segments.length == 2 &&
        segments[1] == 'execute') {
      if (!await _isMethod(request, 'POST')) return;
      if (!await _requireCounter(request)) return;
      final Map<String, dynamic> body = await _readBody(request);
      final Object? command = body['command'];
      if (command is! String || command.trim().isEmpty) {
        await _writeError(
          request,
          HttpStatus.badRequest,
          'Command is required',
        );
        return;
      }
      final String line =
          '[INFO] QA accepted console command: ${command.trim()}';
      session.logs.add(line);
      _broadcastLog(session.profile, line);
      await _writeData(request, <String, dynamic>{'dispatched': true});
      return;
    }
    await _writeError(request, HttpStatus.notFound, 'Route not found');
  }

  Future<void> _handleMetrics(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    if (!await _isMethod(request, 'GET')) return;
    final Map<String, dynamic> metrics = VisualQaFixtures.metrics(
      session.profile,
    );
    if (segments.length == 1) {
      await _writeJson(request, metrics);
      return;
    }
    if (segments.length == 3 && segments[2] == 'history') {
      final List<dynamic> data = metrics['data'] as List<dynamic>;
      Map<String, dynamic>? match;
      for (final dynamic item in data) {
        final Map<String, dynamic> sample = item as Map<String, dynamic>;
        if (sample['id'] == segments[1]) {
          match = sample;
          break;
        }
      }
      if (match == null) {
        await _writeError(
          request,
          HttpStatus.notFound,
          'Unknown sampler: ${segments[1]}',
        );
        return;
      }
      await _writeData(request, match);
      return;
    }
    await _writeError(request, HttpStatus.notFound, 'Metric route not found');
  }

  Future<void> _handleHeatmaps(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    if (!await _isMethod(request, 'GET')) return;
    if (segments.length == 1) {
      await _writeData(request, VisualQaFixtures.heatmapSummaries());
      return;
    }
    if (segments.length == 2) {
      final Map<String, dynamic>? heatmap = VisualQaFixtures.heatmap(
        session.profile,
        segments[1],
      );
      if (heatmap == null) {
        await _writeError(
          request,
          HttpStatus.notFound,
          'Unknown heatmap: ${segments[1]}',
        );
        return;
      }
      await _writeData(request, heatmap);
      return;
    }
    await _writeError(request, HttpStatus.notFound, 'Heatmap route not found');
  }

  Future<void> _handleControls(
    HttpRequest request,
    List<Map<String, dynamic>> controls,
    List<String> segments,
  ) async {
    if (segments.length == 1) {
      if (!await _isMethod(request, 'GET')) return;
      await _writeData(request, controls);
      return;
    }

    Map<String, dynamic>? control;
    for (final Map<String, dynamic> candidate in controls) {
      if (candidate['id'] == segments[1]) {
        control = candidate;
        break;
      }
    }
    if (control == null) {
      await _writeError(
        request,
        HttpStatus.notFound,
        'Unknown control: ${segments[1]}',
      );
      return;
    }

    if (segments.length == 2 && request.method == 'GET') {
      await _writeData(request, control);
      return;
    }
    if (segments.length == 2 && request.method == 'PUT') {
      if (!await _requireCounter(request)) return;
      final Map<String, dynamic> body = await _readBody(request);
      final Object? enabled = body['enabled'];
      if (enabled is! bool) {
        await _writeError(
          request,
          HttpStatus.badRequest,
          'Enabled must be a boolean',
        );
        return;
      }
      control['enabled'] = enabled;
      await _writeData(request, control);
      return;
    }
    if (segments.length == 3 &&
        segments[2] == 'config' &&
        request.method == 'PUT') {
      if (!await _requireCounter(request)) return;
      final Map<String, dynamic> body = await _readBody(request);
      final List<dynamic> knobs = control['knobs'] as List<dynamic>;
      for (final dynamic item in knobs) {
        final Map<String, dynamic> knob = item as Map<String, dynamic>;
        final String key = knob['key'] as String;
        if (body.containsKey(key)) {
          knob['value'] = body[key];
        }
      }
      await _writeData(request, control);
      return;
    }
    await _writeError(
      request,
      HttpStatus.methodNotAllowed,
      'Method not allowed',
    );
  }

  Future<void> _handleWorlds(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    if (segments.length == 1) {
      if (!await _isMethod(request, 'GET')) return;
      await _writeData(request, session.worlds);
      return;
    }
    if (segments.length != 2 || request.method != 'PUT') {
      await _writeError(
        request,
        HttpStatus.methodNotAllowed,
        'Method not allowed',
      );
      return;
    }
    if (!await _requireCounter(request)) return;

    Map<String, dynamic>? world;
    for (final Map<String, dynamic> candidate in session.worlds) {
      if (candidate['name'] == segments[1]) {
        world = candidate;
        break;
      }
    }
    if (world == null) {
      await _writeError(
        request,
        HttpStatus.notFound,
        'Unknown world: ${segments[1]}',
      );
      return;
    }
    final Map<String, dynamic> body = await _readBody(request);
    for (final String key in <String>['budgetMs', 'panicMs', 'releaseMs']) {
      final Object? value = body[key];
      if (value is num) {
        world[key] = value.toDouble();
      }
    }
    await _writeData(request, world);
  }

  Future<void> _handleActions(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    if (segments.length == 1) {
      if (!await _isMethod(request, 'GET')) return;
      await _writeData(request, VisualQaFixtures.actions());
      return;
    }
    if (segments.length != 3 ||
        segments[2] != 'execute' ||
        request.method != 'POST') {
      await _writeError(
        request,
        HttpStatus.methodNotAllowed,
        'Method not allowed',
      );
      return;
    }
    if (!await _requireCounter(request)) return;
    final List<Map<String, dynamic>> actions = VisualQaFixtures.actions();
    final bool known = actions.any(
      (Map<String, dynamic> action) => action['id'] == segments[1],
    );
    if (!known) {
      await _writeError(
        request,
        HttpStatus.notFound,
        'Unknown action: ${segments[1]}',
      );
      return;
    }
    await _readBody(request);
    final String ticketId = '${session.profile.tokenId}-ticket-001';
    final String line = '[INFO] QA queued action ${segments[1]} as $ticketId';
    session.logs.add(line);
    _broadcastLog(session.profile, line);
    await _writeData(request, <String, dynamic>{
      'ticketId': ticketId,
      'status': 'queued',
    }, statusCode: HttpStatus.accepted);
  }

  Future<void> _handleConfig(
    HttpRequest request,
    _VisualQaSession session,
    List<String> segments,
  ) async {
    if (segments.length == 1 && request.method == 'GET') {
      await _writeData(request, session.config);
      return;
    }
    if (segments.length == 1 && request.method == 'PUT') {
      if (!await _requireCounter(request)) return;
      final Map<String, dynamic> changes = await _readBody(request);
      _applyConfigChanges(session.config, changes);
      await _writeData(request, session.config);
      return;
    }
    if (segments.length == 3 &&
        segments[1] == 'preset' &&
        request.method == 'POST') {
      if (!await _requireCounter(request)) return;
      await _readBody(request);
      final String preset = segments[2];
      final Map<String, Object?>? values = switch (preset) {
        'off' => <String, Object?>{'incident-mode-enabled': false},
        'light' => <String, Object?>{
          'tick-budget-ms': 48.0,
          'incident-mode-enabled': true,
        },
        'balanced' => <String, Object?>{
          'tick-budget-ms': 45.0,
          'incident-mode-enabled': true,
        },
        'high' => <String, Object?>{
          'tick-budget-ms': 40.0,
          'incident-mode-enabled': true,
        },
        _ => null,
      };
      if (values == null) {
        await _writeError(
          request,
          HttpStatus.badRequest,
          'Unknown preset: $preset',
        );
        return;
      }
      _applyConfigChanges(session.config, values);
      await _writeData(request, session.config);
      return;
    }
    await _writeError(
      request,
      HttpStatus.methodNotAllowed,
      'Method not allowed',
    );
  }

  Future<void> _handleWebSocket(HttpRequest request) async {
    final String path = request.uri.path;
    if (path != '/ws/metrics' && path != '/ws/logs') {
      await _writeError(request, HttpStatus.notFound, 'Socket not found');
      return;
    }
    final String? token = request.uri.queryParameters['token'];
    final _VisualQaSession? session = token == null ? null : _sessions[token];
    if (session == null) {
      await _writeError(request, HttpStatus.unauthorized, 'Invalid QA token');
      return;
    }
    final WebSocket socket = await WebSocketTransformer.upgrade(request);
    if (path == '/ws/metrics') {
      _metricsSockets[socket] = session.profile;
      socket.add(jsonEncode(VisualQaFixtures.metrics(session.profile)));
    } else {
      _logSockets[socket] = session.profile;
      for (final String line in session.logs) {
        socket.add(jsonEncode(<String, Object?>{'type': 'log', 'line': line}));
      }
    }
    socket.listen(
      (dynamic _) {},
      onError: (Object _) => _removeSocket(socket),
      onDone: () => _removeSocket(socket),
      cancelOnError: true,
    );
  }

  _VisualQaSession? _sessionForAuthorization(HttpRequest request) {
    final String? authorization = request.headers.value(
      HttpHeaders.authorizationHeader,
    );
    if (authorization == null || !authorization.startsWith('Bearer ')) {
      return null;
    }
    return _sessions[authorization.substring('Bearer '.length)];
  }

  void _broadcastMetrics() {
    for (final MapEntry<WebSocket, VisualQaProfile> entry
        in List<MapEntry<WebSocket, VisualQaProfile>>.from(
          _metricsSockets.entries,
        )) {
      if (entry.key.readyState != WebSocket.open) {
        _removeSocket(entry.key);
        continue;
      }
      entry.key.add(jsonEncode(VisualQaFixtures.metrics(entry.value)));
    }
  }

  void _broadcastLog(VisualQaProfile profile, String line) {
    for (final MapEntry<WebSocket, VisualQaProfile> entry
        in List<MapEntry<WebSocket, VisualQaProfile>>.from(
          _logSockets.entries,
        )) {
      if (entry.key.readyState != WebSocket.open) {
        _removeSocket(entry.key);
        continue;
      }
      if (entry.value == profile) {
        entry.key.add(
          jsonEncode(<String, Object?>{'type': 'log', 'line': line}),
        );
      }
    }
  }

  void _removeSocket(WebSocket socket) {
    _metricsSockets.remove(socket);
    _logSockets.remove(socket);
  }

  Future<void> _requireGetAndWrite(HttpRequest request, Object? data) async {
    if (!await _isMethod(request, 'GET')) return;
    await _writeData(request, data);
  }

  Future<bool> _isMethod(HttpRequest request, String expected) async {
    if (request.method == expected) return true;
    await _writeError(
      request,
      HttpStatus.methodNotAllowed,
      'Expected $expected',
    );
    return false;
  }

  Future<bool> _requireCounter(HttpRequest request) async {
    final String? counter = request.headers.value('X-React-Counter');
    if (counter != null && int.tryParse(counter) != null) return true;
    await _writeError(request, HttpStatus.conflict, 'Missing X-React-Counter');
    return false;
  }

  Future<Map<String, dynamic>> _readBody(HttpRequest request) async {
    final String raw = await utf8.decoder.bind(request).join();
    if (raw.trim().isEmpty) return <String, dynamic>{};
    final Object? decoded = jsonDecode(raw);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Request body must be a JSON object');
    }
    return decoded;
  }

  void _applyConfigChanges(
    Map<String, dynamic> config,
    Map<String, Object?> changes,
  ) {
    final List<dynamic> sections = config['sections'] as List<dynamic>;
    for (final dynamic sectionValue in sections) {
      final Map<String, dynamic> section = sectionValue as Map<String, dynamic>;
      final List<dynamic> nodes = section['nodes'] as List<dynamic>;
      for (final dynamic nodeValue in nodes) {
        final Map<String, dynamic> node = nodeValue as Map<String, dynamic>;
        final String key = node['key'] as String;
        if (changes.containsKey(key)) {
          node['value'] = changes[key];
        }
      }
    }
  }

  void _applyCors(HttpResponse response) {
    response.headers.set(
      HttpHeaders.accessControlAllowOriginHeader,
      allowedOrigin,
    );
    response.headers.set(HttpHeaders.varyHeader, 'Origin');
    response.headers.set(
      HttpHeaders.accessControlAllowMethodsHeader,
      'GET, POST, PUT, OPTIONS',
    );
    response.headers.set(
      HttpHeaders.accessControlAllowHeadersHeader,
      'Authorization, Content-Type, X-React-Counter',
    );
  }

  Future<void> _writeData(
    HttpRequest request,
    Object? data, {
    int statusCode = HttpStatus.ok,
  }) => _writeJson(request, <String, Object?>{
    'data': data,
  }, statusCode: statusCode);

  Future<void> _writeError(
    HttpRequest request,
    int statusCode,
    String message,
  ) => _writeJson(request, <String, Object?>{
    'error': <String, Object?>{'message': message},
  }, statusCode: statusCode);

  Future<void> _writeJson(
    HttpRequest request,
    Object? body, {
    int statusCode = HttpStatus.ok,
  }) async {
    request.response.statusCode = statusCode;
    request.response.headers.contentType = ContentType.json;
    request.response.headers.set(HttpHeaders.cacheControlHeader, 'no-store');
    request.response.write(jsonEncode(body));
    await request.response.close();
  }
}

final class _VisualQaSession {
  final VisualQaProfile profile;
  final List<Map<String, dynamic>> features;
  final List<Map<String, dynamic>> tweaks;
  final List<Map<String, dynamic>> worlds;
  final Map<String, dynamic> config;
  final List<String> logs;

  _VisualQaSession(this.profile)
    : features = VisualQaFixtures.features(profile),
      tweaks = VisualQaFixtures.tweaks(profile),
      worlds = VisualQaFixtures.worlds(profile),
      config = VisualQaFixtures.config(profile),
      logs = VisualQaFixtures.logs(profile);
}

Future<void> main(List<String> arguments) async {
  final int port = _parsePort(arguments);
  final VisualQaServer server = await VisualQaServer.start(port: port);
  stdout.writeln('Visual QA stub: ${server.baseUri}');
  stdout.writeln('Allowed browser origin: ${server.allowedOrigin}');
  for (final VisualQaProfile profile in VisualQaProfile.values) {
    stdout.writeln('${profile.label}: ${server.pairingCode(profile)}');
  }
  stdout.writeln('Pairing code JSON: ${server.baseUri}/__qa/codes');
  await ProcessSignal.sigint.watch().first;
  await server.close();
}

int _parsePort(List<String> arguments) {
  int port = VisualQaServer.defaultPort;
  for (int index = 0; index < arguments.length; index++) {
    final String argument = arguments[index];
    if (argument == '--port') {
      if (index + 1 >= arguments.length) {
        throw const FormatException('--port requires a value');
      }
      port = int.parse(arguments[++index]);
      continue;
    }
    if (argument.startsWith('--port=')) {
      port = int.parse(argument.substring('--port='.length));
      continue;
    }
    throw FormatException('Unknown argument: $argument');
  }
  if (port < 1 || port > 65535) {
    throw FormatException('Port out of range: $port');
  }
  return port;
}
