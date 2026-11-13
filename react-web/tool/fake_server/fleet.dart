import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:crypto/crypto.dart';

import 'fixtures.dart';
import 'host_fixtures.dart';
import 'metric_fixtures.dart';
import 'plugin_source.dart';

part 'fleet_api.dart';
part 'fleet_control.dart';
part 'fleet_sockets.dart';

const List<String> _compactSamplerKeys = <String>[
  'id',
  'name',
  'suffix',
  'value',
  'display',
];

/// The kinds the plugin keeps a revision counter for.
const List<String> _revisionKinds = <String>[
  'features',
  'tweaks',
  'config',
  'worlds',
  'packs',
  'actions',
];

/// A pairing token the way the plugin's `WebTokens` keeps it: the bearer is
/// `<id>.<secret>`, the pairing ends after [lifetime] without use, and an
/// ended token is remembered with its reason.
final class FakeToken {
  final String id;
  final String label;
  final String role;
  final String bearer;
  final Duration lifetime;
  DateTime expiresAt;

  /// `token_revoked` or `token_expired` once the pairing ended.
  String? ended;
  String? client;

  FakeToken({
    required this.id,
    required this.label,
    required this.role,
    required this.bearer,
    required this.lifetime,
    required this.expiresAt,
  });

  List<String> get scopes => roleScopes[role] ?? const <String>[];

  /// The `whoami` body, and `summary.pairing`.
  Map<String, Object?> toJson() => <String, Object?>{
    'tokenId': id,
    'label': label,
    'role': role,
    'scopes': scopes,
    'expiresAt': _instant(expiresAt),
    'lifetimeSeconds': lifetime.inSeconds,
  };
}

/// A link `/react web pair` printed: one-time, ten minutes.
final class FakeLink {
  final String tokenId;
  final String secret;
  final String role;
  final String label;
  final Duration lifetime;
  final DateTime expiresAt;

  /// open, used or revoked.
  String state = 'open';

  FakeLink({
    required this.tokenId,
    required this.secret,
    required this.role,
    required this.label,
    required this.lifetime,
    required this.expiresAt,
  });
}

/// "2026-11-20T10:00:00Z": whole seconds, as the plugin writes instants.
String _instant(DateTime at) =>
    '${at.toUtc().toIso8601String().split('.').first}Z';

/// The live state of one fake server.
final class _FakeServer {
  final FakeServerProfile profile;
  final List<int> secret;
  final Map<String, FakeToken> tokens = <String, FakeToken>{};
  final Map<String, FakeLink> links = <String, FakeLink>{};
  final Map<String, int> counters = <String, int>{};
  final Map<String, String> unavailable = <String, String>{};
  final Map<String, int> revisions = <String, int>{
    for (final String kind in _revisionKinds) kind: 1,
  };
  final List<Map<String, dynamic>> features;
  final List<Map<String, dynamic>> tweaks;
  final List<Map<String, dynamic>> worlds;
  final List<Map<String, dynamic>> players;
  final Map<String, dynamic> config;
  final Map<String, dynamic> pluginApiCatalog;
  final List<_LogLine> logs;
  final Map<WebSocket, String> metricsSockets = <WebSocket, String>{};
  final Set<_LogSession> logSessions = <_LogSession>{};
  final Map<String, _Ticket> tickets = <String, _Ticket>{};
  final ListQueue<Map<String, Object?>> requests =
      ListQueue<Map<String, Object?>>();
  int logNumber;
  int logSeq;
  int sequence = 0;
  int ticketCount = 0;
  int mutateCursor = 0;
  int throttleCount = 0;
  int throttleRetryAfter = 0;
  bool quiet = false;

  /// Answers nothing: every connection is dropped, as a stopped server.
  bool down = false;
  bool failNextAction = false;
  Timer? revisionPush;

  _FakeServer(
    this.profile,
    PluginControlSource controls,
    int backlog,
    DateTime now,
  ) : secret = sha256.convert(utf8.encode('react-fake-${profile.slug}')).bytes,
      features = FakeFixtures.controls(profile, controls.features),
      tweaks = FakeFixtures.controls(profile, controls.tweaks),
      worlds = FakeFixtures.worlds(profile),
      players = FakeFixtures.players(profile),
      config = HostFixtures.config(profile),
      pluginApiCatalog = HostFixtures.pluginApiPacks(profile),
      logs = <_LogLine>[
        for (final (int index, String line) in FakeFixtures.logBacklog(
          profile,
          now,
          backlog,
        ).indexed)
          _LogLine(index + 1, line),
      ],
      logNumber = backlog,
      logSeq = backlog {
    // Folia has no single tick; the plugin reports those samplers as
    // unavailable and leaves them out of live frames.
    if (profile.folia) {
      for (final String id in kFoliaUnreported) {
        unavailable[id] = 'source-unavailable';
      }
    }
  }

