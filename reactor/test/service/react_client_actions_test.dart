import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:reactor/model/action_descriptor.dart';
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

  group('ReactClient.actions()', () {
    test('GETs /api/v1/actions and decodes ActionDescriptors', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'purge-entities',
            'name': 'Purge Entities',
            'description': 'Remove excess entities from loaded chunks',
            'destructive': true,
            'params': <Map<String, dynamic>>[
              <String, dynamic>{
                'key': 'radius',
                'label': 'Radius',
                'type': 'int',
                'required': true,
                'default': 64,
              },
            ],
          },
          <String, dynamic>{
            'id': 'hopper-network-normalize',
            'name': 'Normalize Hopper Network',
            'description': 'Rebalance hopper tick distribution',
            'destructive': false,
            'params': <dynamic>[],
          },
        ],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/actions'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(jsonEncode(body), 200,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<ActionDescriptor> descriptors = await client.actions();

      expect(descriptors.length, equals(2));
      expect(descriptors[0].destructive, isTrue);
      expect(descriptors[0].params.length, equals(1));
      expect(descriptors[0].params[0].key, equals('radius'));
      expect(descriptors[1].id, equals('hopper-network-normalize'));
      expect(descriptors[1].params, isEmpty);
    });
  });

  group('ReactClient.executeAction()', () {
    test('POSTs to /api/v1/actions/{id}/execute with counter header and body',
        () async {
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'ticketId': 'tk-9',
          'status': 'queued',
        },
      };

      http.Request? captured;
      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(jsonEncode(responseBody), 202,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ActionTicket ticket = await client.executeAction(
        'purge-entities',
        params: <String, Object?>{'radius': 64},
        confirm: true,
      );

      expect(captured, isNotNull);
      expect(captured!.method, equals('POST'));
      expect(captured!.url.path,
          equals('/api/v1/actions/purge-entities/execute'));
      expect(captured!.headers['x-react-counter'], isNotNull);
      final Map<String, dynamic> sentBody =
          jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(sentBody['params'], equals(<String, Object?>{'radius': 64}));
      expect(sentBody['confirm'], isTrue);
      expect(ticket.ticketId, equals('tk-9'));
      expect(ticket.status, equals('queued'));
    });

    test('409 response throws ReactConflict with server message', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'error': <String, dynamic>{
          'message': 'confirm required',
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(jsonEncode(body), 409,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.executeAction(
          'purge-entities',
          params: <String, Object?>{},
          confirm: false,
        ),
        throwsA(
          isA<ReactConflict>().having(
            (ReactConflict e) => e.message,
            'message',
            contains('confirm required'),
          ),
        ),
      );
    });

    test('403 response throws ReactForbidden', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'error': <String, dynamic>{
          'message': 'missing op:execute scope',
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(jsonEncode(body), 403,
            headers: <String, String>{'content-type': 'application/json'});
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.executeAction(
          'purge-entities',
          params: <String, Object?>{},
          confirm: true,
        ),
        throwsA(isA<ReactForbidden>()),
      );
    });
  });
}
