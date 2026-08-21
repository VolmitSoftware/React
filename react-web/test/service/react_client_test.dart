import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/sampler_sample.dart';
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

  group('ReactClient.metrics()', () {
    test('decodes a 2-sampler body', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'cpu',
            'name': 'CPU Usage',
            'value': 45.0,
            'suffix': '%',
            'min': 0.0,
            'max': 100.0,
            'history': <double>[40.0, 42.0, 45.0],
          },
          <String, dynamic>{
            'id': 'mem',
            'name': 'Memory',
            'value': 70.0,
            'suffix': 'MB',
            'min': 0.0,
            'max': 8192.0,
            'history': <double>[68.0, 69.0, 70.0],
          },
        ],
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
    });
  });

  group('ReactClient.history()', () {
    test('GETs /metrics/{id}/history and decodes the data envelope', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'tps',
          'name': 'TPS',
          'value': 19.5,
          'suffix': ' tps',
          'min': 0.0,
          'max': 20.0,
          'history': <double>[18.0, 19.0, 19.5],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/metrics/tps/history'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final SamplerSample sample = await client.history('tps');

      expect(sample.id, equals('tps'));
      expect(sample.value, equals(19.5));
      expect(
        sample.display,
        equals('19.5'),
        reason:
            'display falls back to value.toString() when absent from the payload',
      );
      expect(sample.history, equals(<double>[18.0, 19.0, 19.5]));
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
          jsonEncode(<String, dynamic>{'data': <Map<String, dynamic>>[]}),
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
          jsonEncode(<String, dynamic>{'data': <Map<String, dynamic>>[]}),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ServerSnapshot snapshot = await client.metrics();
      expect(snapshot.byId.isEmpty, isTrue);
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

    test('401 on history throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.history('cpu'),
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
          'min': 3.0,
          'max': 7.0,
          'cells': <Map<String, dynamic>>[
            <String, dynamic>{'x': 0, 'z': 0, 'score': 3.0},
            <String, dynamic>{'x': 1, 'z': 0, 'score': 7.0},
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
            'world': 'world_nether',
            'centerX': '3',
            'centerZ': '-2',
            'radius': '8',
          }),
        );
        return http.Response(
          jsonEncode(<String, dynamic>{
            'data': <String, dynamic>{
              'id': 'id',
              'label': 'Label',
              'world': 'world_nether',
              'centerChunkX': 3,
              'centerChunkZ': -2,
              'radius': 8,
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
        world: 'world_nether',
        centerX: 3,
        centerZ: -2,
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
