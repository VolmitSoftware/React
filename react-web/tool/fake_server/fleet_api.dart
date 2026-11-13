part of 'fleet.dart';

/// The `/api/v1` resources of one fake server.
extension _FleetApi on FakeFleet {
  Future<void> _handleApi(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    final FakeServerProfile profile = state.profile;
    final DateTime now = DateTime.now();
    final String resource = route.first;
    final int depth = route.length;
    switch ((resource, depth)) {
      case ('identity', 1):
        _expect(request, 'GET');
        await _writeData(request, FakeFixtures.identity(profile));
      case ('whoami', 1):
        _expect(request, 'GET');
        await _writeData(request, token.toJson());
      case ('summary', 1):
        _expect(request, 'GET');
        _requireScope(token, 'read');
        await _writeData(request, _summary(state, token, now));
      case ('metrics', _):
        await _handleMetrics(request, state, route, now);
      case ('plugin-api-packs', _):
        await _handlePluginApiPacks(request, state, token, route);
      case ('heatmaps', _):
        await _handleHeatmaps(request, state, route);
      case ('features', _):
        await _handleControls(request, state, token, 'features', route);
      case ('tweaks', _):
        await _handleControls(request, state, token, 'tweaks', route);
      case ('worlds', _):
        await _handleWorlds(request, state, token, route);
      case ('players', _):
        await _handlePlayers(request, state, token, route);
      case ('actions', _):
        await _handleActions(request, state, token, route);
      case ('incidents', 1):
        _expect(request, 'GET');
        await _writeData(request, FakeFixtures.incidents(profile, now));
      case ('environment', 1):
        _expect(request, 'GET');
        await _writeData(request, HostFixtures.environment(profile, now));
      case ('config', _):
        await _handleConfig(request, state, token, route);
      case ('logs', 1):
        _expect(request, 'GET');
        _requireScope(token, 'console:read');
        await _writeJson(request, _logPage(state, request.uri.queryParameters));
      case ('console', 2) when route[1] == 'execute':
        _expect(request, 'POST');
        _requireScope(token, 'console:execute');
        final Object? command = (await _readBody(request))['command'];
        if (command is! String || command.trim().isEmpty) {
          throw const _Reject(HttpStatus.badRequest, 'Command is required');
        }
        state.addLog(
          '[${_clock(now)} INFO]: ${token.label} issued server command: /${command.trim()}',
        );
        await _writeData(request, <String, dynamic>{'dispatched': true});
      default:
        throw const _Reject(HttpStatus.notFound, 'Route not found');
    }
  }

  Future<void> _handleMetrics(
    HttpRequest request,
    _FakeServer state,
    List<String> route,
    DateTime now,
  ) async {
    _expect(request, 'GET');
    if (route.length == 1) {
      await _writeJson(
        request,
        request.uri.queryParameters['compact'] == '1'
            ? state.compactBody(now)
            : state.metricsBody(now),
      );
      return;
    }
    if (route.length == 2 && route[1] == 'catalog') {
      await _writeData(request, <Map<String, dynamic>>[
        for (final Map<String, dynamic> entry
            in FakeFixtures.metricHistoryCatalog(now))
          <String, dynamic>{
            ...entry,
            'available': !state.unavailable.containsKey(entry['id']),
            'unavailableReason': state.unavailable[entry['id']],
          },
      ]);
      return;
    }
    if (route.length == 2 && route[1] == 'range') {
      final String? ids = request.uri.queryParameters['ids'];
      final Map<String, dynamic>? range = FakeFixtures.metricRange(
        state.profile,
        range: request.uri.queryParameters['range'] ?? '',
        ids: ids?.split(',').where((String id) => id.isNotEmpty).toList(),
        nowMs: now.millisecondsSinceEpoch,
      );
      if (range == null) {
        throw const _Reject(
          HttpStatus.badRequest,
          'range must be 1h, 6h or 24h',
        );
      }
      await _writeData(request, range);
      return;
    }
    if (route.length != 2 || route[1] != 'history') {
      throw const _Reject(HttpStatus.notFound, 'Metric route not found');
    }
    final Map<String, String> query = request.uri.queryParameters;
    final _HistoryCursor cursor;
    if (query['cursor'] != null) {
      cursor = _HistoryCursor.decode(query['cursor']!);
    } else {
      final List<String> ids = (query['ids'] ?? '')
          .split(',')
          .where((String id) => id.isNotEmpty)
          .toList();
      final int? from = int.tryParse(query['from'] ?? '');
      final int? to = int.tryParse(query['to'] ?? '');
      final int maxPoints = int.tryParse(query['maxPoints'] ?? '') ?? 1200;
      final int pageSize = int.tryParse(query['pageSize'] ?? '') ?? 256;
      if (ids.isEmpty || from == null || to == null || to <= from) {
        throw const _Reject(
          HttpStatus.badRequest,
          'ids, from and to are required',
        );
      }
      if (maxPoints < 1 || maxPoints > 1200) {
        throw const _Reject(
          HttpStatus.badRequest,
          'maxPoints must be between 1 and 1200',
        );
      }
      cursor = _HistoryCursor(
        ids: ids,
        from: from,
        to: to,
        pageFrom: from,
        resolution: FakeFixtures.resolutionFor(from, to, maxPoints),
        sequence: state.sequence,
        through: now.millisecondsSinceEpoch,
        pagePoints: pageSize.clamp(1, 1200),
      );
    }
    final Map<String, dynamic>? page = FakeFixtures.metricHistoryPage(
      state.profile,
      ids: cursor.ids,
      requestedFromMs: cursor.from,
      requestedToMs: cursor.to,
      pageFromMs: cursor.pageFrom,
      resolutionMs: cursor.resolution,
      pagePoints: cursor.pagePoints,
      throughSequence: cursor.sequence,
      throughMs: cursor.through,
      nextCursor: (int pageTo) => cursor.next(pageTo).encode(),
    );
    if (page == null) {
      throw const _Reject(HttpStatus.notFound, 'No requested metric is known');
    }
    await _writeData(request, page);
  }