  /// The ready admin token the fake's own tests use.
  String get builtInAdminId =>
      'tok_${'${profile.slug}admin'.padRight(16, 'x')}';

  /// A secret for [id], the same every run (43 base64url characters).
  String secretFor(String id, String purpose) => base64Url
      .encode(sha256.convert(utf8.encode('$purpose\n$id')).bytes)
      .replaceAll('=', '')
      .substring(0, 43);

  /// A token without a link, as the plugin would hold after a pairing.
  FakeToken mint(
    String role,
    String label,
    String id, {
    Duration lifetime = const Duration(days: 7),
  }) {
    final FakeToken token = FakeToken(
      id: id,
      label: label,
      role: role,
      bearer: '$id.${secretFor(id, 'bearer')}',
      lifetime: lifetime,
      expiresAt: DateTime.now().toUtc().add(lifetime),
    );
    tokens[id] = token;
    return token;
  }

  /// The token presenting [bearer], ended ones included.
  FakeToken? verify(String bearer) {
    final int dot = bearer.indexOf('.');
    if (dot < 0) return null;
    final FakeToken? token = tokens[bearer.substring(0, dot)];
    if (token == null || token.bearer != bearer) return null;
    if (token.ended == null && !token.expiresAt.isAfter(DateTime.now())) {
      token.ended = 'token_expired';
    }
    return token;
  }

  /// Bumps [kind] and, like the plugin, pushes a metrics frame with the
  /// new revisions shortly after the write, repeating the sequence.
  int bump(String kind) {
    revisionPush ??= Timer(const Duration(milliseconds: 50), () {
      revisionPush = null;
      broadcastMetrics(DateTime.now(), advance: false);
    });
    return revisions[kind] = revisions[kind]! + 1;
  }

  /// The full snapshot of `GET /metrics`: every sampler with `available`
  /// and `unavailableReason`, plus `data.revisions`.
  Map<String, dynamic> metricsBody(DateTime now, {bool advance = true}) {
    final Map<String, dynamic> body = FakeFixtures.metrics(
      profile,
      sequence: advance ? ++sequence : sequence,
      now: now,
    );
    final Map<String, dynamic> data = body['data'] as Map<String, dynamic>;
    for (final dynamic item in data['samplers'] as List<dynamic>) {
      final Map<String, dynamic> sampler = item as Map<String, dynamic>;
      final String? reason = unavailable[sampler['id']];
      sampler['available'] = reason == null;
      sampler['unavailableReason'] = reason;
    }
    data['revisions'] = Map<String, int>.of(revisions);
    return body;
  }

  /// The `/ws/metrics` frame and `GET /metrics?compact=1` body: available
  /// samplers only, as `{id, name, suffix, value, display}`.
  Map<String, dynamic> compactBody(DateTime now, {bool advance = true}) {
    final Map<String, dynamic> body = metricsBody(now, advance: advance);
    final Map<String, dynamic> data = body['data'] as Map<String, dynamic>;
    data['samplers'] = <Map<String, dynamic>>[
      for (final dynamic item in data['samplers'] as List<dynamic>)
        if ((item as Map<String, dynamic>)['available'] == true)
          <String, dynamic>{
            for (final String key in _compactSamplerKeys) key: item[key],
          },
    ];
    return body;
  }

  _LogLine addLog(String line) {
    final _LogLine entry = _LogLine(++logSeq, line);
    logs.add(entry);
    if (logs.length > 2000) logs.removeRange(0, logs.length - 2000);
    for (final _LogSession session in logSessions) {
      session.enqueue(entry);
    }
    return entry;
  }

  void record(String method, String path, String? tokenId, int status) {
    requests.add(<String, Object?>{
      'method': method,
      'path': path,
      'tokenId': tokenId,
      'status': status,
    });
    while (requests.length > 50) {
      requests.removeFirst();
    }
  }
}

/// Request failures that end as an error reply: `{"error": {"message": ...}}`
/// unless [body] replaces it.
final class _Reject implements Exception {
  final int status;
  final String message;

