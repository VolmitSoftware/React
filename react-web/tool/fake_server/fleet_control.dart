part of 'fleet.dart';

/// Control routes for tests, not in the plugin. `server` takes a slug
/// (`s1`) or a number and defaults to `s1`.
///
/// - `GET /__fake/health`, `GET /__fake/servers` (each with a new admin
///   pairing link), `GET /__fake/state?server=`
/// - `POST /__fake/pair?server=&role=viewer|operator|admin&label=`
///   `&lifetime=<seconds>&linkSeconds=<seconds>`: a pairing link as
///   `/react web pair` prints one (one-time, 600 s unless linkSeconds)
/// - `POST /__fake/revoke?server=&tokenId=`: an unused link is cancelled
///   (`pairing_revoked`), a token ends (`token_revoked`, sockets close 4401);
///   or `&role=` for every token of that role
/// - `POST /__fake/expire?server=&tokenId=`: the pairing ends unused
///   (`token_expired`, sockets close 4401)
/// - `POST /__fake/down?server=&down=true|false`: the server stops answering
///   (connections drop) or comes back
/// - `POST /__fake/origins?allow=<origin>,<origin>`: the fleet's
///   allowedOrigins from now on (empty allows no page)
/// - `POST /__fake/mutate?server=&kind=tweaks|features|config|worlds&id=`
/// - `POST /__fake/counter?server=&add=<ms>`
/// - `POST /__fake/logs?server=&quiet=true|false`,
///   `POST /__fake/log?server=&line=`, `POST /__fake/log-burst?server=&count=`
/// - `POST /__fake/drop-sockets?server=&kind=logs|metrics|all`
/// - `POST /__fake/throttle?server=&count=&retryAfter=`
/// - `POST /__fake/fail-next-action?server=`
/// - `POST /__fake/sampler?server=&id=&available=true|false&reason=`
extension _FleetControl on FakeFleet {
  Future<void> _handleControl(HttpRequest request, List<String> route) async {
    final String name = route.isEmpty ? '' : route.first;
    final Map<String, String> query = request.uri.queryParameters;
    switch (name) {
      case 'health':
        await _writeJson(request, <String, Object?>{
          'status': 'ready',
          'port': port,
          'origins': allowedOrigins,
          'servers': _servers.length,
        });
      case 'servers':
        await _writeJson(request, <String, Object?>{
          'servers': <Map<String, Object?>>[
            for (final _FakeServer state in _servers.values)
              <String, Object?>{
                'slug': state.profile.slug,
                'name': state.profile.label,
                'health': state.profile.health.name,
                'directUrl': directUrl(state.profile).toString(),
                'fingerprint': state.profile.fingerprint,
                'pairingLink': pair(state.profile).link,
              },
          ],
        });
      case 'state':
        _expect(request, 'GET');
        await _writeJson(request, _stateOf(_controlServer(query)));
      case 'pair':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final String role = query['role'] ?? 'admin';
        if (!roleScopes.containsKey(role)) {
          throw _Reject(HttpStatus.badRequest, 'Unknown role $role');
        }
        final ({String link, FakeLink pending}) paired = pair(
          state.profile,
          role: role,
          label: query['label'] ?? 'React Web',
          lifetime: Duration(seconds: _intParam(query, 'lifetime', 604800)),
          linkLife: Duration(seconds: _intParam(query, 'linkSeconds', 600)),
        );
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'role': role,
          'tokenId': paired.pending.tokenId,
          'link': paired.link,
          'code': paired.link.substring(paired.link.indexOf('#') + 1),
          'expiresAt': _instant(paired.pending.expiresAt),
        });
      case 'expire':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final FakeToken token =
            state.tokens[query['tokenId']] ??
            (throw _Reject(
              HttpStatus.notFound,
              'Unknown token ${query['tokenId']}',
            ));
        token.ended = 'token_expired';
        final int closed = state.closeSockets(
          logs: true,
          metrics: true,
          code: 4401,
          reason: 'token_expired',
          tokenId: token.id,
        );
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'expired': token.id,
          'closedSockets': closed,
        });
      case 'origins':
        _expect(request, 'POST');
        allowedOrigins
          ..clear()
          ..addAll(
            (query['allow'] ?? '')
                .split(',')
                .where((String each) => each.isNotEmpty),
          );
        await _writeJson(request, <String, Object?>{'origins': allowedOrigins});
      case 'down':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        state.down = switch (query['down']) {
          'true' => true,
          'false' => false,
          _ => throw const _Reject(
            HttpStatus.badRequest,
            'down must be true or false',
          ),
        };
        if (state.down) {
          state.closeSockets(
            logs: true,
            metrics: true,
            code: WebSocketStatus.goingAway,
            reason: 'Server stopping',
          );
        }
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'down': state.down,
        });
      case 'mutate':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final String kind = query['kind'] ?? '';
        final String id = query['id'] ?? '';
        if (id.isEmpty) {
          throw const _Reject(HttpStatus.badRequest, 'id is required');
        }
        final Map<String, dynamic> item = _mutate(state, kind, id);
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'kind': kind,
          'revision': state.revisions[kind],
          'item': item,
        });
      case 'counter':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final int add = _intParam(query, 'add', 10000000);
        final int now = DateTime.now().millisecondsSinceEpoch;
        for (final String id in state.tokens.keys) {
          state.counters[id] = (state.counters[id] ?? now) + add;
        }
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'add': add,
          'counters': state.counters,
        });
      case 'revoke':
        _expect(request, 'POST');
        await _revoke(request, _controlServer(query), query);
      case 'logs':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        state.quiet = switch (query['quiet']) {
          'true' => true,
          'false' => false,
          _ => throw const _Reject(
            HttpStatus.badRequest,
            'quiet must be true or false',
          ),
        };
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'quiet': state.quiet,
        });
      case 'log':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final String text = query['line'] ?? '';
        if (text.isEmpty) {
          throw const _Reject(HttpStatus.badRequest, 'line is required');
        }
        final _LogLine line = state.addLog(
          '[${_clock(DateTime.now())} INFO]: $text',
        );
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          ...line.toJson(),
        });
      case 'log-burst':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final int count = _intParam(query, 'count', 100);
        if (count < 1 || count > 100000) {
          throw const _Reject(
            HttpStatus.badRequest,
            'count must be between 1 and 100000',
          );
        }
        final String time = _clock(DateTime.now());
        final int firstSeq = state.logSeq + 1;
        for (int n = 1; n <= count; n++) {
          state.addLog('[$time INFO]: Burst line $n of $count');
        }
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'count': count,
          'firstSeq': firstSeq,
          'lastSeq': state.logSeq,
        });
      case 'drop-sockets':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final String kind = query['kind'] ?? 'all';
        if (!const <String>{'logs', 'metrics', 'all'}.contains(kind)) {
          throw const _Reject(
            HttpStatus.badRequest,
            'kind must be logs, metrics or all',
          );
        }
        final int closed = state.closeSockets(
          logs: kind != 'metrics',
          metrics: kind != 'logs',
          code: WebSocketStatus.goingAway,
          reason: 'Connection Idle Timeout',
        );
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'kind': kind,
          'closed': closed,
        });
      case 'throttle':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        state.throttleCount = math.max(0, _intParam(query, 'count', 1));
        state.throttleRetryAfter = math.max(
          0,
          _intParam(query, 'retryAfter', 1),
        );
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'count': state.throttleCount,
          'retryAfter': state.throttleRetryAfter,
        });
      case 'fail-next-action':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        state.failNextAction = true;
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'failNextAction': true,
        });
      case 'sampler':
        _expect(request, 'POST');
        final _FakeServer state = _controlServer(query);
        final String id = query['id'] ?? '';
        if (!metricFixtures.any((MetricFixture fixture) => fixture.id == id)) {
          throw _Reject(HttpStatus.notFound, 'Unknown sampler: $id');
        }
        switch (query['available']) {
          case 'true':
            state.unavailable.remove(id);
          case 'false':
            state.unavailable[id] = query['reason'] ?? 'Sampler unavailable';
          default:
            throw const _Reject(
              HttpStatus.badRequest,
              'available must be true or false',
            );
        }
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'id': id,
          'available': !state.unavailable.containsKey(id),
          'unavailableReason': state.unavailable[id],
        });
      default:
        throw const _Reject(HttpStatus.notFound, 'Unknown control route');
    }
  }

  _FakeServer _controlServer(Map<String, String> query) {
    final String key = query['server'] ?? 's1';
    final _FakeServer? state =
        _servers[key] ?? _servers['s${int.tryParse(key) ?? 0}'];
    if (state == null) {
      throw _Reject(HttpStatus.notFound, 'Unknown server $key');
    }
    return state;
  }

  Map<String, Object?> _stateOf(_FakeServer state) => <String, Object?>{
    'server': state.profile.slug,
    'revisions': state.revisions,
    'tweaks': <String, Object?>{
      for (final Map<String, dynamic> tweak in state.tweaks)
        tweak['id'] as String: tweak['enabled'],
    },
    'features': <String, Object?>{
      for (final Map<String, dynamic> feature in state.features)
        feature['id'] as String: feature['enabled'],
    },
    'worlds': <String, Object?>{
      for (final Map<String, dynamic> world in state.worlds)
        world['name'] as String: <String, Object?>{
          'budgetMs': world['budgetMs'],
          'panicMs': world['panicMs'],
          'releaseMs': world['releaseMs'],
        },
    },
    'counters': state.counters,
    'revoked': <String>[
      for (final FakeToken token in state.tokens.values)
        if (token.ended == 'token_revoked') token.id,
    ],
    'links': <String, Object?>{
      for (final FakeLink link in state.links.values) link.tokenId: link.state,
    },
    'tokens': <String, Object?>{
      for (final FakeToken token in state.tokens.values)
        token.id: <String, Object?>{
          ...token.toJson(),
          'ended': token.ended,
          'client': token.client,
        },
    },
    'requests': state.requests.toList(),
  };

  /// Changes one item the way a hotload or the in-game GUI would and bumps
  /// its kind's revision.
  Map<String, dynamic> _mutate(_FakeServer state, String kind, String id) {
    final Map<String, dynamic> item;
    switch (kind) {
      case 'tweaks' || 'features':
        item = _findItem(
          kind == 'tweaks' ? state.tweaks : state.features,
          'id',
          id,
        );
        item['enabled'] = item['enabled'] != true;
      case 'worlds':
        item = _findItem(state.worlds, 'name', id);
        item['budgetMs'] = (item['budgetMs'] as num).toDouble() + 1.0;
      case 'config':
        item =
            _configNode(state.config, id) ??
            (throw _Reject(HttpStatus.notFound, 'Unknown config key: $id'));
        item['value'] = _nextConfigValue(item);
      default:
        throw const _Reject(
          HttpStatus.badRequest,
          'kind must be tweaks, features, config or worlds',
        );
    }
    state.bump(kind);
    return item;
  }

  Future<void> _revoke(
    HttpRequest request,
    _FakeServer state,
    Map<String, String> query,
  ) async {
    final String? tokenId = query['tokenId'];
    final String? role = query['role'];
    final List<String> ids;
    if (tokenId != null) {
      final FakeLink? pending = state.links[tokenId];
      if (pending != null && pending.state == 'open') {
        pending.state = 'revoked';
        await _writeJson(request, <String, Object?>{
          'server': state.profile.slug,
          'revoked': <String>[tokenId],
          'closedSockets': 0,
        });
        return;
      }
      if (!state.tokens.containsKey(tokenId)) {
        throw _Reject(HttpStatus.notFound, 'Unknown token $tokenId');
      }
      ids = <String>[tokenId];
    } else if (role != null && roleScopes.containsKey(role)) {
      ids = <String>[
        for (final FakeToken token in state.tokens.values)
          if (token.role == role && token.id != state.builtInAdminId) token.id,
      ];
    } else {
      throw const _Reject(
        HttpStatus.badRequest,
        'tokenId or role (viewer, operator, admin) is required',
      );
    }
    int closed = 0;
    for (final String id in ids) {
      state.tokens[id]!.ended = 'token_revoked';
      closed += state.closeSockets(
        logs: true,
        metrics: true,
        code: 4401,
        reason: 'token_revoked',
        tokenId: id,
      );
    }
    await _writeJson(request, <String, Object?>{
      'server': state.profile.slug,
      'revoked': ids,
      'closedSockets': closed,
    });
  }
}

Map<String, dynamic> _findItem(
  List<Map<String, dynamic>> items,
  String key,
  String id,
) => items.firstWhere(
  (Map<String, dynamic> item) => item[key] == id,
  orElse: () => throw _Reject(HttpStatus.notFound, 'Unknown item: $id'),
);

/// The next value of a config node: the next option, the other boolean,
/// one more for numbers, a marked string.
Object? _nextConfigValue(Map<String, dynamic> node) {
  final Object? value = node['value'];
  final List<dynamic> options =
      node['options'] as List<dynamic>? ?? const <dynamic>[];
  if (options.isNotEmpty) {
    return options[(options.indexOf(value) + 1) % options.length];
  }
  return switch (value) {
    final bool flag => !flag,
    final int number => number + 1,
    final double number => number + 1.0,
    final String text => '$text (changed)',
    _ => value,
  };
}

int _intParam(Map<String, String> query, String key, int fallback) {
  final String? raw = query[key];
  if (raw == null || raw.isEmpty) return fallback;
  final int? value = int.tryParse(raw);
  if (value == null) {
    throw _Reject(HttpStatus.badRequest, '$key must be an integer');
  }
  return value;
}