  Future<void> _handlePluginApiPacks(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    final List<dynamic> packs =
        state.pluginApiCatalog['packs'] as List<dynamic>;
    if (route.length == 1) {
      _expect(request, 'GET');
      await _writeData(request, state.pluginApiCatalog);
      return;
    }
    if (route.length != 2) {
      throw const _Reject(HttpStatus.notFound, 'Pack route not found');
    }
    if (route[1] == 'validate') {
      _expect(request, 'POST');
      _requireScope(token, 'admin');
      final String content =
          (await _readBody(request))['content'] as String? ?? '';
      final RegExpMatch? id = RegExp(
        r'^id\s*=\s*"([a-z0-9][a-z0-9._-]+)"',
        multiLine: true,
      ).firstMatch(content);
      final bool valid =
          content.contains('schema = "react.plugin-api/v1"') &&
          id != null &&
          content.contains('[[metrics]]');
      await _writeData(request, <String, dynamic>{
        'valid': valid,
        'id': id?.group(1) ?? '',
        'metricCount': RegExp(
          r'^\[\[metrics\]\]$',
          multiLine: true,
        ).allMatches(content).length,
        'message': valid
            ? 'Pack is valid'
            : 'Missing schema, id or [[metrics]]',
      });
      return;
    }
    final String id = Uri.decodeComponent(route[1]);
    final int index = packs.indexWhere(
      (dynamic item) => (item as Map<String, dynamic>)['id'] == id,
    );
    switch (request.method) {
      case 'GET':
        if (index < 0) throw _Reject(HttpStatus.notFound, 'Unknown pack: $id');
        await _writeData(request, packs[index]);
      case 'PUT':
        _requireScope(token, 'admin');
        final String content =
            (await _readBody(request))['content'] as String? ?? '';
        final Map<String, dynamic> pack = <String, dynamic>{
          'id': id,
          'version': '1.0.0',
          'name': 'Community Pack',
          'authors': <String>['Community'],
          'targetPlugin': 'ExamplePlugin',
          'targetVersion': '1.0.0',
          'targetVersions': <String>['*'],
          'enabled': true,
          'trusted': false,
          'state': 'DEGRADED',
          'detail': 'target-plugin-unavailable',
          'fileName': '$id.toml',
          'rawContent': content,
          'metrics': <Map<String, dynamic>>[],
        };
        if (index < 0) {
          packs.add(pack);
        } else {
          packs[index] = pack;
        }
        state.bump('packs');
        await _writeData(request, pack);
      case 'DELETE':
        _requireScope(token, 'admin');
        if (index < 0) throw _Reject(HttpStatus.notFound, 'Unknown pack: $id');
        packs.removeAt(index);
        state.bump('packs');
        await _writeData(request, state.pluginApiCatalog);
      default:
        throw const _Reject(HttpStatus.methodNotAllowed, 'Method not allowed');
    }
  }