  /// The plugin's error code; by status when null.
  final String? code;
  final Map<String, Object?> extras;
  final Map<String, String> headers;

  const _Reject(
    this.status,
    this.message, {
    this.code,
    this.extras = const <String, Object?>{},
    this.headers = const <String, String>{},
  });
}

/// The plugin's `WebError` code for a status without its own.
String _codeFor(int status) => switch (status) {
  400 => 'bad_request',
  401 => 'unauthorized',
  403 => 'forbidden',
  404 => 'not_found',
  409 => 'conflict',
  413 => 'too_large',
  429 => 'rate_limited',
  503 => 'unavailable',
  _ => 'internal',
};

/// A fleet of fake React servers on one port, each under its own base path
/// (`/s1`, `/s2`, ...) with its own key, tokens and data, speaking the
/// plugin's `/api/v1` REST API and its `/ws/metrics` and `/ws/logs` sockets.
/// The `/__fake/...` control routes are listed in `fleet_control.dart`.
final class FakeFleet {
  static const int defaultPort = 8821;
  static const String defaultOrigin = 'http://127.0.0.1:8820';

  final HttpServer _http;
  final List<String> allowedOrigins;

  /// Where links point (`<appUrl>/pair#RCT3...`).
  final String appUrl;
  final Map<String, _FakeServer> _servers;
  final Duration idleTimeout;
  final math.Random _random = math.Random(7);
  late final StreamSubscription<HttpRequest> _requests;
  late final Timer _metricsPulse;
  late final Timer _logPulse;
  Timer? _mutatePulse;
  bool _closed = false;

  FakeFleet._(
    this._http,
    this.allowedOrigins,
    this.appUrl,
    this._servers,
    this.idleTimeout,
  );

  /// Binds [port] on 127.0.0.1 (0 picks a free one) and starts [servers]
  /// servers with [players] players each and [logBacklog] logged lines.
  /// Features and tweaks come from the plugin sources in [pluginContent].
  /// A positive [mutateEvery] flips one tweak per server on that period;
  /// a positive [idleTimeout] closes log sockets that went that long
  /// without a frame.
  static Future<FakeFleet> start({
    int port = defaultPort,
    List<String> origins = const <String>[defaultOrigin],
    String? appUrl,
    int servers = 3,
    int players = 40,
    int logBacklog = 400,
    Duration mutateEvery = Duration.zero,
    Duration idleTimeout = Duration.zero,
    Directory? pluginContent,
  }) async {
    final PluginControlSource controls = PluginControlSource.read(
      pluginContent ?? PluginControlSource.defaultContent(),
    );
    final DateTime now = DateTime.now();
    final Map<String, _FakeServer> states = <String, _FakeServer>{
      for (final FakeServerProfile profile in FakeServerProfile.fleet(
        servers,
        players: players,
      ))
        profile.slug: _FakeServer(profile, controls, logBacklog, now),
    };
    final HttpServer http = await HttpServer.bind(
      InternetAddress.loopbackIPv4,
      port,
    );
    final FakeFleet fleet = FakeFleet._(
      http,
      List<String>.of(origins),
      appUrl ?? (origins.isEmpty ? defaultOrigin : origins.first),
      states,
      idleTimeout,
    );
    for (final _FakeServer state in states.values) {
      state.mint('admin', 'React Web', state.builtInAdminId);
    }
    fleet._requests = http.listen(fleet._handle);
    fleet._metricsPulse = Timer.periodic(
      const Duration(seconds: 1),
      (Timer _) => fleet._broadcastMetrics(),
    );
    fleet._logPulse = Timer.periodic(
      const Duration(milliseconds: 1500),
      (Timer _) => fleet._appendLogs(),
    );
    if (mutateEvery > Duration.zero) {
      fleet._mutatePulse = Timer.periodic(
        mutateEvery,
        (Timer _) => fleet._mutateTweaks(),
      );
    }
    return fleet;
  }

  int get port => _http.port;

  Uri get baseUri => Uri.parse('http://127.0.0.1:$port');

  List<FakeServerProfile> get profiles => <FakeServerProfile>[
    for (final _FakeServer state in _servers.values) state.profile,
  ];

  Uri directUrl(FakeServerProfile profile) =>
      baseUri.replace(path: '/${profile.slug}');

  /// The ready admin token of [profile].
  FakeToken adminToken(FakeServerProfile profile) {
    final _FakeServer state = _servers[profile.slug]!;
    return state.tokens[state.builtInAdminId]!;
  }

