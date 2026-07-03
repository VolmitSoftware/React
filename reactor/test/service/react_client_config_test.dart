import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:reactor/model/config_tree.dart';
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

  Map<String, dynamic> _configBody(double gcValue) => <String, dynamic>{
        'data': <String, dynamic>{
          'sections': <dynamic>[
            <String, dynamic>{
              'name': 'Memory',
              'nodes': <dynamic>[
                <String, dynamic>{
                  'key': 'gc.threshold',
                  'label': 'GC %',
                  'type': 'double',
                  'value': gcValue,
                  'options': <dynamic>[],
                  'doc': '',
                },
              ],
            },
          ],
        },
      };

  group('ReactClient.config()', () {
    test('GETs /api/v1/config with Bearer and decodes ConfigTree', () async {
      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/config'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(jsonEncode(_configBody(80.0)), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ConfigTree tree = await client.config();

      expect(tree.sections.length, equals(1));
      expect(tree.sections[0].name, equals('Memory'));
      expect(tree.sections[0].nodes.length, equals(1));
      expect(tree.sections[0].nodes[0].key, equals('gc.threshold'));
      expect(tree.sections[0].nodes[0].value, equals(80.0));
    });
  });

  group('ReactClient.applyConfig()', () {
    test('PUTs /api/v1/config with counter header and flat changes map',
        () async {
      http.Request? captured;
      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(jsonEncode(_configBody(75.0)), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ConfigTree result =
          await client.applyConfig(<String, Object?>{'gc.threshold': 75.0});

      expect(captured, isNotNull);
      expect(captured!.method, equals('PUT'));
      expect(captured!.url.path, equals('/api/v1/config'));
      expect(captured!.headers['x-react-counter'], isNotNull);
      final Map<String, dynamic> sentBody =
          jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(sentBody, equals(<String, Object?>{'gc.threshold': 75.0}));
      expect(result.sections[0].nodes[0].value, equals(75.0));
    });

    test('403 response throws ReactForbidden', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
            jsonEncode(<String, dynamic>{
              'error': <String, dynamic>{'message': 'forbidden'}
            }),
            403,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.applyConfig(<String, Object?>{'gc.threshold': 50.0}),
        throwsA(isA<ReactForbidden>()),
      );
    });

    test('409 response throws ReactConflict', () async {
      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
            jsonEncode(<String, dynamic>{
              'error': <String, dynamic>{'message': 'conflict'}
            }),
            409,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.applyConfig(<String, Object?>{'gc.threshold': 50.0}),
        throwsA(isA<ReactConflict>()),
      );
    });
  });

  group('ReactClient.applyPreset()', () {
    test('POSTs /api/v1/config/preset/balanced with counter and returns tree',
        () async {
      http.Request? captured;
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'sections': <dynamic>[],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(jsonEncode(responseBody), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ConfigTree tree = await client.applyPreset('balanced');

      expect(captured, isNotNull);
      expect(captured!.method, equals('POST'));
      expect(captured!.url.path, equals('/api/v1/config/preset/balanced'));
      expect(captured!.headers['x-react-counter'], isNotNull);
      expect(tree, isA<ConfigTree>());
      expect(tree.sections, isEmpty);
    });
  });
}