  Future<void> _handleHeatmaps(
    HttpRequest request,
    _FakeServer state,
    List<String> route,
  ) async {
    _expect(request, 'GET');
    if (route.length == 1) {
      await _writeData(request, HostFixtures.heatmapSummaries());
      return;
    }
    if (route.length != 2) {
      throw const _Reject(HttpStatus.notFound, 'Heatmap route not found');
    }
    final Map<String, String> query = request.uri.queryParameters;
    int? integer(String key) {
      final String? raw = query[key];
      if (raw == null) return null;
      final int? value = int.tryParse(raw);
      if (value == null) {
        throw const _Reject(
          HttpStatus.badRequest,
          'Heatmap coordinates and radius must be integers',
        );
      }
      return value;
    }

    final Map<String, dynamic>? heatmap = HostFixtures.heatmap(
      state.profile,
      route[1],
      world: query['world'],
      centerChunkX: integer('centerChunkX'),
      centerChunkZ: integer('centerChunkZ'),
      radius: integer('radius'),
    );
    if (heatmap == null) {
      throw _Reject(HttpStatus.notFound, 'Unknown heatmap: ${route[1]}');
    }
    await _writeData(request, heatmap);
  }

  Future<void> _handleControls(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    String kind,
    List<String> route,
  ) async {
    final List<Map<String, dynamic>> controls = kind == 'tweaks'
        ? state.tweaks
        : state.features;
    if (route.length == 1) {
      _expect(request, 'GET');
      await _writeData(request, controls);
      return;
    }
    final Map<String, dynamic> control = controls.firstWhere(
      (Map<String, dynamic> candidate) => candidate['id'] == route[1],
      orElse: () =>
          throw _Reject(HttpStatus.notFound, 'Unknown control: ${route[1]}'),
    );
    if (route.length == 2 && request.method == 'GET') {
      await _writeData(request, control);
      return;
    }
    if (route.length == 2 && request.method == 'PUT') {
      _requireScope(token, 'op:execute');
      final Object? enabled = (await _readBody(request))['enabled'];
      if (enabled is! bool) {
        throw const _Reject(HttpStatus.badRequest, 'Enabled must be a boolean');
      }
      control['enabled'] = enabled;
      state.bump(kind);
      await _writeData(request, control);
      return;
    }
    if (route.length == 3 && route[2] == 'config' && request.method == 'PUT') {
      _requireScope(token, 'op:execute');
      final Map<String, dynamic> body = await _readBody(request);
      for (final dynamic item in control['knobs'] as List<dynamic>) {
        final Map<String, dynamic> knob = item as Map<String, dynamic>;
        if (body.containsKey(knob['key'])) knob['value'] = body[knob['key']];
      }
      state.bump(kind);
      await _writeData(request, control);
      return;
    }
    throw const _Reject(HttpStatus.methodNotAllowed, 'Method not allowed');
  }

  Future<void> _handleWorlds(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    if (route.length == 1) {
      _expect(request, 'GET');
      await _writeData(request, state.worlds);
      return;
    }
    if (route.length != 2) {
      throw const _Reject(HttpStatus.notFound, 'World route not found');
    }
    _expect(request, 'PUT');
    _requireScope(token, 'op:execute');
    final Map<String, dynamic> body = await _readBody(request);
    final Map<String, dynamic> world = state.worlds.firstWhere(
      (Map<String, dynamic> candidate) => candidate['name'] == route[1],
      orElse: () =>
          throw _Reject(HttpStatus.notFound, 'Unknown world: ${route[1]}'),
    );
    for (final String key in <String>['budgetMs', 'panicMs', 'releaseMs']) {
      final Object? value = body[key];
      if (value is num) world[key] = value.toDouble();
    }
    state.bump('worlds');
    await _writeData(request, world);
  }

  Future<void> _handlePlayers(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    _requireScope(token, 'admin');
    if (route.length == 1) {
      _expect(request, 'GET');
      await _writeData(request, state.players);
      return;
    }
    if (route.length != 3 || route[2] != 'teleport') {
      throw const _Reject(HttpStatus.notFound, 'Player route not found');
    }
    _expect(request, 'POST');
    final Map<String, dynamic> body = await _readBody(request);
    final Map<String, dynamic> player = state.players.firstWhere(
      (Map<String, dynamic> candidate) => candidate['id'] == route[1],
      orElse: () =>
          throw const _Reject(HttpStatus.notFound, 'Player is not online'),
    );
    if (body['confirm'] != true) {
      throw const _Reject(
        HttpStatus.badRequest,
        'Teleport requires confirm=true',
      );
    }
    final Object? blockX = body['blockX'];
    final Object? blockZ = body['blockZ'];
    if (blockX is! int || blockZ is! int) {
      throw const _Reject(
        HttpStatus.badRequest,
        'blockX and blockZ are required',
      );
    }
    final Object? worldKey = body['worldKey'];
    await _writeData(request, <String, dynamic>{
      'playerId': player['id'],
      'playerName': player['name'],
      'status': 'queued',
      'worldKey': worldKey is String ? worldKey : 'minecraft:world',
      'blockX': blockX,
      'blockZ': blockZ,
    });
  }