  /// A token on [profile] as if a link had just been redeemed, for tests
  /// that need one without the pairing round trip.
  FakeToken issue(
    FakeServerProfile profile, {
    String role = 'admin',
    String label = 'React Web',
  }) {
    final String tokenId = pair(
      profile,
      role: role,
      label: label,
    ).pending.tokenId;
    final _FakeServer state = _servers[profile.slug]!;
    state.links[tokenId]!.state = 'used';
    return state.mint(role, label, tokenId);
  }

  /// A new pairing link on [profile], as `/react web pair` prints one: it
  /// works once, until [linkLife] has passed, and pairs as [role]. The
  /// pairing lasts [lifetime] without use.
  ({String link, FakeLink pending}) pair(
    FakeServerProfile profile, {
    String role = 'admin',
    String label = 'React Web',
    Duration lifetime = const Duration(days: 7),
    Duration linkLife = const Duration(minutes: 10),
  }) {
    if (!roleScopes.containsKey(role)) {
      throw ArgumentError.value(role, 'role', 'viewer, operator or admin');
    }
    final _FakeServer state = _servers[profile.slug]!;
    const String alphabet =
        'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-';
    final String id =
        'tok_${List<String>.generate(16, (int _) => alphabet[_random.nextInt(64)]).join()}';
    final DateTime now = DateTime.now().toUtc();
    final FakeLink pending = FakeLink(
      tokenId: id,
      secret: state.secretFor(id, 'link${now.microsecondsSinceEpoch}'),
      role: role,
      label: label,
      lifetime: lifetime,
      expiresAt: now.add(linkLife),
    );
    state.links[id] = pending;
    return (
      link: profile.pairingLink(
        appUrl: appUrl,
        directUrl: directUrl(profile),
        tokenId: id,
        secret: pending.secret,
        expiresAt: pending.expiresAt,
        role: role,
        lifetime: lifetime,
        label: label,
      ),
      pending: pending,
    );
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _metricsPulse.cancel();
    _logPulse.cancel();
    _mutatePulse?.cancel();
    for (final _FakeServer state in _servers.values) {
      state.revisionPush?.cancel();
      for (final _LogSession session in state.logSessions) {
        session.stop();
      }
      for (final WebSocket socket in <WebSocket>[
        ...state.metricsSockets.keys,
        for (final _LogSession session in state.logSessions) session.socket,
      ]) {
        await socket.close(WebSocketStatus.goingAway, 'Fake server shutdown');
      }
      state.metricsSockets.clear();
      state.logSessions.clear();
    }
    await _http.close(force: true);
    await _requests.cancel();
  }

