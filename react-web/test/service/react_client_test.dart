import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

void main() {
  const ServerCredential cred = ServerCredential(
    id: 'test',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'tok-abc',
  );

  group('ReactClient.ping()', () {
    test('uses the unauthenticated direct ping endpoint', () async {
      final MockClient mock = MockClient((http.Request request) async {
        expect(request.url.path, equals('/api/v1/ping'));
        expect(request.headers, isNot(contains('authorization')));
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'protocolVersion': 2,
              'serverFingerprint': 'fingerprint',
              'relayAvailable': false,
            },
          }),
          200,
        );
      });

      final ServerCapabilities capabilities = await ReactClient(
        cred,
        client: mock,
      ).ping();

      expect(capabilities.serverFingerprint, equals('fingerprint'));
    });

    test('fails closed on a malformed ping response', () async {
      final MockClient mock = MockClient((http.Request _) async {
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'protocolVersion': 'two',
              'serverFingerprint': 42,
              'relayAvailable': 'false',
            },
          }),
          200,
        );
      });

      await expectLater(
        ReactClient(cred, client: mock).ping(),
        throwsA(isA<ReactUnavailable>()),
      );
    });
  });

  group('ReactClient.metrics()', () {
    test('decodes a 2-sampler body', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'sequence': 7,
          'capturedAtMs': 1750000000000,
          'samplers': <Map<String, dynamic>>[
            <String, dynamic>{
              'id': 'cpu',
              'name': 'CPU Usage',
              'value': 45.0,
              'suffix': '%',
              'display': '45%',
              'available': true,
            },
            <String, dynamic>{
              'id': 'mem',
              'name': 'Memory',
              'value': 70.0,
              'suffix': 'MB',
              'display': '70 MB',
              'available': true,
            },
          ],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/metrics'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ServerSnapshot snapshot = await client.metrics();

      expect(snapshot.byId.length, equals(2));
      expect(snapshot.byId.containsKey('cpu'), isTrue);
      expect(snapshot.byId.containsKey('mem'), isTrue);
      expect(snapshot.byId['cpu']!.value, equals(45.0));
      expect(snapshot.byId['mem']!.suffix, equals('MB'));
      expect(snapshot.seq, equals(7));
    });
  });

  group('ReactClient history API', () {
    test('decodes the catalog and a paged tuple response', () async {
      int requests = 0;
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'requestedFromMs': 1000,
          'requestedToMs': 3000,
          'pageFromMs': 1000,
          'pageToMs': 3000,
          'actualResolutionMs': 1000,
          'throughSequence': 9,
          'throughMs': 3000,
          'nextCursor': null,
          'series': <Map<String, dynamic>>[
            <String, dynamic>{
              'id': 'tps',
              'name': 'TPS',
              'suffix': ' tps',
              'points': <List<num>>[
                <num>[1000, 19.5, 19.0, 20.0, 19.8, 2],
              ],
            },
          ],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        requests++;
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        if (req.url.path.endsWith('/catalog')) {
          return http.Response(
            jsonEncode(<String, dynamic>{
              'data': <Map<String, dynamic>>[
                <String, dynamic>{
                  'id': 'tps',
                  'name': 'TPS',
                  'suffix': ' tps',
                  'firstTimestampMs': 1000,
                  'lastTimestampMs': 3000,
                  'active': true,
                },
              ],
            }),
            200,
          );
        }
        expect(req.url.path, equals('/api/v1/metrics/history'));
        expect(req.url.queryParameters['ids'], equals('tps'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<MetricHistoryDescriptor> catalog = await client
          .historyCatalog();
      final MetricHistoryPage page = await client.historyPage(
        ids: <String>['tps'],
        from: DateTime.fromMillisecondsSinceEpoch(1000),
        to: DateTime.fromMillisecondsSinceEpoch(3000),
      );

      expect(requests, equals(2));
      expect(catalog.single.id, equals('tps'));
      expect(page.resolution, equals(const Duration(seconds: 1)));
      expect(page.series.single.points.single.average, equals(19.5));
      expect(page.series.single.points.single.count, equals(2));
    });
  });

  group('ReactClient.identity()', () {
    test('GETs /identity and decodes the data envelope', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'version': '1.2.3',
          'serverName': 'TestServer',
          'folia': true,
          'serverId': '127.0.0.1:9696',
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/identity'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final IdentityInfo info = await client.identity();

      expect(info.version, equals('1.2.3'));
      expect(info.serverName, equals('TestServer'));
      expect(info.folia, isTrue);
      expect(info.serverId, equals('127.0.0.1:9696'));
    });
  });

  group('ReactClient.executeConsole()', () {
    test('POSTs an authenticated monotonic console command', () async {
      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('POST'));
        expect(req.url.path, equals('/api/v1/console/execute'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        expect(int.tryParse(req.headers['x-react-counter'] ?? ''), isNotNull);
        expect(
          jsonDecode(req.body),
          equals(<String, dynamic>{'command': 'say hello'}),
        );
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{'dispatched': true},
          }),
          202,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);

      expect(await client.executeConsole('say hello'), isTrue);
    });

    test('surfaces validation errors from the server', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
          jsonEncode(<String, dynamic>{
            'error': <String, dynamic>{'message': 'command is blank'},
          }),
          400,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.executeConsole(''),
        throwsA(isA<ReactBadRequest>()),
      );
    });
  });

  group('ReactClient player navigation', () {
    test(
      'lists players and posts a confirmed integral teleport target',
      () async {
        int requests = 0;
        final MockClient mock = MockClient((http.Request request) async {
          requests++;
          expect(request.headers['authorization'], equals('Bearer tok-abc'));
          if (request.method == 'GET') {
            expect(request.url.path, equals('/api/v1/players'));
            return http.Response(
              jsonEncode(<String, dynamic>{
                'data': <Map<String, dynamic>>[
                  <String, dynamic>{'id': 'player-id', 'name': 'Alice'},
                ],
              }),
              200,
            );
          }
          expect(request.method, equals('POST'));
          expect(
            request.url.path,
            equals('/api/v1/players/player-id/teleport'),
          );
          expect(
            int.tryParse(request.headers['x-react-counter'] ?? ''),
            isNotNull,
          );
          expect(
            jsonDecode(request.body),
            equals(<String, dynamic>{
              'worldKey': 'minecraft:overworld',
              'blockX': -24,
              'blockZ': 40,
              'confirm': true,
            }),
          );
          return http.Response(
            jsonEncode(<String, dynamic>{
              'data': <String, dynamic>{
                'playerId': 'player-id',
                'playerName': 'Alice',
                'status': 'queued',
                'worldKey': 'minecraft:overworld',
                'blockX': -24,
                'blockZ': 40,
              },
            }),
            202,
          );
        });
        final ReactClient client = ReactClient(cred, client: mock);

        final List<OnlinePlayerInfo> players = await client.players();
        final PlayerTeleportResult result = await client.teleportPlayer(
          players.single.id,
          worldKey: 'minecraft:overworld',
          blockX: -24,
          blockZ: 40,
        );

        expect(requests, equals(2));
        expect(players.single.name, equals('Alice'));
        expect(result.status, equals('queued'));
        expect(result.blockX, equals(-24));
      },
    );

    test('surfaces an admin-scope rejection from player listing', () async {
      final MockClient mock = MockClient((http.Request request) async {
        return http.Response(
          jsonEncode(<String, dynamic>{
            'error': <String, dynamic>{'message': 'Insufficient scope: admin'},
          }),
          403,
        );
      });

      await expectLater(
        ReactClient(cred, client: mock).players(),
        throwsA(isA<ReactForbidden>()),
      );
    });
  });

  group('ReactClient URL scheme', () {
    test('uses https:// scheme when secure is true', () async {
      const ServerCredential secureCred = ServerCredential(
        id: 'secure-test',
        label: 'Secure',
        host: 'example.com',
        port: 443,
        bearer: 'tok-secure',
        secure: true,
      );

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.scheme, equals('https'));
        expect(req.url.host, equals('example.com'));
        expect(req.url.port, equals(443));
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'sequence': 1,
              'capturedAtMs': 1750000000000,
              'samplers': <Map<String, dynamic>>[],
            },
          }),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(secureCred, client: mock);
      final ServerSnapshot snapshot = await client.metrics();
      expect(snapshot.byId.isEmpty, isTrue);
    });

    test('uses http:// scheme when secure is false (default)', () async {
      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.scheme, equals('http'));
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'sequence': 1,
              'capturedAtMs': 1750000000000,
              'samplers': <Map<String, dynamic>>[],
            },
          }),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ServerSnapshot snapshot = await client.metrics();
      expect(snapshot.byId.isEmpty, isTrue);
    });

    test('preserves reverse-proxy path and IPv6 authority', () async {
      const ServerCredential proxyCredential = ServerCredential(
        id: 'proxy-test',
        label: 'Proxy',
        host: '2001:db8::1',
        port: 9443,
        bearer: 'tok-proxy',
        secure: true,
        basePath: '/proxy/react',
      );
      final MockClient mock = MockClient((http.Request request) async {
        expect(request.url.host, equals('2001:db8::1'));
        expect(request.url.port, equals(9443));
        expect(request.url.path, equals('/proxy/react/api/v1/identity'));
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'version': '1.0.0',
              'serverName': 'Proxy',
              'folia': false,
              'serverId': 'proxy',
            },
          }),
          200,
        );
      });

      await ReactClient(proxyCredential, client: mock).identity();
    });
  });

  group('ReactClient error handling', () {
    test('401 response throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.metrics(), throwsA(isA<ReactAuthException>()));
    });

    test('401 on history catalog throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.historyCatalog(),
        throwsA(isA<ReactAuthException>()),
      );
    });

    test('connection error throws ReactUnavailable', () async {
      final MockClient mock = MockClient((http.Request req) async {
        throw http.ClientException('Connection refused');
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.metrics(), throwsA(isA<ReactUnavailable>()));
    });

    test('non-JSON error body throws ReactUnavailable', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('<html>Internal Server Error</html>', 500);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.metrics(), throwsA(isA<ReactUnavailable>()));
    });

    test(
      'timeout throws ReactUnavailable',
      () async {
        final MockClient mock = MockClient((http.Request req) async {
          await Future<void>.delayed(const Duration(seconds: 3));
          return http.Response('', 200);
        });

        final ReactClient client = ReactClient(cred, client: mock);

        await expectLater(client.metrics(), throwsA(isA<ReactUnavailable>()));
      },
      timeout: const Timeout(Duration(seconds: 10)),
    );
  });

  group('ReactClient.heatmaps()', () {
    test('GETs /api/v1/heatmaps and decodes summaries', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'entity-pressure-heatmap',
            'label': 'Entity Pressure',
          },
          <String, dynamic>{
            'id': 'redstone-activity-heatmap',
            'label': 'Redstone Activity',
          },
        ],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/heatmaps'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<HeatmapSummary> summaries = await client.heatmaps();

      expect(summaries.length, equals(2));
      expect(summaries[0].id, equals('entity-pressure-heatmap'));
      expect(summaries[0].label, equals('Entity Pressure'));
      expect(summaries[1].id, equals('redstone-activity-heatmap'));
      expect(summaries[1].label, equals('Redstone Activity'));
    });

    test('401 throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.heatmaps(), throwsA(isA<ReactAuthException>()));
    });
  });

  group('ReactClient.heatmap()', () {
    test('GETs /api/v1/heatmaps/{id} with no query and decodes grid', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'entity-pressure-heatmap',
          'label': 'Entity Pressure',
          'world': 'world',
          'centerChunkX': 0,
          'centerChunkZ': 0,
          'radius': 8,
          'originChunkX': -8,
          'originChunkZ': -8,
          'width': 17,
          'height': 17,
          'cellSizeChunks': 1,
          'capturedAtMs': 1750000000000,
          'spawnChunkX': 0,
          'spawnChunkZ': 0,
          'min': 3.0,
          'max': 7.0,
          'cells': <Map<String, dynamic>>[
            <String, dynamic>{
              'x': 0,
              'z': 0,
              'sizeChunks': 1,
              'score': 3.0,
              'averageScore': 2.5,
              'samples': 4,
            },
            <String, dynamic>{
              'x': 1,
              'z': 0,
              'sizeChunks': 1,
              'score': 7.0,
              'averageScore': 6.0,
              'samples': 5,
            },
          ],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(
          req.url.path,
          equals('/api/v1/heatmaps/entity-pressure-heatmap'),
        );
        expect(req.url.queryParameters, isEmpty);
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final HeatmapGrid grid = await client.heatmap('entity-pressure-heatmap');

      expect(grid.radius, equals(8));
      expect(grid.cells.length, equals(2));
      expect(grid.max, equals(7.0));
    });

    test('encodes world/center/radius query params', () async {
      final MockClient mock = MockClient((http.Request req) async {
        expect(
          req.url.queryParameters,
          equals(<String, String>{
            'world': 'minecraft:world_nether',
            'centerChunkX': '3',
            'centerChunkZ': '-2',
            'radius': '8',
          }),
        );
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'id': 'id',
              'label': 'Label',
              'world': 'minecraft:world_nether',
              'centerChunkX': 3,
              'centerChunkZ': -2,
              'radius': 8,
              'originChunkX': -5,
              'originChunkZ': -10,
              'width': 17,
              'height': 17,
              'cellSizeChunks': 1,
              'capturedAtMs': 1750000000000,
              'spawnChunkX': 0,
              'spawnChunkZ': 0,
              'min': 0.0,
              'max': 0.0,
              'cells': <dynamic>[],
            },
          }),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      await client.heatmap(
        'id',
        world: 'minecraft:world_nether',
        centerChunkX: 3,
        centerChunkZ: -2,
        radius: 8,
      );
    });

    test('404 throws ReactUnavailable', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('{"error":"not found"}', 404);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.heatmap('nonexistent-heatmap'),
        throwsA(isA<ReactUnavailable>()),
      );
    });

    test('empty cells yields empty grid', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'x',
          'label': 'X',
          'world': 'world',
          'centerChunkX': 0,
          'centerChunkZ': 0,
          'radius': 4,
          'originChunkX': -4,
          'originChunkZ': -4,
          'width': 9,
          'height': 9,
          'cellSizeChunks': 1,
          'capturedAtMs': 1750000000000,
          'spawnChunkX': 0,
          'spawnChunkZ': 0,
          'min': 0.0,
          'max': 0.0,
          'cells': <dynamic>[],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final HeatmapGrid grid = await client.heatmap('x');

      expect(grid.cells.isEmpty, isTrue);
    });
  });
}