  Future<void> _handleActions(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    if (route.length == 1) {
      _expect(request, 'GET');
      await _writeData(request, FakeFixtures.actions());
      return;
    }
    if (route.length == 3 && route[1] == 'tickets' && request.method == 'GET') {
      final _Ticket? ticket = state.tickets[route[2]];
      if (ticket == null) {
        throw const _Reject(HttpStatus.notFound, 'Unknown ticket');
      }
      await _writeJson(request, ticket.toJson(DateTime.now()));
      return;
    }
    if (route.length != 3 || route[2] != 'execute') {
      throw const _Reject(HttpStatus.notFound, 'Action route not found');
    }
    _expect(request, 'POST');
    _requireScope(token, 'op:execute');
    final Map<String, dynamic> action = FakeFixtures.actions().firstWhere(
      (Map<String, dynamic> candidate) => candidate['id'] == route[1],
      orElse: () =>
          throw _Reject(HttpStatus.notFound, 'Unknown action: ${route[1]}'),
    );
    if (action['destructive'] == true) _requireScope(token, 'admin');
    await _readBody(request);
    final DateTime now = DateTime.now();
    final String ticket = '${state.profile.slug}-ticket-${++state.ticketCount}';
    state.tickets[ticket] = _Ticket(
      ticket,
      route[1],
      action['name'] as String,
      now,
      fails: state.failNextAction,
    );
    state.failNextAction = false;
    while (state.tickets.length > 256) {
      state.tickets.remove(state.tickets.keys.first);
    }
    state.bump('actions');
    state.addLog(
      '[${_clock(now)} INFO]: [React] Queued action ${route[1]} as $ticket',
    );
    await _writeData(request, <String, dynamic>{
      'ticketId': ticket,
      'status': 'queued',
    });
  }

  Future<void> _handleConfig(
    HttpRequest request,
    _FakeServer state,
    FakeToken token,
    List<String> route,
  ) async {
    if (route.length == 1 && request.method == 'GET') {
      await _writeData(request, state.config);
      return;
    }
    if (route.length == 1 && request.method == 'PUT') {
      _requireScope(token, 'admin');
      _applyConfig(state.config, await _readBody(request));
      state.bump('config');
      await _writeData(request, state.config);
      return;
    }
    if (route.length == 3 && route[1] == 'preset' && request.method == 'POST') {
      _requireScope(token, 'admin');
      final Map<String, Object?>? changes = switch (route[2]) {
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
      if (changes == null) {
        throw _Reject(HttpStatus.notFound, 'Unknown preset: ${route[2]}');
      }
      _applyConfig(state.config, changes);
      state.bump('config');
      await _writeData(request, state.config);
      return;
    }
    throw const _Reject(HttpStatus.notFound, 'Config route not found');
  }
}

/// `GET /logs`: `{data, lastSeq}` with the last `limit` lines as plain
/// strings, or with `after` the first `limit` lines past that seq as
/// `{seq, line}` objects. `lastSeq` is the seq of the last line in `data`
/// (the newest seq when `data` is empty).
Map<String, Object?> _logPage(_FakeServer state, Map<String, String> query) {
  final int limit = (int.tryParse(query['limit'] ?? '') ?? 200).clamp(1, 1000);
  final String? rawAfter = query['after'];
  final List<_LogLine> lines;
  if (rawAfter == null) {
    lines = state.logs.sublist(math.max(0, state.logs.length - limit));
  } else {
    final int? after = int.tryParse(rawAfter);
    if (after == null) {
      throw const _Reject(HttpStatus.badRequest, 'after must be an integer');
    }
    lines = state.logs
        .where((_LogLine line) => line.seq > after)
        .take(limit)
        .toList();
  }
  return <String, Object?>{
    'data': <Object?>[
      for (final _LogLine line in lines)
        rawAfter == null ? line.line : line.toJson(),
    ],
    'lastSeq': lines.isEmpty ? state.logSeq : lines.last.seq,
  };
}

/// An action run: queued, running after 0.5 s, done (or failed when
/// [fails]) after 1.5 s. `message` stays empty until it ends.
final class _Ticket {
  static const Map<String, String> _doneMessages = <String, String>{
    'action-incident-playbook': 'Incident playbook finished',
    'action-prewarm-critical-chunks': 'Prewarmed critical chunks',
    'action-quarantine-hot-chunks': 'Quarantined hot chunks',
    'action-trim-entities-by-age-priority': 'Trimmed entities by age',
    'action-hopper-network-normalize': 'Normalized hopper networks',
    'capture-profile': 'Captured profile',
    'collect-garbage': 'Collected garbage',
    'purge-chunks': 'Purged idle chunks',
    'purge-entities': 'Purged entities',
  };