  Future<void> _handle(HttpRequest request) async {
    _FakeServer? recorded;
    String recordedPath = '';
    String? recordedToken;
    try {
      final List<String> segments = request.uri.pathSegments;
      final _FakeServer? target = segments.isEmpty
          ? null
          : _servers[segments.first];
      if (target != null && target.down) {
        // A stopped server: the connection drops without an answer.
        (await request.response.detachSocket()).destroy();
        return;
      }
      // The plugin's WebOrigins: every preflight is answered, and a refused
      // origin still gets CORS headers so the page can read why.
      final String? origin = request.headers.value('Origin');
      if (origin != null) _applyCors(request.response, origin);
      if (request.method == 'OPTIONS') {
        request.response.statusCode = HttpStatus.noContent;
        await request.response.close();
        return;
      }
      if (origin != null && !allowedOrigins.contains(origin)) {
        throw _Reject(
          HttpStatus.forbidden,
          'This page is not in allowedOrigins.',
          code: 'origin_not_allowed',
          extras: <String, Object?>{
            'origin': origin,
            'configKey': 'allowedOrigins',
            'configFile': 'plugins/React/web.toml',
          },
        );
      }
      if (segments.isNotEmpty && segments.first == '__fake') {
        await _handleControl(request, segments.sublist(1));
        return;
      }
      final _FakeServer? state = target;
      if (state == null) {
        throw const _Reject(HttpStatus.notFound, 'Unknown server path');
      }
      final List<String> rest = segments.sublist(1);
      if (rest.length == 2 && rest.first == 'ws') {
        await _handleSocket(request, state, rest[1]);
        return;
      }
      if (rest.length < 3 || rest[0] != 'api' || rest[1] != 'v1') {
        throw const _Reject(HttpStatus.notFound, 'Route not found');
      }
      recorded = state;
      recordedPath = request.uri.path.substring(state.profile.slug.length + 1);
      recordedToken = _presentedToken(request, state)?.id;
      if (state.throttleCount > 0) {
        state.throttleCount--;
        throw _Reject(
          HttpStatus.tooManyRequests,
          'Too many requests',
          headers: <String, String>{
            HttpHeaders.retryAfterHeader: '${state.throttleRetryAfter}',
          },
        );
      }
      final List<String> route = rest.sublist(2);
      if (route.length == 1 && route.first == 'ping') {
        _expect(request, 'GET');
        await _writeData(request, FakeFixtures.capabilities(state.profile));
      } else if (route.length == 1 && route.first == 'pair') {
        _expect(request, 'POST');
        await _redeem(request, state);
      } else {
        final FakeToken token = _authenticate(request, state);
        await _handleApi(request, state, token, route);
      }
    } on _Reject catch (reject) {
      for (final MapEntry<String, String> header in reject.headers.entries) {
        request.response.headers.set(header.key, header.value);
      }
      await _writeJson(request, <String, Object?>{
        'error': <String, Object?>{
          'code': reject.code ?? _codeFor(reject.status),
          'message': reject.message,
          ...reject.extras,
        },
      }, status: reject.status);
    } on FormatException catch (error) {
      await _writeError(request, HttpStatus.badRequest, error.message);
    } on Object catch (error, stackTrace) {
      stderr.writeln('Fake server request failed: $error\n$stackTrace');
      await _writeError(
        request,
        HttpStatus.internalServerError,
        'Request failed',
      );
    }
    recorded?.record(
      request.method,
      recordedPath,
      recordedToken,
      request.response.statusCode,
    );
  }

  static FakeToken? _presentedToken(HttpRequest request, _FakeServer state) {
    final String? header = request.headers.value(
      HttpHeaders.authorizationHeader,
    );
    if (header == null || !header.startsWith('Bearer ')) return null;
    return state.verify(header.substring('Bearer '.length));
  }

  /// `POST /api/v1/pair`: the plugin's `PairResource`. A link works once
  /// and for ten minutes; a revoked link is refused as revoked.
  Future<void> _redeem(HttpRequest request, _FakeServer state) async {
    final Map<String, dynamic> body = await _readBody(request);
    if (body.keys.any(
      (String key) =>
          !const <String>{'tokenId', 'secret', 'client'}.contains(key),
    )) {
      throw const _Reject(HttpStatus.badRequest, 'Unknown field.');
    }
    final Object? tokenId = body['tokenId'];
    final Object? secret = body['secret'];
    final FakeLink? pending = tokenId is String ? state.links[tokenId] : null;
    if (pending == null || secret != pending.secret) {
      throw const _Reject(
        HttpStatus.unauthorized,
        'The server does not know this link.',
        code: 'pairing_unknown',
      );
    }
    switch (pending.state) {
      case 'used':
        throw const _Reject(
          HttpStatus.gone,
          'The link was already used.',
          code: 'pairing_used',
        );
      case 'revoked':
        throw const _Reject(
          HttpStatus.gone,
          'The link was revoked.',
          code: 'pairing_revoked',
        );
    }
    if (!pending.expiresAt.isAfter(DateTime.now())) {
      throw const _Reject(
        HttpStatus.gone,
        'The link expired.',
        code: 'pairing_expired',
      );
    }
    pending.state = 'used';
    final FakeToken token = state.mint(
      pending.role,
      pending.label,
      pending.tokenId,
      lifetime: pending.lifetime,
    );
    final Object? client = body['client'];
    if (client is String) token.client = client;
    await _writeData(request, <String, Object?>{
      'token': token.bearer,
      ...token.toJson(),
      'fingerprint': state.profile.fingerprint,
      'serverName': state.profile.label,
    });
  }

