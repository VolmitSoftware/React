library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/relay_frame.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/relay_connection.dart';
import 'package:react_web/service/relay_react_client.dart';

class _FakeRelayConnection implements IRelayConnection {
  String? lastMethod;
  String? lastPath;
  Map<String, String>? lastHeaders;
  Object? lastBody;

  final Map<String, RelayResponse> _responses;

  _FakeRelayConnection(this._responses);

  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) async {
    lastMethod = method;
    lastPath = path;
    lastHeaders = Map<String, String>.from(headers);
    lastBody = body;
    final RelayResponse? response = _responses[path];
    if (response == null) {
      return RelayResponse(
        status: 404,
        body: <String, dynamic>{
          'error': <String, dynamic>{'message': 'not found'},
        },
      );
    }
    return response;
  }

  @override
  Future<void> close() async {}
}

void main() {
  const ServerCredential cred = ServerCredential(
    id: 'srv-1',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'test-bearer-token',
  );

  group('RelayReactClient.identity()', () {
    test(
      'returns IdentityInfo from 200 and sends GET with Authorization header',
      () async {
        final _FakeRelayConnection conn = _FakeRelayConnection(
          <String, RelayResponse>{
            '/api/v1/identity': RelayResponse(
              status: 200,
              body: <String, dynamic>{
                'data': <String, dynamic>{
                  'serverName': 'T',
                  'version': '2.0',
                  'folia': true,
                  'serverId': 'aa:bb',
                },
              },
            ),
          },
        );

        final RelayReactClient client = RelayReactClient(conn, cred);
        final IdentityInfo info = await client.identity();

        expect(info.serverName, equals('T'));
        expect(info.version, equals('2.0'));
        expect(info.folia, isTrue);
        expect(info.serverId, equals('aa:bb'));

        expect(conn.lastMethod, equals('GET'));
        expect(conn.lastPath, equals('/api/v1/identity'));
        expect(
          conn.lastHeaders?['Authorization'],
          equals('Bearer test-bearer-token'),
        );
      },
    );
  });

  group('RelayReactClient.metrics()', () {
    test('returns ServerSnapshot from a timestamped scalar response', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/metrics': RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'data': <String, dynamic>{
                'sequence': 5,
                'capturedAtMs': 1750000000000,
                'samplers': <Map<String, dynamic>>[
                  <String, dynamic>{
                    'id': 'cpu',
                    'name': 'CPU',
                    'value': 1.0,
                    'suffix': '%',
                    'display': '1',
                    'available': true,
                  },
                ],
              },
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);
      final ServerSnapshot snapshot = await client.metrics();

      expect(snapshot.byId.containsKey('cpu'), isTrue);
      expect(snapshot.byId['cpu']!.value, equals(1.0));
      expect(snapshot.seq, equals(5));
    });
  });

  group('RelayReactClient.heatmap()', () {
    test('uses the coordinate viewport query and decodes aggregates', () async {
      const String path =
          '/api/v1/heatmaps/entity-pressure?world=minecraft%3Aworld_nether&centerChunkX=-31&centerChunkZ=17&radius=64';
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          path: RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'data': <String, dynamic>{
                'id': 'entity-pressure',
                'label': 'Entity Pressure',
                'world': 'minecraft:world_nether',
                'centerChunkX': -31,
                'centerChunkZ': 17,
                'radius': 64,
                'originChunkX': -96,
                'originChunkZ': -48,
                'width': 17,
                'height': 17,
                'cellSizeChunks': 8,
                'capturedAtMs': 1750000000000,
                'spawnChunkX': 0,
                'spawnChunkZ': 0,
                'worldBorder': null,
                'min': 0.0,
                'max': 9.0,
                'cells': <Map<String, dynamic>>[
                  <String, dynamic>{
                    'x': -32,
                    'z': 16,
                    'sizeChunks': 8,
                    'score': 9.0,
                    'averageScore': 6.5,
                    'samples': 20,
                  },
                ],
              },
            },
          ),
        },
      );

      final HeatmapGrid grid = await RelayReactClient(conn, cred).heatmap(
        'entity-pressure',
        world: 'minecraft:world_nether',
        centerChunkX: -31,
        centerChunkZ: 17,
        radius: 64,
      );

      expect(conn.lastPath, equals(path));
      expect(grid.columns, equals(17));
      expect(grid.cells.single.averageScore, equals(6.5));
    });
  });

  group('RelayReactClient.executeConsole()', () {
    test('encodes command JSON before sending it through the relay', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/console/execute': RelayResponse(
            status: 202,
            body: <String, dynamic>{
              'data': <String, dynamic>{'dispatched': true},
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      expect(await client.executeConsole('say relay'), isTrue);
      expect(conn.lastMethod, equals('POST'));
      expect(conn.lastPath, equals('/api/v1/console/execute'));
      expect(conn.lastBody, equals('{"command":"say relay"}'));
      expect(
        int.tryParse(conn.lastHeaders?['X-React-Counter'] ?? ''),
        isNotNull,
      );
    });
  });

  group('RelayReactClient player navigation', () {
    test('lists and teleports an explicit player through relay RPC', () async {
      final _FakeRelayConnection connection = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/players': RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'data': <Map<String, dynamic>>[
                <String, dynamic>{'id': 'player-id', 'name': 'Alice'},
              ],
            },
          ),
          '/api/v1/players/player-id/teleport': RelayResponse(
            status: 202,
            body: <String, dynamic>{
              'data': <String, dynamic>{
                'playerId': 'player-id',
                'playerName': 'Alice',
                'status': 'queued',
                'worldKey': 'minecraft:overworld',
                'blockX': -24,
                'blockZ': 40,
              },
            },
          ),
        },
      );
      final RelayReactClient client = RelayReactClient(connection, cred);
      final List<OnlinePlayerInfo> players = await client.players();
      final PlayerTeleportResult result = await client.teleportPlayer(
        players.single.id,
        worldKey: 'minecraft:overworld',
        blockX: -24,
        blockZ: 40,
      );

      expect(result.status, equals('queued'));
      expect(connection.lastMethod, equals('POST'));
      expect(
        connection.lastBody,
        equals(
          '{"worldKey":"minecraft:overworld","blockX":-24,"blockZ":40,"confirm":true}',
        ),
      );
      expect(
        int.tryParse(connection.lastHeaders?['X-React-Counter'] ?? ''),
        isNotNull,
      );
    });
  });

  group('RelayReactClient error handling', () {
    test('401 throws ReactAuthException', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 401,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'Unauthorized'},
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(client.identity(), throwsA(isA<ReactAuthException>()));
    });

    test('503 throws ReactUnavailable', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 503,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'Service unavailable'},
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(client.identity(), throwsA(isA<ReactUnavailable>()));
    });

    test('200 with missing data field throws ReactUnavailable', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 200,
            body: <String, dynamic>{'other': 'stuff'},
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(client.identity(), throwsA(isA<ReactUnavailable>()));
    });
  });
}
