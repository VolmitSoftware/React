import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/environment_info.dart';
import 'package:react_web/model/incident_status.dart';
import 'package:react_web/model/server_credential.dart';
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

  group('ReactClient.incidents()', () {
    test(
      'GETs /api/v1/incidents with Bearer and decodes IncidentStatus',
      () async {
        final Map<String, dynamic> body = <String, dynamic>{
          'data': <String, dynamic>{
            'score': 55.0,
            'scoreAvailable': true,
            'sampledAtMs': 1234,
            'state': 'CRITICAL',
            'contributors': <Map<String, dynamic>>[
              <String, dynamic>{
                'id': 'tick-ms-p95',
                'label': 'Tick P95',
                'available': true,
                'weight': 0.5,
                'value': 120.0,
                'display': '120 ms',
                'pressure': 0.7,
                'scorePoints': 35.0,
                'minimum': 50.0,
                'maximum': 150.0,
              },
            ],
            'incidents': <Map<String, dynamic>>[],
          },
        };

        final MockClient mock = MockClient((http.Request req) async {
          expect(req.method, equals('GET'));
          expect(req.url.path, equals('/api/v1/incidents'));
          expect(req.headers['authorization'], equals('Bearer tok-abc'));
          return http.Response(
            jsonEncode(body),
            200,
            headers: <String, String>{'content-type': 'application/json'},
          );
        });

        final ReactClient client = ReactClient(cred, client: mock);
        final IncidentStatus status = await client.incidents();

        expect(status.score, equals(55.0));
        expect(status.state, equals('CRITICAL'));
        expect(status.contributors.length, equals(1));
        expect(status.contributors[0].label, equals('Tick P95'));
      },
    );

    test('401 response throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.incidents(), throwsA(isA<ReactAuthException>()));
    });
  });

  group('ReactClient.environment()', () {
    test('GETs /api/v1/environment and decodes EnvironmentInfo', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'cpu': <String, dynamic>{'model': 'M3', 'cores': 8},
          'memory': <String, dynamic>{'usedMb': 1000},
          'jvm': <String, dynamic>{'version': '25'},
          'server': <String, dynamic>{'brand': 'Purpur'},
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/environment'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final EnvironmentInfo info = await client.environment();

      expect(info.cpu['model'], equals('M3'));
      expect(info.server['brand'], equals('Purpur'));
    });
  });
}