  /// The plugin's `WebAuth`: a bearer of a pairing that has not ended (each
  /// use moves its end on), and on mutations an `X-React-Counter` above the
  /// last one seen for the token.
  FakeToken _authenticate(HttpRequest request, _FakeServer state) {
    final String? header = request.headers.value(
      HttpHeaders.authorizationHeader,
    );
    if (header == null || !header.startsWith('Bearer ')) {
      throw const _Reject(
        HttpStatus.unauthorized,
        'Missing or invalid Authorization header',
      );
    }
    final FakeToken? token = state.verify(header.substring('Bearer '.length));
    if (token == null) {
      throw const _Reject(HttpStatus.unauthorized, 'Unknown token');
    }
    final String? ended = token.ended;
    if (ended != null) {
      throw _Reject(HttpStatus.unauthorized, 'The pairing ended.', code: ended);
    }
    token.expiresAt = DateTime.now().toUtc().add(token.lifetime);
    if (const <String>{
      'PUT',
      'POST',
      'PATCH',
      'DELETE',
    }.contains(request.method)) {
      final String? value = request.headers.value('X-React-Counter');
      final int? last = state.counters[token.id];
      final _Reject conflict = _Reject(
        HttpStatus.conflict,
        'Replayed or non-increasing X-React-Counter',
        code: 'counter_conflict',
        extras: <String, Object?>{'lastCounter': last ?? 0},
      );
      if (value == null) throw conflict;
      final int? counter = int.tryParse(value);
      if (counter == null) {
        throw const _Reject(
          HttpStatus.badRequest,
          'Invalid X-React-Counter value',
        );
      }
      if (last != null && counter <= last) throw conflict;
      state.counters[token.id] = counter;
    }
    return token;
  }

  void _appendLogs() {
    final DateTime now = DateTime.now();
    for (final _FakeServer state in _servers.values) {
      if (state.quiet) continue;
      state.addLog(FakeFixtures.logLine(state.profile, state.logNumber++, now));
    }
  }

  void _mutateTweaks() {
    for (final _FakeServer state in _servers.values) {
      if (state.tweaks.isEmpty) continue;
      final Map<String, dynamic> tweak =
          state.tweaks[state.mutateCursor++ % state.tweaks.length];
      tweak['enabled'] = tweak['enabled'] != true;
      state.bump('tweaks');
    }
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

  void _applyCors(HttpResponse response, String origin) {
    response.headers
      ..set(HttpHeaders.accessControlAllowOriginHeader, origin)
      ..set(HttpHeaders.varyHeader, 'Origin')
      ..set(
        HttpHeaders.accessControlAllowMethodsHeader,
        'GET, POST, PUT, PATCH, DELETE, OPTIONS',
      )
      ..set(
        HttpHeaders.accessControlAllowHeadersHeader,
        'Authorization, Content-Type, X-React-Counter, X-React-Challenge',
      )
      ..set(HttpHeaders.accessControlExposeHeadersHeader, 'Retry-After');
  }

  Future<void> _writeData(HttpRequest request, Object? data) =>
      _writeJson(request, <String, Object?>{'data': data});

  Future<void> _writeError(HttpRequest request, int status, String message) =>
      _writeJson(request, <String, Object?>{
        'error': <String, Object?>{
          'code': _codeFor(status),
          'message': message,
        },
      }, status: status);

  Future<void> _writeJson(
    HttpRequest request,
    Object? body, {
    int status = HttpStatus.ok,
  }) async {
    try {
      request.response
        ..statusCode = status
        ..headers.contentType = ContentType.json
        ..headers.set(HttpHeaders.cacheControlHeader, 'no-store')
        ..write(jsonEncode(body));
      await request.response.close();
    } on StateError {
      // The socket upgrade already took the response.
    }
  }
}

void _requireScope(FakeToken token, String scope) {
  if (!token.scopes.contains(scope)) {
    throw _Reject(
      HttpStatus.forbidden,
      'Insufficient scope: $scope',
      code: 'scope_missing',
      extras: <String, Object?>{'scope': scope},
    );
  }
}

void _expect(HttpRequest request, String method) {
  if (request.method != method) {
    throw _Reject(HttpStatus.methodNotAllowed, 'Expected $method');
  }
}

String _clock(DateTime at) =>
    '${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}:${at.second.toString().padLeft(2, '0')}';

Map<String, dynamic>? _configNode(Map<String, dynamic> config, String key) {
  for (final dynamic section in config['sections'] as List<dynamic>) {
    for (final dynamic item
        in (section as Map<String, dynamic>)['nodes'] as List<dynamic>) {
      final Map<String, dynamic> node = item as Map<String, dynamic>;
      if (node['key'] == key) return node;
    }
  }
  return null;
}

void _applyConfig(Map<String, dynamic> config, Map<String, Object?> changes) {
  for (final MapEntry<String, Object?> change in changes.entries) {
    _configNode(config, change.key)?['value'] = change.value;
  }
}
