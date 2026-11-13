import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:test/test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/environment_info.dart';
import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_summary.dart';
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/plugin_api_pack.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/direct_web_socket_handshake.dart';
import 'package:react_web/service/pairing_redeemer.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

import '../../tool/fake_server/fixtures.dart';
import '../../tool/fake_server/fleet.dart';
import '../../tool/fake_server/metric_fixtures.dart';

const String _origin = FakeFleet.defaultOrigin;

void main() {
  late FakeFleet fleet;
  late FakeServerProfile survival;
  late FakeServerProfile skyblock;

  setUpAll(() async {
    fleet = await FakeFleet.start(port: 0, servers: 3);
    survival = fleet.profiles[0];
    skyblock = fleet.profiles[1];
  });

  tearDownAll(() async {
    await fleet.close();
  });

  ReactClient clientFor(FakeServerProfile profile, {FakeToken? token}) {
    final http.Client transport = http.Client();
    addTearDown(transport.close);
    return ReactClient(
      _credential(fleet, profile, token ?? fleet.adminToken(profile)),
      client: transport,
    );
  }

  Future<Map<String, dynamic>> mint(String query) async =>
      _json(await http.post(fleet.baseUri.resolve('/__fake/pair?$query')));

  PairingRedeemer redeemer({DateTime Function()? now}) =>
      PairingRedeemer(now: now);

  group('fake server pairing', () {
    test('links parse through the production link parser', () async {
      for (final FakeServerProfile profile in fleet.profiles) {
        final PairingLink link = PairingLink.parse(fleet.pair(profile).link)!;
        expect(link.directUrl, equals('${fleet.baseUri}/${profile.slug}'));
        expect(link.relayUrl, isNull);
        expect(link.fingerprint, equals(profile.fingerprint));
        expect(link.serverName, equals(profile.label));
        expect(link.role, equals('admin'));
        expect(link.scopes, <String>[
          'read',
          'op:execute',
          'admin',
          'console:read',
          'console:execute',
        ]);
        expect(
          link.expiresAt.difference(DateTime.now()).inMinutes,
          inInclusiveRange(9, 10),
        );
      }
      expect(
        fleet.profiles.map((FakeServerProfile p) => p.fingerprint).toSet(),
        hasLength(3),
      );
    });

    test('a link redeems once through the production redeemer', () async {
      final PairingLink link = PairingLink.parse(
        (await mint('server=s2&role=viewer&label=laptop'))['link'] as String,
      )!;
      final PairedToken token = await redeemer().redeem(
        link,
        client: 'Chrome on macOS',
      );
      expect(token.role, 'viewer');
      expect(token.scopes, <String>['read']);
      expect(token.serverName, 'Skyblock');
      expect(token.path, PairPath.direct);
      final FakeToken stored = FakeToken(
        id: token.tokenId,
        label: 'laptop',
        role: 'viewer',
        bearer: token.bearer,
        lifetime: token.lifetime,
        expiresAt: token.expiresAt,
      );
      final ReactClient viewer = clientFor(skyblock, token: stored);
      expect((await viewer.whoami()).role, equals('viewer'));
      await expectLater(viewer.players(), throwsA(isA<Exception>()));
      await expectLater(viewer.logs(), throwsA(isA<Exception>()));
      await expectLater(
        redeemer().redeem(link),
        throwsA(
          isA<PairingRefused>().having(
            (PairingRefused e) => e.failure,
            'failure',
            PairingFailure.used,
          ),
        ),
      );
      final Map<String, dynamic> state = _json(
        await http.get(fleet.baseUri.resolve('/__fake/state?server=s2')),
      );
      expect(
        (state['tokens'] as Map<String, dynamic>)[token.tokenId]['client'],
        'Chrome on macOS',
      );
    });

    test('a revoked, expired or forged link says which', () async {
      final Map<String, dynamic> cancelled = await mint('server=s1');
      await http.post(
        fleet.baseUri.resolve(
          '/__fake/revoke?server=s1&tokenId=${cancelled['tokenId']}',
        ),
      );
      await expectLater(
        redeemer().redeem(PairingLink.parse(cancelled['link'] as String)!),
        throwsA(
          isA<PairingRefused>().having(
            (PairingRefused e) => e.failure,
            'failure',
            PairingFailure.revoked,
          ),
        ),
      );

      final Map<String, dynamic> late = await mint('server=s1&linkSeconds=0');
      final PairingLink lateLink = PairingLink.parse(late['link'] as String)!;
      await expectLater(
        redeemer(now: () => DateTime.utc(2020)).redeem(lateLink),
        throwsA(
          isA<PairingRefused>().having(
            (PairingRefused e) => e.failure,
            'failure',
            PairingFailure.expired,
          ),
        ),
      );

      final PairingLink link = PairingLink.parse(
        (await mint('server=s1'))['link'] as String,
      )!;
      final http.Response forged = await http.post(
        fleet.baseUri.resolve('/s1/api/v1/pair'),
        body: jsonEncode(<String, String>{
          'tokenId': link.tokenId,
          'secret': 'A' * 43,
        }),
      );
      expect(forged.statusCode, HttpStatus.unauthorized);
      expect(_json(forged)['error']['code'], 'pairing_unknown');
    });

    test(
      'an origin outside allowedOrigins is refused in a way the page can read',
      () async {
        final PairingLink link = PairingLink.parse(
          (await mint('server=s1'))['link'] as String,
        )!;
        final http.Response refused = await http.post(
          fleet.baseUri.resolve('/s1/api/v1/pair'),
          headers: <String, String>{
            'Origin': 'http://evil.example',
            'Content-Type': 'application/json',
          },
          body: jsonEncode(<String, String>{
            'tokenId': link.tokenId,
            'secret': link.secret,
          }),
        );
        expect(refused.statusCode, HttpStatus.forbidden);
        expect(
          refused.headers[HttpHeaders.accessControlAllowOriginHeader],
          'http://evil.example',
        );
        final Map<String, dynamic> error =
            _json(refused)['error'] as Map<String, dynamic>;
        expect(error['code'], 'origin_not_allowed');
        expect(error['origin'], 'http://evil.example');
        expect(error['configKey'], 'allowedOrigins');
        expect(error['configFile'], 'plugins/React/web.toml');
        expect(
          (await redeemer().redeem(link)).role,
          'admin',
          reason: 'the link stays usable',
        );
      },
    );

    test('ended pairings answer token_revoked and token_expired', () async {
      Future<FakeToken> paired() async {
        final PairedToken token = await redeemer().redeem(
          PairingLink.parse((await mint('server=s1'))['link'] as String)!,
        );
        return FakeToken(
          id: token.tokenId,
          label: '',
          role: 'admin',
          bearer: token.bearer,
          lifetime: token.lifetime,
          expiresAt: token.expiresAt,
        );
      }

      final FakeToken revoked = await paired();
      final FakeToken expired = await paired();
      await http.post(
        fleet.baseUri.resolve('/__fake/revoke?server=s1&tokenId=${revoked.id}'),
      );
      await http.post(
        fleet.baseUri.resolve('/__fake/expire?server=s1&tokenId=${expired.id}'),
      );
      await expectLater(
        clientFor(survival, token: revoked).whoami(),
        throwsA(isA<ReactRevoked>()),
      );
      await expectLater(
        clientFor(survival, token: expired).whoami(),
        throwsA(isA<ReactRevoked>()),
      );
      final http.Response body = await http.get(
        fleet.baseUri.resolve('/s1/api/v1/whoami'),
        headers: <String, String>{
          HttpHeaders.authorizationHeader: 'Bearer ${expired.bearer}',
        },
      );
      expect(_json(body)['error']['code'], 'token_expired');
    });

    test(
      'summary and whoami carry the pairing end, which moves with use',
      () async {
        final ReactClient client = clientFor(survival);
        final ServerSummary summary = await client.summary();
        expect(summary.serverName, 'Survival');
        expect(summary.fingerprint, survival.fingerprint);
        expect(summary.players, isNotNull);
        expect(summary.tps, isNotNull);
        expect(
          summary.pairingEndsAt!.difference(DateTime.now()).inDays,
          inInclusiveRange(6, 7),
        );
        expect((await client.whoami()).expiresAt, isNotNull);
        final http.Response raw = await http.get(
          fleet.baseUri.resolve('/s1/api/v1/summary'),
          headers: <String, String>{
            HttpHeaders.authorizationHeader:
                'Bearer ${fleet.adminToken(survival).bearer}',
          },
        );
        expect(raw.body.length, lessThan(700));
      },
    );

    test('a stopped server drops connections until it is back', () async {
      await http.post(
        fleet.baseUri.resolve('/__fake/down?server=s3&down=true'),
      );
      final ReactClient client = clientFor(fleet.profiles[2]);
      await expectLater(client.ping(), throwsA(isA<ReactUnavailable>()));
      await http.post(
        fleet.baseUri.resolve('/__fake/down?server=s3&down=false'),
      );
      expect((await client.ping()).protocolVersion, 3);
    });

    test('rejects a forged bearer and a replayed counter', () async {
      final FakeToken token = fleet.adminToken(survival);
      final Uri identity = fleet.baseUri.resolve('/s1/api/v1/identity');
      final http.Response forged = await http.get(
        identity,
        headers: <String, String>{
          HttpHeaders.authorizationHeader: 'Bearer ${token.id}.AAAA',
        },
      );
      final Uri world = fleet.baseUri.resolve('/s1/api/v1/worlds/spawn');
      final Map<String, String> headers = <String, String>{
        HttpHeaders.authorizationHeader: 'Bearer ${token.bearer}',
        HttpHeaders.contentTypeHeader: 'application/json',
        'X-React-Counter': '900000',
      };
      final http.Response first = await http.put(
        world,
        headers: headers,
        body: '{"budgetMs":9}',
      );
      final http.Response replay = await http.put(
        world,
        headers: headers,
        body: '{"budgetMs":9}',
      );
      final http.Response other = await http.get(
        fleet.baseUri.resolve('/s2/api/v1/identity'),
        headers: <String, String>{
          HttpHeaders.authorizationHeader: 'Bearer ${token.bearer}',
        },
      );

      expect(forged.statusCode, equals(HttpStatus.unauthorized));
      expect(first.statusCode, equals(HttpStatus.ok));
      expect(replay.statusCode, equals(HttpStatus.conflict));
      expect(_json(replay)['error']['code'], 'counter_conflict');
      expect(_json(replay)['error']['lastCounter'], 900000);
      expect(other.statusCode, equals(HttpStatus.unauthorized));
    });
  });

  group('fake server protocol', () {
    test('serves a stored chart range like the plugin', () async {
      final ReactClient client = clientFor(skyblock);
      final DateTime before = DateTime.now();
      final RangeHistory hour = await client.metricRange(ChartRange.hour);
      final RangeHistory day = await client.metricRange(
        ChartRange.day,
        ids: const <String>['tick-time', 'no-such-sampler'],
      );
      final FakeToken token = fleet.adminToken(skyblock);
      final http.Response unknown = await http.get(
        fleet.baseUri.resolve(
          '/${skyblock.slug}/api/v1/metrics/range?range=7d',
        ),
        headers: <String, String>{
          HttpHeaders.authorizationHeader: 'Bearer ${token.bearer}',
        },
      );

      final int step = ChartRange.hour.step.inMilliseconds;
      expect(
        hour.to.millisecondsSinceEpoch,
        lessThanOrEqualTo(DateTime.now().millisecondsSinceEpoch),
      );
      expect(
        hour.to.millisecondsSinceEpoch,
        greaterThan(before.millisecondsSinceEpoch - 2 * step),
      );
      expect(hour.to.millisecondsSinceEpoch % step, 0);
      final RangeView ticks = hour.view(const <String>[
        'tick-time',
        'tick-ms-p95',
        'memory-used',
      ]);
      expect(ticks.times, hasLength(ChartRange.buckets));
      expect(ticks.values.keys, <String>[
        'tick-time',
        'tick-ms-p95',
        'memory-used',
      ]);
      expect(
        day.view(const <String>['tick-time']).times,
        hasLength(ChartRange.buckets),
      );
      expect(day.view(const <String>['memory-used']).isEmpty, isTrue);
      expect(unknown.statusCode, equals(HttpStatus.badRequest));
    });

    test('serves every read model at real scale', () async {
      final ReactClient client = clientFor(skyblock);
      final DateTime now = DateTime.now();

      final ServerCapabilities capabilities = await client.ping();
      final IdentityInfo identity = await client.identity();
      final RoleInfo role = await client.whoami();
      final PluginApiCatalog pluginApiCatalog = await client.pluginApiPacks();
      final ServerSnapshot snapshot = await client.metrics();
      final List<MetricHistoryDescriptor> historyCatalog = await client
          .historyCatalog();
      final MetricHistoryPage day = await client.historyPage(
        ids: <String>['tick-time', 'ticks-per-second'],
        from: now.subtract(const Duration(hours: 24)),
        to: now,
      );
      final List<HeatmapSummary> heatmaps = await client.heatmaps();
      final HeatmapGrid heatmap = await client.heatmap(
        heatmaps.first.id,
        world: 'minecraft:world_the_end',
        centerChunkX: 31,
        centerChunkZ: -17,
        radius: 3,
      );
      final List<ControlItem> features = await client.features();
      final ControlItem feature = await client.feature('mob-stacking');
      final List<ControlItem> tweaks = await client.tweaks();
      final List<WorldSettings> worlds = await client.worlds();
      final List<OnlinePlayerInfo> players = await client.players();
      final List<ActionDescriptor> actions = await client.actions();
      final IncidentStatus incidents = await client.incidents();
      final EnvironmentInfo environment = await client.environment();
      final ConfigTree config = await client.config();
      final List<String> logs = await client.logs(limit: 500);

      expect(capabilities.serverFingerprint, equals(identity.serverId));
      expect(identity.serverName, equals('Skyblock'));
      expect(role.isAdmin, isTrue);
      expect(pluginApiCatalog.packs, hasLength(2));
      expect(snapshot.byId, hasLength(metricFixtures.length));
      expect(metricFixtures.length, greaterThanOrEqualTo(79));
      expect(snapshot.sampler('players')!.value, equals(40));
      expect(snapshot.sampler('ticks-per-second')!.value, lessThan(12));
      expect(historyCatalog, hasLength(metricFixtures.length));
      expect(day.resolution, equals(const Duration(minutes: 15)));
      expect(day.series, hasLength(2));
      expect(day.series.first.points.length, inInclusiveRange(95, 97));
      expect(day.nextCursor, isNull);
      expect(heatmap.world, equals('minecraft:world_the_end'));
      expect(heatmap.radius, equals(3));
      expect(heatmap.cells, isNotEmpty);
      expect(features.length, greaterThanOrEqualTo(40));
      expect(tweaks.length, greaterThanOrEqualTo(15));
      expect(feature.name, equals('Mob Stacking'));
      expect(feature.knobs, isNotEmpty);
      expect(worlds, hasLength(5));
      expect(players, hasLength(40));
      expect(players.map((OnlinePlayerInfo p) => p.id).toSet(), hasLength(40));
      expect(actions.length, equals(9));
      expect(incidents.incidents.length, equals(12));
      expect(incidents.contributors, isNotEmpty);
      expect(environment.disks, isNotEmpty);
      expect(config.node('tick-budget-ms')!.label, equals('Tick Budget'));
      expect(logs.length, equals(400));
    });

    test('pages history with the cursor', () async {
      final ReactClient client = clientFor(survival);
      final DateTime now = DateTime.now();
      final MetricHistoryPage first = await client.historyPage(
        ids: <String>['tick-time'],
        from: now.subtract(const Duration(hours: 1)),
        to: now,
      );
      final MetricHistoryPage second = await client.historyPage(
        cursor: first.nextCursor,
      );

      expect(first.resolution, equals(const Duration(seconds: 10)));
      expect(first.series.single.points, hasLength(256));
      expect(first.nextCursor, isNotNull);
      expect(second.series.single.points.length, inInclusiveRange(103, 105));
      expect(second.nextCursor, isNull);
    });

    test('keeps mutations inside the server session', () async {
      final ReactClient client = clientFor(survival);

      final ControlItem disabled = await client.setFeatureEnabled(
        'mob-stacking',
        false,
      );
      final ControlItem configured = await client.setFeatureConfig(
        'mob-stacking',
        <String, Object?>{'maxStackSize': 64},
      );
      final WorldSettings world = await client.setWorld(
        'world_nether',
        budgetMs: 21.0,
      );
      final ActionTicket ticket = await client.executeAction(
        'collect-garbage',
        params: const <String, Object?>{},
        confirm: false,
      );
      final ConfigTree preset = await client.applyPreset('high');
      final bool dispatched = await client.executeConsole('react status');
      final List<String> logs = await client.logs();
      final PluginApiCatalog removed = await client.removePluginApiPack(
        (await client.installPluginApiPack(
          'community.example',
          'schema = "react.plugin-api/v1"\nid = "community.example"\n[[metrics]]\n',
        )).id,
      );

      expect(disabled.enabled, isFalse);
      expect(
        configured.knobs
            .singleWhere((Knob knob) => knob.key == 'maxStackSize')
            .intValue,
        equals(64),
      );
      expect(world.budgetMs, equals(21.0));
      expect(ticket.ticketId, equals('s1-ticket-1'));
      expect(preset.node('tick-budget-ms')!.doubleValue, equals(40.0));
      expect(dispatched, isTrue);
      expect(logs.last, endsWith('issued server command: /react status'));
      expect(
        removed.packs.map((PluginApiPack pack) => pack.id),
        isNot(contains('community.example')),
      );
      expect(
        (await clientFor(skyblock).feature('mob-stacking')).enabled,
        isTrue,
      );
    });

    test('streams metric and log frames on the plugin socket paths', () async {
      final ServerCredential credential = _credential(
        fleet,
        skyblock,
        fleet.adminToken(skyblock),
      );
      final DirectWebSocketHandshake metricsHandshake =
          DirectWebSocketHandshake(credential, 'ws/metrics');
      final WebSocket metricsSocket = await WebSocket.connect(
        metricsHandshake.endpoint.toString(),
        headers: <String, dynamic>{'Origin': _origin},
      );
      addTearDown(metricsSocket.close);
      metricsSocket.add(metricsHandshake.authFrame);
      final List<ServerSnapshot> frames = await metricsSocket
          .take(2)
          .map(
            (dynamic raw) => ServerSnapshot.fromJson(
              jsonDecode(raw as String) as Map<String, dynamic>,
            ),
          )
          .toList()
          .timeout(const Duration(seconds: 5));

      final DirectWebSocketHandshake logsHandshake = DirectWebSocketHandshake(
        credential,
        'ws/logs',
      );
      final WebSocket logsSocket = await WebSocket.connect(
        logsHandshake.endpoint.toString(),
        headers: <String, dynamic>{'Origin': _origin},
      );
      addTearDown(logsSocket.close);
      logsSocket.add(logsHandshake.authFrame);
      final Map<String, dynamic> logFrame =
          jsonDecode(await logsSocket.first as String) as Map<String, dynamic>;

      expect(frames.last.seq, greaterThan(frames.first.seq));
      expect(frames.first.byId, hasLength(metricFixtures.length));
      expect(logFrame['type'], equals('log'));
      expect(
        logFrame['line'],
        matches(RegExp(r'^\[\d\d:\d\d:\d\d [A-Z]+\]: ')),
      );
      expect(logFrame['seq'], isA<int>());
    });

    test('closes a socket whose first frame is not an auth frame', () async {
      final WebSocket socket = await WebSocket.connect(
        'ws://127.0.0.1:${fleet.port}/s2/ws/metrics'
        '?token=${fleet.adminToken(skyblock).bearer}',
        headers: <String, dynamic>{'Origin': _origin},
      );
      addTearDown(socket.close);
      socket.add('{"type":"not-auth"}');

      await expectLater(socket, emitsDone);
    });
  });

  group('fake server transport', () {
    test(
      'answers CORS for every origin and refuses the others readably',
      () async {
        final http.Response allowed = await http.get(
          fleet.baseUri.resolve('/s1/api/v1/identity'),
          headers: <String, String>{
            'Origin': _origin,
            HttpHeaders.authorizationHeader:
                'Bearer ${fleet.adminToken(survival).bearer}',
          },
        );
        final http.Response denied = await http.get(
          fleet.baseUri.resolve('/__fake/health'),
          headers: <String, String>{'Origin': 'http://localhost:8080'},
        );

        expect(allowed.statusCode, equals(HttpStatus.ok));
        expect(
          allowed.headers[HttpHeaders.accessControlAllowOriginHeader],
          equals(_origin),
        );
        expect(denied.statusCode, equals(HttpStatus.forbidden));
        expect(
          denied.headers[HttpHeaders.accessControlAllowOriginHeader],
          equals('http://localhost:8080'),
        );
        expect(_json(denied)['error']['code'], 'origin_not_allowed');
      },
    );

    test('answers browser preflight with the mutation headers', () async {
      final http.Client transport = http.Client();
      addTearDown(transport.close);
      final http.Request request = http.Request(
        'OPTIONS',
        fleet.baseUri.resolve('/s1/api/v1/features/mob-stacking'),
      );
      request.headers.addAll(<String, String>{
        'Origin': _origin,
        HttpHeaders.accessControlRequestMethodHeader: 'PUT',
        HttpHeaders.accessControlRequestHeadersHeader:
            'Authorization, Content-Type, X-React-Counter',
      });
      final http.StreamedResponse response = await transport.send(request);

      expect(response.statusCode, equals(HttpStatus.noContent));
      expect(
        response.headers[HttpHeaders.accessControlAllowHeadersHeader],
        contains('X-React-Counter'),
      );
    });
  });

  group('fake server live changes', () {
    late http.Client raw;
    int counter = 1000;

    setUpAll(() {
      raw = http.Client();
    });

    tearDownAll(() {
      raw.close();
    });

    Uri at(String path) => fleet.baseUri.resolve(path);

    Future<Map<String, dynamic>> post(String path) async {
      final http.Response response = await raw.post(at(path));
      expect(response.statusCode, equals(HttpStatus.ok), reason: response.body);
      return _json(response);
    }

    Future<Map<String, dynamic>> fakeState(String slug) async =>
        _json(await raw.get(at('/__fake/state?server=$slug')));

    Future<http.Response> api(
      String method,
      String path,
      FakeToken token, {
      Object? body,
      String? counterValue,
      bool withCounter = true,
      Map<String, String> headers = const <String, String>{},
    }) async {
      final http.Request request = http.Request(method, at(path))
        ..headers[HttpHeaders.authorizationHeader] = 'Bearer ${token.bearer}'
        ..headers.addAll(headers);
      if (withCounter && method != 'GET') {
        request.headers['X-React-Counter'] = counterValue ?? '${++counter}';
      }
      if (body != null) {
        request.headers[HttpHeaders.contentTypeHeader] = 'application/json';
        request.body = jsonEncode(body);
      }
      return http.Response.fromStream(await raw.send(request));
    }

    Future<Map<String, dynamic>> revisionsOf(
      String slug,
      FakeToken token,
    ) async =>
        ((_json(await api('GET', '/$slug/api/v1/metrics', token))['data']
                as Map<String, dynamic>)['revisions']
            as Map<String, dynamic>);

    Future<_Probe> open(
      FakeFleet target,
      String slug,
      String kind,
      FakeToken token,
    ) async {
      final WebSocket socket = await WebSocket.connect(
        'ws://127.0.0.1:${target.port}/$slug/ws/$kind',
        headers: <String, dynamic>{'Origin': _origin},
      );
      final _Probe probe = _Probe(socket);
      addTearDown(probe.close);
      socket.add(
        jsonEncode(<String, String>{'type': 'auth', 'token': token.bearer}),
      );
      return probe;
    }

    test('stamps revisions on metrics and bumps one kind per write', () async {
      final FakeToken admin = fleet.issue(fleet.profiles[2]);
      final Map<String, dynamic> before = await revisionsOf('s3', admin);
      final String tweak =
          ((await fakeState('s3'))['tweaks'] as Map<String, dynamic>)
              .keys
              .first;
      final List<http.Response> writes = <http.Response>[
        await api(
          'PUT',
          '/s3/api/v1/features/mob-stacking',
          admin,
          body: <String, Object?>{'enabled': false},
        ),
        await api(
          'PUT',
          '/s3/api/v1/features/mob-stacking/config',
          admin,
          body: <String, Object?>{'maxStackSize': 32},
        ),
        await api(
          'PUT',
          '/s3/api/v1/tweaks/$tweak',
          admin,
          body: <String, Object?>{'enabled': false},
        ),
        await api(
          'PUT',
          '/s3/api/v1/config',
          admin,
          body: <String, Object?>{'tick-budget-ms': 44.0},
        ),
        await api('POST', '/s3/api/v1/config/preset/light', admin),
        await api(
          'PUT',
          '/s3/api/v1/worlds/spawn',
          admin,
          body: <String, Object?>{'budgetMs': 9},
        ),
        await api(
          'PUT',
          '/s3/api/v1/plugin-api-packs/community.rev',
          admin,
          body: <String, Object?>{'content': 'id = "community.rev"'},
        ),
        await api('DELETE', '/s3/api/v1/plugin-api-packs/community.rev', admin),
        await api(
          'POST',
          '/s3/api/v1/actions/collect-garbage/execute',
          admin,
          body: <String, Object?>{},
        ),
        await api(
          'POST',
          '/s3/api/v1/plugin-api-packs/validate',
          admin,
          body: <String, Object?>{'content': ''},
        ),
      ];
      final Map<String, dynamic> after = await revisionsOf('s3', admin);
      final _Probe metrics = await open(fleet, 's3', 'metrics', admin);
      await _until(() => metrics.frames.isNotEmpty);

      expect(
        writes.map((http.Response response) => response.statusCode),
        everyElement(equals(HttpStatus.ok)),
      );
      expect(
        before,
        equals(<String, int>{
          'features': 1,
          'tweaks': 1,
          'config': 1,
          'worlds': 1,
          'packs': 1,
          'actions': 1,
        }),
      );
      expect(
        after,
        equals(<String, int>{
          'features': 3,
          'tweaks': 2,
          'config': 3,
          'worlds': 2,
          'packs': 3,
          'actions': 2,
        }),
      );
      expect(
        (metrics.frames.first['data'] as Map<String, dynamic>)['revisions'],
        equals(after),
      );
    });

    test('changes items out of band through the mutate route', () async {
      final Map<String, dynamic> before = await fakeState('s2');
      final Map<String, dynamic> beforeTweaks =
          before['tweaks'] as Map<String, dynamic>;
      final Map<String, dynamic> beforeRevisions =
          before['revisions'] as Map<String, dynamic>;
      final String tweak = beforeTweaks.keys.first;
      final bool tweakWas = beforeTweaks[tweak] as bool;
      final bool featureWas =
          (before['features'] as Map<String, dynamic>)['mob-stacking'] as bool;
      final double budgetWas =
          ((before['worlds'] as Map<String, dynamic>)['spawn']
                  as Map<String, dynamic>)['budgetMs']
              as double;

      final Map<String, dynamic> tweaked = await post(
        '/__fake/mutate?server=s2&kind=tweaks&id=$tweak',
      );
      final Map<String, dynamic> featured = await post(
        '/__fake/mutate?server=s2&kind=features&id=mob-stacking',
      );
      final Map<String, dynamic> world = await post(
        '/__fake/mutate?server=s2&kind=worlds&id=spawn',
      );
      final Map<String, dynamic> budget = await post(
        '/__fake/mutate?server=s2&kind=config&id=tick-budget-ms',
      );
      final Map<String, dynamic> incident = await post(
        '/__fake/mutate?server=s2&kind=config&id=incident-mode-enabled',
      );
      final Map<String, dynamic> language = await post(
        '/__fake/mutate?server=s2&kind=config&id=main.language',
      );
      final http.Response badKind = await raw.post(
        at('/__fake/mutate?server=s2&kind=packs&id=x'),
      );
      final http.Response unknown = await raw.post(
        at('/__fake/mutate?server=s2&kind=tweaks&id=no-such-tweak'),
      );
      final Map<String, dynamic> after = await fakeState('s2');
      final Map<String, dynamic> served = _json(
        await api(
          'GET',
          '/s2/api/v1/tweaks/$tweak',
          fleet.adminToken(skyblock),
        ),
      );

      expect(tweaked['server'], equals('s2'));
      expect(tweaked['kind'], equals('tweaks'));
      expect(
        tweaked['revision'],
        equals((beforeRevisions['tweaks'] as int) + 1),
      );
      expect(
        (tweaked['item'] as Map<String, dynamic>)['enabled'],
        equals(!tweakWas),
      );
      expect(
        (served['data'] as Map<String, dynamic>)['enabled'],
        equals(!tweakWas),
      );
      expect(
        (after['tweaks'] as Map<String, dynamic>)[tweak],
        equals(!tweakWas),
      );
      expect(
        (featured['item'] as Map<String, dynamic>)['enabled'],
        equals(!featureWas),
      );
      expect(
        (world['item'] as Map<String, dynamic>)['budgetMs'],
        equals(budgetWas + 1),
      );
      expect((budget['item'] as Map<String, dynamic>)['value'], equals(43.0));
      expect((incident['item'] as Map<String, dynamic>)['value'], isFalse);
      expect(
        (language['item'] as Map<String, dynamic>)['value'],
        equals('de_DE'),
      );
      expect(
        after['revisions'],
        equals(<String, Object?>{
          ...beforeRevisions,
          'tweaks': (beforeRevisions['tweaks'] as int) + 1,
          'features': (beforeRevisions['features'] as int) + 1,
          'worlds': (beforeRevisions['worlds'] as int) + 1,
          'config': (beforeRevisions['config'] as int) + 3,
        }),
      );
      expect(badKind.statusCode, equals(HttpStatus.badRequest));
      expect(unknown.statusCode, equals(HttpStatus.notFound));
    });

    test('flips one tweak per server on the mutate timer', () async {
      final FakeFleet timed = await FakeFleet.start(
        port: 0,
        servers: 2,
        mutateEvery: const Duration(milliseconds: 100),
      );
      addTearDown(timed.close);
      Future<Map<String, dynamic>> stateOf(String slug) async => _json(
        await raw.get(timed.baseUri.resolve('/__fake/state?server=$slug')),
      );
      int tweaksRevision(Map<String, dynamic> state) =>
          (state['revisions'] as Map<String, dynamic>)['tweaks'] as int;

      final Map<String, dynamic> first = await stateOf('s2');
      Map<String, dynamic> latest = first;
      final DateTime deadline = DateTime.now().add(const Duration(seconds: 5));
      while (tweaksRevision(latest) < tweaksRevision(first) + 2) {
        if (DateTime.now().isAfter(deadline)) {
          fail('The mutate timer never bumped the tweaks revision');
        }
        await Future<void>.delayed(const Duration(milliseconds: 20));
        latest = await stateOf('s2');
      }
      final Map<String, dynamic> was = first['tweaks'] as Map<String, dynamic>;
      final Map<String, dynamic> now = latest['tweaks'] as Map<String, dynamic>;
      final int ticks = tweaksRevision(latest) - tweaksRevision(first);
      final Map<String, dynamic> metrics = _json(
        await raw.get(
          timed.baseUri.resolve('/s2/api/v1/metrics'),
          headers: <String, String>{
            HttpHeaders.authorizationHeader:
                'Bearer ${timed.adminToken(timed.profiles[1]).bearer}',
          },
        ),
      );

      expect(ticks, lessThan(was.length));
      expect(
        was.keys.where((String id) => was[id] != now[id]),
        hasLength(ticks),
      );
      expect(
        (latest['revisions'] as Map<String, dynamic>)['features'],
        equals(1),
      );
      expect(
        (latest['revisions'] as Map<String, dynamic>)['config'],
        equals(1),
      );
      expect(
        ((metrics['data'] as Map<String, dynamic>)['revisions']
            as Map<String, dynamic>)['tweaks'],
        greaterThanOrEqualTo(tweaksRevision(latest)),
      );
    });

    test(
      'answers a stale counter with counter_conflict and the last counter',
      () async {
        final FakeToken token = fleet.issue(survival);
        final FakeToken unused = fleet.issue(survival);
        const String world = '/s1/api/v1/worlds/spawn';
        const Map<String, Object?> body = <String, Object?>{'budgetMs': 11};
        Future<http.Response> put(String counterValue) =>
            api('PUT', world, token, body: body, counterValue: counterValue);

        final http.Response missing = await api(
          'PUT',
          world,
          token,
          body: body,
          withCounter: false,
        );
        final http.Response first = await put('5');
        final http.Response replayed = await put('5');
        final http.Response lower = await put('3');
        final http.Response garbled = await put('abc');
        final int before = DateTime.now().millisecondsSinceEpoch;
        final Map<String, dynamic> raised = await post(
          '/__fake/counter?server=s1&add=1000',
        );
        final http.Response stale = await put('6');
        final http.Response rebased = await put('1006');
        final Map<String, dynamic> counters =
            raised['counters'] as Map<String, dynamic>;

        expect(missing.statusCode, equals(HttpStatus.conflict));
        expect(
          _json(missing),
          equals(<String, Object?>{
            'error': <String, Object?>{
              'code': 'counter_conflict',
              'message': 'Replayed or non-increasing X-React-Counter',
              'lastCounter': 0,
            },
          }),
        );
        expect(first.statusCode, equals(HttpStatus.ok));
        expect(replayed.statusCode, equals(HttpStatus.conflict));
        expect(
          _json(replayed),
          equals(<String, Object?>{
            'error': <String, Object?>{
              'code': 'counter_conflict',
              'message': 'Replayed or non-increasing X-React-Counter',
              'lastCounter': 5,
            },
          }),
        );
        expect(_json(lower)['error']['lastCounter'], equals(5));
        expect(garbled.statusCode, equals(HttpStatus.badRequest));
        expect(counters[token.id], equals(1005));
        expect(counters[unused.id], greaterThanOrEqualTo(before + 1000));
        expect(stale.statusCode, equals(HttpStatus.conflict));
        expect(_json(stale)['error']['lastCounter'], equals(1005));
        expect(rebased.statusCode, equals(HttpStatus.ok));
      },
    );

    test(
      'revokes a token: REST answers token_revoked, its sockets close 4401',
      () async {
        final FakeToken doomed = fleet.issue(skyblock);
        final FakeToken kept = fleet.issue(skyblock);
        final _Probe doomedMetrics = await open(fleet, 's2', 'metrics', doomed);
        final _Probe doomedLogs = await open(fleet, 's2', 'logs', doomed);
        final _Probe keptMetrics = await open(fleet, 's2', 'metrics', kept);
        await _until(
          () =>
              doomedMetrics.frames.isNotEmpty &&
              doomedLogs.frames.isNotEmpty &&
              keptMetrics.frames.isNotEmpty,
        );

        final Map<String, dynamic> revoked = await post(
          '/__fake/revoke?server=s2&tokenId=${doomed.id}',
        );
        await doomedMetrics.closed();
        await doomedLogs.closed();
        final bool keptOpen = keptMetrics.socket.readyState == WebSocket.open;
        final http.Response rest = await api(
          'GET',
          '/s2/api/v1/identity',
          doomed,
        );
        final _Probe retry = await open(fleet, 's2', 'metrics', doomed);
        await retry.closed();
        final FakeToken viewerA = fleet.issue(skyblock, role: 'viewer');
        final FakeToken viewerB = fleet.issue(skyblock, role: 'viewer');
        final Map<String, dynamic> viewers = await post(
          '/__fake/revoke?server=s2&role=viewer',
        );
        final Map<String, dynamic> admins = await post(
          '/__fake/revoke?server=s2&role=admin',
        );
        await keptMetrics.closed();
        final http.Response builtIn = await api(
          'GET',
          '/s2/api/v1/identity',
          fleet.adminToken(skyblock),
        );
        final Map<String, dynamic> state = await fakeState('s2');

        expect(revoked['revoked'], equals(<String>[doomed.id]));
        expect(revoked['closedSockets'], equals(2));
        for (final _Probe probe in <_Probe>[doomedMetrics, doomedLogs]) {
          expect(probe.socket.closeCode, equals(4401));
          expect(probe.socket.closeReason, equals('token_revoked'));
        }
        expect(keptOpen, isTrue);
        expect(rest.statusCode, equals(HttpStatus.unauthorized));
        expect(
          _json(rest),
          equals(<String, Object?>{
            'error': <String, Object?>{
              'code': 'token_revoked',
              'message': 'The pairing ended.',
            },
          }),
        );
        expect(retry.socket.closeCode, equals(4401));
        expect(
          viewers['revoked'],
          containsAll(<String>[viewerA.id, viewerB.id]),
        );
        expect(admins['revoked'], contains(kept.id));
        expect(admins['revoked'], isNot(contains('s2-admin')));
        expect(keptMetrics.socket.closeCode, equals(4401));
        expect(builtIn.statusCode, equals(HttpStatus.ok));
        expect(
          state['revoked'],
          containsAll(<String>[doomed.id, viewerA.id, kept.id]),
        );
      },
    );

    test('batches log lines on ?batch=1 with a server-wide seq', () async {
      await post('/__fake/logs?server=s1&quiet=true');
      addTearDown(() => raw.post(at('/__fake/logs?server=s1&quiet=false')));
      final FakeToken admin = fleet.issue(survival);
      final _Probe logs = await open(fleet, 's1', 'logs?batch=1', admin);
      await _until(() => logs.lines.length >= 200);
      final List<int> seqs = <int>[
        for (final Map<String, dynamic> line in logs.lines) line['seq'] as int,
      ];
      final int last = seqs.last;

      final Map<String, dynamic> appended = await post(
        '/__fake/log?server=s1&line=${Uri.encodeQueryComponent('Hello fleet')}',
      );
      await _until(() => logs.lines.length == 201);
      final Map<String, dynamic> plainPage = _json(
        await api('GET', '/s1/api/v1/logs?limit=3', admin),
      );
      final Map<String, dynamic> afterPage = _json(
        await api('GET', '/s1/api/v1/logs?after=${last - 2}&limit=10', admin),
      );
      final Map<String, dynamic> cappedPage = _json(
        await api('GET', '/s1/api/v1/logs?after=${last - 2}&limit=1', admin),
      );
      final List<dynamic> plain = plainPage['data'] as List<dynamic>;
      final List<dynamic> after = afterPage['data'] as List<dynamic>;
      final List<dynamic> capped = cappedPage['data'] as List<dynamic>;

      expect(
        logs.frames.map((Map<String, dynamic> frame) => frame['type']),
        everyElement(equals('logs')),
      );
      expect(logs.frames.length, greaterThanOrEqualTo(3));
      expect(seqs, hasLength(200));
      expect(seqs, equals(<int>[for (int n = 0; n < 200; n++) seqs.first + n]));
      expect(appended['seq'], equals(last + 1));
      expect(logs.lines.last['seq'], equals(last + 1));
      expect(
        logs.lines.last['line'],
        matches(RegExp(r'^\[\d\d:\d\d:\d\d INFO\]: Hello fleet$')),
      );
      expect(plain, hasLength(3));
      expect(plain, everyElement(isA<String>()));
      expect(plain.last, equals(appended['line']));
      expect(plainPage['lastSeq'], equals(last + 1));
      expect(afterPage['lastSeq'], equals(last + 1));
      expect(cappedPage['lastSeq'], equals(last - 1));
      expect(
        after.map(
          (dynamic line) => (line as Map<String, dynamic>)['seq'] as int,
        ),
        equals(<int>[last - 1, last, last + 1]),
      );
      expect(
        (after.last as Map<String, dynamic>)['line'],
        equals(appended['line']),
      );
      expect((capped.single as Map<String, dynamic>)['seq'], equals(last - 1));
    });

    test(
      'drops the oldest lines of a full session queue and says how many',
      () async {
        await post('/__fake/logs?server=s1&quiet=true');
        addTearDown(() => raw.post(at('/__fake/logs?server=s1&quiet=false')));
        final FakeToken admin = fleet.issue(survival);
        final _Probe logs = await open(fleet, 's1', 'logs?batch=1', admin);
        await _until(() => logs.lines.length >= 200);
        final int backlogFrames = logs.frames.length;

        final Map<String, dynamic> burst = await post(
          '/__fake/log-burst?server=s1&count=700',
        );
        await _until(() => logs.frames.length > backlogFrames);
        final Map<String, dynamic> frame = logs.frames[backlogFrames];
        final List<dynamic> lines = frame['lines'] as List<dynamic>;

        expect(backlogFrames, equals(2));
        expect(
          logs.frames
              .take(backlogFrames)
              .any((Map<String, dynamic> each) => each.containsKey('dropped')),
          isFalse,
        );
        expect(frame['dropped'], equals(200));
        expect(lines, hasLength(500));
        expect(
          (lines.first as Map<String, dynamic>)['seq'],
          equals((burst['firstSeq'] as int) + 200),
        );
        expect(
          (lines.last as Map<String, dynamic>)['seq'],
          equals(burst['lastSeq']),
        );
        expect(
          (lines.last as Map<String, dynamic>)['line'],
          endsWith('INFO]: Burst line 700 of 700'),
        );
      },
    );

    test('sends one log frame per line without ?batch=1', () async {
      await post('/__fake/logs?server=s1&quiet=true');
      addTearDown(() => raw.post(at('/__fake/logs?server=s1&quiet=false')));
      final FakeToken admin = fleet.issue(survival);
      final _Probe logs = await open(fleet, 's1', 'logs', admin);
      await _until(() => logs.frames.length >= 200);
      final List<Map<String, dynamic>> backlog = List<Map<String, dynamic>>.of(
        logs.frames,
      );

      final Map<String, dynamic> burst = await post(
        '/__fake/log-burst?server=s1&count=700',
      );
      await _until(() => logs.frames.length == 700);
      final List<Map<String, dynamic>> burstFrames = logs.frames.sublist(200);
      final int firstSeq = burst['firstSeq'] as int;

      expect(backlog, hasLength(200));
      expect(
        logs.frames.map((Map<String, dynamic> frame) => frame['type']),
        everyElement(equals('log')),
      );
      expect(
        backlog.map((Map<String, dynamic> frame) => frame.keys.toSet()),
        everyElement(equals(<String>{'type', 'line', 'seq'})),
      );
      expect(burstFrames.first['dropped'], equals(200));
      expect(burstFrames.first['seq'], equals(firstSeq + 200));
      expect(
        burstFrames
            .skip(1)
            .where(
              (Map<String, dynamic> frame) => frame.containsKey('dropped'),
            ),
        isEmpty,
      );
      expect(
        burstFrames.map((Map<String, dynamic> frame) => frame['seq']),
        equals(<int>[for (int n = 200; n < 700; n++) firstSeq + n]),
      );
    });

    test('closes idle log sockets and dropped sockets with 1001', () async {
      final FakeFleet idle = await FakeFleet.start(
        port: 0,
        servers: 1,
        idleTimeout: const Duration(milliseconds: 300),
      );
      addTearDown(idle.close);
      await raw.post(idle.baseUri.resolve('/__fake/logs?server=s1&quiet=true'));
      final _Probe quiet = await open(
        idle,
        's1',
        'logs',
        idle.adminToken(idle.profiles.first),
      );
      await quiet.closed();

      final FakeToken admin = fleet.issue(fleet.profiles[2]);
      final _Probe metrics = await open(fleet, 's3', 'metrics', admin);
      final _Probe logs = await open(fleet, 's3', 'logs', admin);
      await _until(() => metrics.frames.isNotEmpty && logs.frames.isNotEmpty);
      final Map<String, dynamic> droppedMetrics = await post(
        '/__fake/drop-sockets?server=s3&kind=metrics',
      );
      await metrics.closed();
      final bool logsOpen = logs.socket.readyState == WebSocket.open;
      final Map<String, dynamic> droppedAll = await post(
        '/__fake/drop-sockets?server=s3&kind=all',
      );
      await logs.closed();

      expect(quiet.lines, hasLength(200));
      for (final _Probe probe in <_Probe>[quiet, metrics, logs]) {
        expect(probe.socket.closeCode, equals(WebSocketStatus.goingAway));
        expect(probe.socket.closeReason, equals('Connection Idle Timeout'));
      }
      expect(droppedMetrics['closed'], greaterThanOrEqualTo(1));
      expect(logsOpen, isTrue);
      expect(droppedAll['closed'], greaterThanOrEqualTo(1));
    });

    test(
      'moves action tickets from queued through running to an end',
      () async {
        final FakeToken operator = fleet.issue(
          fleet.profiles[2],
          role: 'operator',
        );
        final FakeToken viewer = fleet.issue(fleet.profiles[2], role: 'viewer');
        Future<http.Response> execute() => api(
          'POST',
          '/s3/api/v1/actions/collect-garbage/execute',
          operator,
          body: <String, Object?>{},
        );
        String ticketOf(http.Response response) =>
            (_json(response)['data'] as Map<String, dynamic>)['ticketId']
                as String;
        Future<Map<String, dynamic>> poll(String id) async =>
            _json(await api('GET', '/s3/api/v1/actions/tickets/$id', viewer));

        final Map<String, dynamic> failNext = await post(
          '/__fake/fail-next-action?server=s3',
        );
        final String failing = ticketOf(await execute());
        final http.Response executed = await execute();
        final String ticket = ticketOf(executed);
        final Map<String, dynamic> queued = await poll(ticket);
        final List<String> states = <String>[queued['state'] as String];
        Map<String, dynamic> latest = queued;
        final DateTime deadline = DateTime.now().add(
          const Duration(seconds: 5),
        );
        while (latest['state'] != 'done') {
          if (DateTime.now().isAfter(deadline)) {
            fail('The ticket never finished');
          }
          await Future<void>.delayed(const Duration(milliseconds: 50));
          latest = await poll(ticket);
          if (latest['state'] != states.last) {
            states.add(latest['state'] as String);
          }
        }
        final Map<String, dynamic> failed = await poll(failing);
        final http.Response unknown = await api(
          'GET',
          '/s3/api/v1/actions/tickets/s3-ticket-999',
          viewer,
        );
        final http.Response elsewhere = await api(
          'GET',
          '/s1/api/v1/actions/tickets/$ticket',
          fleet.issue(survival),
        );

        expect(
          _json(executed)['data'],
          equals(<String, Object?>{'ticketId': ticket, 'status': 'queued'}),
        );
        expect(
          queued,
          equals(<String, Object?>{
            'id': ticket,
            'state': 'queued',
            'message': '',
          }),
        );
        expect(states, equals(<String>['queued', 'running', 'done']));
        expect(latest['message'], equals('Collected garbage'));
        expect(failNext['failNextAction'], isTrue);
        expect(failed['id'], equals(failing));
        expect(failed['state'], equals('failed'));
        expect(failed['message'], isNotEmpty);
        expect(unknown.statusCode, equals(HttpStatus.notFound));
        expect(
          _json(unknown),
          equals(<String, Object?>{
            'error': <String, Object?>{
              'code': 'not_found',
              'message': 'Unknown ticket',
            },
          }),
        );
        expect(elsewhere.statusCode, equals(HttpStatus.notFound));
      },
    );

    test('sends compact metric frames with available samplers only', () async {
      final FakeToken admin = fleet.issue(survival);
      final Map<String, dynamic> marked = await post(
        '/__fake/sampler?server=s1&id=ticks-per-second&available=false'
        '&reason=${Uri.encodeQueryComponent('Paper API missing')}',
      );
      addTearDown(
        () => raw.post(
          at('/__fake/sampler?server=s1&id=ticks-per-second&available=true'),
        ),
      );
      final Map<String, dynamic> full = _json(
        await api('GET', '/s1/api/v1/metrics', admin),
      );
      final Map<String, dynamic> compact = _json(
        await api('GET', '/s1/api/v1/metrics?compact=1', admin),
      );
      final List<Map<String, dynamic>> catalog =
          (_json(await api('GET', '/s1/api/v1/metrics/catalog', admin))['data']
                  as List<dynamic>)
              .cast<Map<String, dynamic>>();
      final _Probe socket = await open(fleet, 's1', 'metrics', admin);
      await _until(() => socket.frames.isNotEmpty);
      final int framesBefore = socket.frames.length;
      final int worldsBefore =
          (_dataOf(socket.frames.last)['revisions']
                  as Map<String, dynamic>)['worlds']
              as int;
      await api(
        'PUT',
        '/s1/api/v1/worlds/world',
        admin,
        body: <String, Object?>{'budgetMs': 31},
      );
      final DateTime written = DateTime.now();
      await _until(
        () => socket.frames
            .skip(framesBefore)
            .any(
              (Map<String, dynamic> frame) =>
                  (_dataOf(frame)['revisions']
                      as Map<String, dynamic>)['worlds'] ==
                  worldsBefore + 1,
            ),
      );
      final Duration pushedAfter = DateTime.now().difference(written);
      final Map<String, dynamic> fullTps = _samplersOf(full).singleWhere(
        (Map<String, dynamic> sampler) => sampler['id'] == 'ticks-per-second',
      );
      final Map<String, dynamic> catalogTps = catalog.singleWhere(
        (Map<String, dynamic> entry) => entry['id'] == 'ticks-per-second',
      );
      const Set<String> compactKeys = <String>{
        'id',
        'name',
        'suffix',
        'value',
        'display',
      };

      expect(marked['available'], isFalse);
      expect(marked['unavailableReason'], equals('Paper API missing'));
      expect(_samplersOf(full), hasLength(metricFixtures.length));
      expect(fullTps['available'], isFalse);
      expect(fullTps['unavailableReason'], equals('Paper API missing'));
      expect(_dataOf(full)['revisions'], isA<Map<String, dynamic>>());
      for (final Map<String, dynamic> body in <Map<String, dynamic>>[
        compact,
        socket.frames.first,
      ]) {
        expect(
          _dataOf(body).keys.toSet(),
          equals(<String>{'sequence', 'capturedAtMs', 'samplers', 'revisions'}),
        );
        expect(_samplersOf(body), hasLength(metricFixtures.length - 1));
        expect(
          _samplersOf(body).map((Map<String, dynamic> s) => s['id']),
          isNot(contains('ticks-per-second')),
        );
        expect(
          _samplersOf(body).map((Map<String, dynamic> s) => s.keys.toSet()),
          everyElement(equals(compactKeys)),
        );
      }
      expect(catalogTps['available'], isFalse);
      expect(catalogTps['unavailableReason'], equals('Paper API missing'));
      expect(
        catalog
            .where((Map<String, dynamic> entry) => entry != catalogTps)
            .every(
              (Map<String, dynamic> entry) =>
                  entry['available'] == true &&
                  entry.containsKey('unavailableReason') &&
                  entry['unavailableReason'] == null,
            ),
        isTrue,
      );
      expect(pushedAfter, lessThan(const Duration(milliseconds: 500)));
    });

    test('throttles the next requests of one server with 429', () async {
      final FakeToken builtIn = fleet.adminToken(skyblock);
      final Map<String, dynamic> throttled = await post(
        '/__fake/throttle?server=s2&count=2&retryAfter=3',
      );
      final http.Response first = await api(
        'GET',
        '/s2/api/v1/identity',
        builtIn,
        headers: const <String, String>{'Origin': _origin},
      );
      final http.Response other = await api(
        'GET',
        '/s1/api/v1/identity',
        fleet.issue(survival),
      );
      final http.Response second = await api('GET', '/s2/api/v1/ping', builtIn);
      final http.Response third = await api(
        'GET',
        '/s2/api/v1/identity',
        builtIn,
      );

      expect(
        throttled,
        equals(<String, Object?>{'server': 's2', 'count': 2, 'retryAfter': 3}),
      );
      expect(first.statusCode, equals(HttpStatus.tooManyRequests));
      expect(first.headers[HttpHeaders.retryAfterHeader], equals('3'));
      expect(
        first.headers[HttpHeaders.accessControlExposeHeadersHeader],
        contains('Retry-After'),
      );
      expect(
        _json(first),
        equals(<String, Object?>{
          'error': <String, Object?>{
            'code': 'rate_limited',
            'message': 'Too many requests',
          },
        }),
      );
      expect(other.statusCode, equals(HttpStatus.ok));
      expect(second.statusCode, equals(HttpStatus.tooManyRequests));
      expect(third.statusCode, equals(HttpStatus.ok));
    });

    test('reports server state and the last 50 API requests', () async {
      final FakeToken admin = fleet.issue(fleet.profiles[2]);
      for (int n = 0; n < 55; n++) {
        await raw.get(at('/s3/api/v1/ping'));
      }
      final http.Response write = await api(
        'PUT',
        '/s3/api/v1/worlds/resources',
        admin,
        body: <String, Object?>{'budgetMs': 13},
        counterValue: '77',
      );
      final Map<String, dynamic> state = await fakeState('s3');
      final http.Response unknown = await raw.get(
        at('/__fake/state?server=s9'),
      );
      final List<dynamic> requests = state['requests'] as List<dynamic>;

      expect(write.statusCode, equals(HttpStatus.ok));
      expect(
        state.keys,
        containsAll(<String>[
          'revisions',
          'tweaks',
          'features',
          'worlds',
          'counters',
          'revoked',
          'requests',
        ]),
      );
      expect(state['revisions'] as Map<String, dynamic>, hasLength(6));
      expect(
        (state['tweaks'] as Map<String, dynamic>).values,
        everyElement(isA<bool>()),
      );
      expect(
        (state['worlds'] as Map<String, dynamic>)['resources'],
        equals(<String, Object?>{
          'budgetMs': 13.0,
          'panicMs': 24.0,
          'releaseMs': 12.0,
        }),
      );
      expect((state['counters'] as Map<String, dynamic>)[admin.id], equals(77));
      expect(requests, hasLength(50));
      expect(
        requests.last,
        equals(<String, Object?>{
          'method': 'PUT',
          'path': '/api/v1/worlds/resources',
          'tokenId': admin.id,
          'status': 200,
        }),
      );
      expect(
        requests[requests.length - 2],
        equals(<String, Object?>{
          'method': 'GET',
          'path': '/api/v1/ping',
          'tokenId': null,
          'status': 200,
        }),
      );
      expect(unknown.statusCode, equals(HttpStatus.notFound));
    });
  });
}

