library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/role_info.dart';
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

  group('ReactClient.whoami()', () {
    test('GETs /whoami and decodes admin role', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'role': 'admin',
          'scopes': <String>['read', 'op:execute', 'admin'],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/whoami'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final RoleInfo info = await client.whoami();

      expect(info.role, equals('admin'));
      expect(info.isAdmin, isTrue);
    });

    test('401 on whoami throws ReactAuthException', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response('Unauthorized', 401);
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(client.whoami(), throwsA(isA<ReactAuthException>()));
    });
  });
}
