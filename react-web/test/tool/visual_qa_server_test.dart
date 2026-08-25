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
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/screen/add_server.dart';
import 'package:react_web/service/direct_web_socket_handshake.dart';
import 'package:react_web/service/react_client.dart';
import '../../tool/visual_qa/fixtures.dart';
import '../../tool/visual_qa/server.dart';

void main() {
  late VisualQaServer server;

  setUpAll(() async {
    server = await VisualQaServer.start(port: 0);
  });

  tearDownAll(() async {
    await server.close();
  });

  group('visual QA pairing codes', () {
    test('decode through the production RCT2 parser', () {
      for (final VisualQaProfile profile in VisualQaProfile.values) {
        final PairingCode? parsed = PairingCode.decode(
          server.pairingCode(profile),
        );

        expect(parsed, isNotNull);
        expect(parsed!.directUrl, equals(server.baseUri.toString()));
        expect(parsed.relayUrl, isEmpty);
        expect(parsed.serverPubKey, equals(profile.publicKey));
        expect(parsed.fingerprint, equals(profile.fingerprint));
        expect(parsed.tokenId, equals(profile.tokenId));
        expect(parsed.tokenSig, equals(profile.tokenSig));

        final ServerCredential credential = parsed.toCredential(
          id: profile.name,
          label: profile.label,
        );
        expect(credential.host, equals('127.0.0.1'));
        expect(credential.port, equals(server.port));
        expect(credential.secure, isFalse);
        expect(credential.bearer, equals(profile.bearer));
      }
    });

    test(
      'publishes both deterministic codes from the local helper route',
      () async {
        final http.Response response = await http.get(
          server.baseUri.resolve('/__qa/codes'),
        );
        final Map<String, dynamic> body =
            jsonDecode(response.body) as Map<String, dynamic>;
        final List<dynamic> servers = body['servers'] as List<dynamic>;

        expect(response.statusCode, equals(HttpStatus.ok));
        expect(servers, hasLength(2));
        expect(
          (servers.first as Map<String, dynamic>)['pairingCode'],
          equals(server.pairingCode(VisualQaProfile.alpha)),
        );
        expect(
          (servers.last as Map<String, dynamic>)['pairingCode'],
          equals(server.pairingCode(VisualQaProfile.beta)),
        );
      },
    );
  });

  group('visual QA protocol integration', () {
    test('serves every read model required by dashboard routes', () async {
      final http.Client transport = http.Client();
      addTearDown(transport.close);
      final ReactClient client = ReactClient(
        _credential(server, VisualQaProfile.beta),
        client: transport,
      );

      final ServerCapabilities capabilities = await client.ping();
      final IdentityInfo identity = await client.identity();
      final RoleInfo role = await client.whoami();
      final ServerSnapshot snapshot = await client.metrics();
      final List<MetricHistoryDescriptor> historyCatalog = await client
          .historyCatalog();
      final MetricHistoryPage history = await client.historyPage(
        ids: <String>['tick-time'],
        from: DateTime.now().subtract(const Duration(hours: 24)),
        to: DateTime.now(),
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
      final ControlItem tweak = await client.tweak('async-chunk-io');
      final List<WorldSettings> worlds = await client.worlds();
      final List<OnlinePlayerInfo> players = await client.players();
      final List<ActionDescriptor> actions = await client.actions();
      final IncidentStatus incidents = await client.incidents();
      final EnvironmentInfo environment = await client.environment();
      final ConfigTree config = await client.config();
      final List<String> logs = await client.logs();

      expect(capabilities.protocolVersion, equals(2));
      expect(capabilities.serverFingerprint, equals(identity.serverId));
      expect(identity.serverName, equals('QA Beta'));
      expect(role.isAdmin, isTrue);
      expect(snapshot.byId, contains('ticks-per-second'));
      expect(snapshot.byId, contains('adapt-world-policy-latency'));
      expect(snapshot.byId, contains('iris-generation-total-ms'));
      expect(snapshot.byId, contains('wormholes-projection-render-ms'));
      expect(snapshot.byId, contains('entity-pressure-heatmap'));
      expect(snapshot.sampler('ticks-per-second')!.value, equals(8.9));
      expect(
        historyCatalog.map((MetricHistoryDescriptor item) => item.id),
        contains('tick-time'),
      );
      expect(history.series.single.points, hasLength(24));
      expect(
        heatmaps.map((HeatmapSummary item) => item.label),
        contains('QA Chunk Pressure'),
      );
      expect(heatmap.world, equals('minecraft:world_the_end'));
      expect(heatmap.centerChunkX, equals(31));
      expect(heatmap.centerChunkZ, equals(-17));
      expect(heatmap.radius, equals(3));
      expect(heatmap.cells, hasLength(46));
      expect(
        heatmap.cells.map((HeatmapCell cell) => cell.x),
        containsAll(<int>[28, 34]),
      );
      expect(
        heatmap.cells.map((HeatmapCell cell) => cell.z),
        containsAll(<int>[-20, -14]),
      );
      expect(
        features.map((ControlItem item) => item.name),
        contains('Mob Stacking'),
      );
      expect(feature.name, equals('Mob Stacking'));
      expect(
        features.map((ControlItem item) => item.name),
        contains('Dynamic View Distance'),
      );
      expect(
        tweaks.map((ControlItem item) => item.name),
        contains('Async Chunk IO'),
      );
      expect(tweak.name, equals('Async Chunk IO'));
      expect(
        worlds.map((WorldSettings world) => world.name),
        contains('world_nether'),
      );
      expect(
        worlds.map((WorldSettings world) => world.key),
        contains('minecraft:world_nether'),
      );
      expect(
        players.map((OnlinePlayerInfo player) => player.name),
        contains('Scout'),
      );
      expect(
        actions.map((ActionDescriptor action) => action.name),
        contains('Force GC'),
      );
      expect(
        incidents.contributors.map((IncidentContributor item) => item.label),
        contains('Scheduler backlog'),
      );
      expect(environment.cpu['model'], equals('QA 16-Core'));
      expect(config.node('tick-budget-ms')!.label, equals('Tick Budget'));
      expect(logs, contains('[INFO] QA fixture ready'));
    });

    test('accepts a confirmed player teleport target end to end', () async {
      final http.Client transport = http.Client();
      addTearDown(transport.close);
      final ReactClient client = ReactClient(
        _credential(server, VisualQaProfile.alpha),
        client: transport,
      );
      final OnlinePlayerInfo player = (await client.players()).first;

      final PlayerTeleportResult result = await client.teleportPlayer(
        player.id,
        worldKey: 'minecraft:world_nether',
        blockX: -24,
        blockZ: 40,
      );

      expect(result.status, equals('queued'));
      expect(result.playerName, equals(player.name));
      expect(result.blockX, equals(-24));
      expect(result.blockZ, equals(40));
    });

    test(
      'persists safe control mutations inside the fixture session',
      () async {
        final http.Client transport = http.Client();
        addTearDown(transport.close);
        final ReactClient client = ReactClient(
          _credential(server, VisualQaProfile.alpha),
          client: transport,
        );

        final ControlItem disabled = await client.setFeatureEnabled(
          'mob-stacking',
          false,
        );
        final ControlItem configured = await client.setFeatureConfig(
          'mob-stacking',
          <String, Object?>{'max-stack': 64},
        );
        final ControlItem tweak = await client.setTweakConfig(
          'async-chunk-io',
          <String, Object?>{'workers': 7},
        );
        final ControlItem disabledTweak = await client.setTweakEnabled(
          'async-chunk-io',
          false,
        );
        final WorldSettings world = await client.setWorld(
          'world_nether',
          budgetMs: 21.0,
        );
        final ActionTicket ticket = await client.executeAction(
          'gc',
          params: const <String, Object?>{},
          confirm: false,
        );
        final ConfigTree changed = await client.applyConfig(<String, Object?>{
          'tick-budget-ms': 43.0,
        });
        final ConfigTree preset = await client.applyPreset('high');
        final bool dispatched = await client.executeConsole('react status');
        final List<String> logs = await client.logs();

        expect(disabled.enabled, isFalse);
        expect(
          configured.knobs
              .singleWhere((Knob knob) => knob.key == 'max-stack')
              .intValue,
          equals(64),
        );
        expect(
          tweak.knobs
              .singleWhere((Knob knob) => knob.key == 'workers')
              .intValue,
          equals(7),
        );
        expect(disabledTweak.enabled, isFalse);
        expect(world.budgetMs, equals(21.0));
        expect(ticket.ticketId, equals('qa-alpha-ticket-001'));
        expect(ticket.status, equals('queued'));
        expect(changed.node('tick-budget-ms')!.doubleValue, equals(43.0));
        expect(preset.node('tick-budget-ms')!.doubleValue, equals(40.0));
        expect(dispatched, isTrue);
        expect(
          logs.last,
          equals('[INFO] QA accepted console command: react status'),
        );
      },
    );

    test(
      'streams metric and log frames over the production socket paths',
      () async {
        final String origin = VisualQaServer.defaultAllowedOrigin;
        final ServerCredential credential = _credential(
          server,
          VisualQaProfile.beta,
        );
        final DirectWebSocketHandshake metricsHandshake =
            DirectWebSocketHandshake(credential, 'ws/metrics');
        final WebSocket metricsSocket = await WebSocket.connect(
          metricsHandshake.endpoint.toString(),
          headers: <String, dynamic>{'Origin': origin},
        );
        addTearDown(metricsSocket.close);
        metricsSocket.add(metricsHandshake.authFrame);
        final String metricsRaw = await metricsSocket.first as String;
        final Map<String, dynamic> metrics =
            jsonDecode(metricsRaw) as Map<String, dynamic>;
        final ServerSnapshot snapshot = ServerSnapshot.fromJson(metrics);

        final DirectWebSocketHandshake logsHandshake = DirectWebSocketHandshake(
          credential,
          'ws/logs',
        );
        final WebSocket logsSocket = await WebSocket.connect(
          logsHandshake.endpoint.toString(),
          headers: <String, dynamic>{'Origin': origin},
        );
        addTearDown(logsSocket.close);
        logsSocket.add(logsHandshake.authFrame);
        final String logsRaw = await logsSocket.first as String;
        final Map<String, dynamic> logFrame =
            jsonDecode(logsRaw) as Map<String, dynamic>;

        expect(snapshot.sampler('incident-score')!.value, equals(72.0));
        expect(logFrame['type'], equals('log'));
        expect(logFrame['line'], equals('[INFO] QA fixture ready'));
      },
    );

    test('does not accept a bearer token from a socket query', () async {
      final WebSocket socket = await WebSocket.connect(
        'ws://127.0.0.1:${server.port}/ws/metrics'
        '?token=${VisualQaProfile.beta.bearer}',
        headers: <String, dynamic>{
          'Origin': VisualQaServer.defaultAllowedOrigin,
        },
      );
      addTearDown(socket.close);
      socket.add('{"type":"not-auth"}');

      await expectLater(socket, emitsDone);
    });
  });

  group('visual QA transport boundaries', () {
    test('binds IPv4 loopback and exposes CORS only to the QA app', () async {
      expect(server.address.type, equals(InternetAddressType.IPv4));
      expect(server.address.address, equals('127.0.0.1'));

      final http.Response allowed = await http.get(
        server.baseUri.resolve('/api/v1/identity'),
        headers: <String, String>{
          'Origin': VisualQaServer.defaultAllowedOrigin,
          HttpHeaders.authorizationHeader:
              'Bearer ${VisualQaProfile.alpha.bearer}',
        },
      );
      final http.Response denied = await http.get(
        server.baseUri.resolve('/__qa/health'),
        headers: <String, String>{'Origin': 'http://localhost:8080'},
      );

      expect(allowed.statusCode, equals(HttpStatus.ok));
      expect(
        allowed.headers[HttpHeaders.accessControlAllowOriginHeader],
        equals(VisualQaServer.defaultAllowedOrigin),
      );
      expect(denied.statusCode, equals(HttpStatus.forbidden));
      expect(
        denied.headers[HttpHeaders.accessControlAllowOriginHeader],
        isNull,
      );
    });

    test('answers browser preflight with the mutation headers', () async {
      final http.Client transport = http.Client();
      addTearDown(transport.close);
      final http.Request request = http.Request(
        'OPTIONS',
        server.baseUri.resolve('/api/v1/features/mob-stacking'),
      );
      request.headers.addAll(<String, String>{
        'Origin': VisualQaServer.defaultAllowedOrigin,
        HttpHeaders.accessControlRequestMethodHeader: 'PUT',
        HttpHeaders.accessControlRequestHeadersHeader:
            'Authorization, Content-Type, X-React-Counter',
      });
      final http.StreamedResponse response = await transport.send(request);

      expect(response.statusCode, equals(HttpStatus.noContent));
      expect(
        response.headers[HttpHeaders.accessControlAllowOriginHeader],
        equals(VisualQaServer.defaultAllowedOrigin),
      );
      expect(
        response.headers[HttpHeaders.accessControlAllowHeadersHeader],
        contains('X-React-Counter'),
      );
    });
  });
}

ServerCredential _credential(VisualQaServer server, VisualQaProfile profile) {
  final PairingCode parsed = PairingCode.decode(server.pairingCode(profile))!;
  return parsed.toCredential(id: profile.name, label: profile.label);
}