  final String id;
  final String actionId;
  final String actionName;
  final DateTime createdAt;
  final bool fails;

  const _Ticket(
    this.id,
    this.actionId,
    this.actionName,
    this.createdAt, {
    this.fails = false,
  });

  Map<String, Object?> toJson(DateTime now) {
    final int elapsed = now.difference(createdAt).inMilliseconds;
    final String state = elapsed < 500
        ? 'queued'
        : elapsed < 1500
        ? 'running'
        : fails
        ? 'failed'
        : 'done';
    return <String, Object?>{
      'id': id,
      'state': state,
      'message': switch (state) {
        'done' => _doneMessages[actionId] ?? '$actionName finished',
        'failed' => '$actionName failed: the server rejected the request',
        _ => '',
      },
    };
  }
}

/// The plugin's history cursor: the query and the start of the next page.
final class _HistoryCursor {
  final List<String> ids;
  final int from;
  final int to;
  final int pageFrom;
  final int resolution;
  final int sequence;
  final int through;
  final int pagePoints;

  const _HistoryCursor({
    required this.ids,
    required this.from,
    required this.to,
    required this.pageFrom,
    required this.resolution,
    required this.sequence,
    required this.through,
    required this.pagePoints,
  });

  _HistoryCursor next(int pageTo) => _HistoryCursor(
    ids: ids,
    from: from,
    to: to,
    pageFrom: pageTo,
    resolution: resolution,
    sequence: sequence,
    through: through,
    pagePoints: pagePoints,
  );

  String encode() => base64Url
      .encode(
        utf8.encode(
          jsonEncode(<String, Object?>{
            'ids': ids,
            'from': from,
            'to': to,
            'pageFrom': pageFrom,
            'resolution': resolution,
            'sequence': sequence,
            'through': through,
            'pagePoints': pagePoints,
          }),
        ),
      )
      .replaceAll('=', '');

  static _HistoryCursor decode(String raw) {
    try {
      final Map<String, dynamic> json = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(raw))),
      ) as Map<String, dynamic>;
      return _HistoryCursor(
        ids: (json['ids'] as List<dynamic>).cast<String>(),
        from: json['from'] as int,
        to: json['to'] as int,
        pageFrom: json['pageFrom'] as int,
        resolution: json['resolution'] as int,
        sequence: json['sequence'] as int,
        through: json['through'] as int,
        pagePoints: json['pagePoints'] as int,
      );
    } on Object {
      throw const _Reject(HttpStatus.badRequest, 'Invalid cursor');
    }
  }
}

/// `GET /api/v1/summary`: the start page's row, about 450 bytes.
Map<String, Object?> _summary(
  _FakeServer state,
  FakeToken token,
  DateTime now,
) {
  final Map<String, dynamic> incidents = FakeFixtures.incidents(
    state.profile,
    now,
  );
  double? value(String id) {
    for (final Map<String, dynamic> sampler in FakeFixtures.metricData(
      state.profile,
      now,
    )) {
      if (sampler['id'] == id && !state.unavailable.containsKey(id)) {
        return (sampler['value'] as num).toDouble();
      }
    }
    return null;
  }

  final double? players = value('players');
  final double? tps = value('ticks-per-second');
  final double? tick = value('tick-time');
  final List<dynamic> events =
      incidents['incidents'] as List<dynamic>? ?? const <dynamic>[];
  return <String, Object?>{
    'serverName': state.profile.label,
    'fingerprint': state.profile.fingerprint,
    'capturedAtMs': now.millisecondsSinceEpoch,
    'players': players?.round(),
    'tps': tps == null ? null : double.parse(tps.toStringAsFixed(1)),
    'tickMs': tick == null ? null : double.parse(tick.toStringAsFixed(1)),
    'incident': <String, Object?>{
      'state': state.profile.health == ServerHealth.healthy
          ? 'NORMAL'
          : 'ACTIVE',
      'open': state.profile.health == ServerHealth.healthy
          ? 0
          : (events.length / 4).ceil(),
    },
    'pairing': token.toJson(),
  };
}
