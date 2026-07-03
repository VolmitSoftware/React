import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:reactor/model/server_credential.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';

void main() {
  const ServerCredential cred = ServerCredential(
    id: 'srv1',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'tok-abc',
  );

  group('ReactClient.logs()', () {
    test('GETs /api/v1/logs with explicit limit and decodes string list',
        () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String>['[INFO] started', '[WARN] high mspt'],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/logs'));
        expect(req.url.queryParameters['limit'], equals('50'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(jsonEncode(body), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<String> result = await client.logs(limit: 50);

      expect(result, equals(<String>['[INFO] started', '[WARN] high mspt']));
    });

    test('uses default limit of 200 when limit is omitted', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String>['[INFO] boot'],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.queryParameters['limit'], equals('200'));
        return http.Response(jsonEncode(body), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<String> result = await client.logs();

      expect(result, equals(<String>['[INFO] boot']));
    });

    test('throws ReactUnavailable when data is not a list', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': 'not-a-list',
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(jsonEncode(body), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.logs(),
        throwsA(isA<ReactUnavailable>()),
      );
    });
  });
}