ServerCredential _credential(
  FakeFleet fleet,
  FakeServerProfile profile,
  FakeToken token,
) => ServerCredential(
  id: profile.slug,
  label: profile.label,
  host: '127.0.0.1',
  port: fleet.port,
  basePath: '/${profile.slug}',
  bearer: token.bearer,
  fingerprint: profile.fingerprint,
  serverKey: profile.publicKey,
);

Map<String, dynamic> _json(http.Response response) =>
    jsonDecode(response.body) as Map<String, dynamic>;

Map<String, dynamic> _dataOf(Map<String, dynamic> body) =>
    body['data'] as Map<String, dynamic>;

List<Map<String, dynamic>> _samplersOf(Map<String, dynamic> body) =>
    (_dataOf(body)['samplers'] as List<dynamic>).cast<Map<String, dynamic>>();

Future<void> _until(bool Function() condition) async {
  final DateTime deadline = DateTime.now().add(const Duration(seconds: 5));
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      throw TimeoutException('Condition not met in 5 s');
    }
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
}

/// A raw socket that keeps every decoded frame and its close.
final class _Probe {
  final WebSocket socket;
  final List<Map<String, dynamic>> frames = <Map<String, dynamic>>[];
  final Completer<void> _done = Completer<void>();

  _Probe(this.socket) {
    socket.listen(
      (dynamic raw) =>
          frames.add(jsonDecode(raw as String) as Map<String, dynamic>),
      onDone: _done.complete,
      onError: (Object _) {},
    );
  }

  List<Map<String, dynamic>> get lines => <Map<String, dynamic>>[
    for (final Map<String, dynamic> frame in frames)
      ...switch (frame['type']) {
        'logs' =>
          (frame['lines'] as List<dynamic>).cast<Map<String, dynamic>>(),
        'log' => <Map<String, dynamic>>[frame],
        _ => const <Map<String, dynamic>>[],
      },
  ];

  Future<void> closed() => _done.future.timeout(const Duration(seconds: 5));

  Future<void> close() => socket.close();
}
