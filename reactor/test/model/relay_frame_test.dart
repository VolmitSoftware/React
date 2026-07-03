library;

import 'dart:convert';
import 'package:test/test.dart';
import 'package:reactor/model/relay_frame.dart';

void main() {
  group('RelayFrameType constants', () {
    test('register equals broker R1 string', () {
      expect(RelayFrameType.register, equals('register'));
    });
    test('subscribe equals broker R1 string', () {
      expect(RelayFrameType.subscribe, equals('subscribe'));
    });
    test('route equals broker R1 string', () {
      expect(RelayFrameType.route, equals('route'));
    });
    test('data equals broker R1 string', () {
      expect(RelayFrameType.data, equals('data'));
    });
    test('error equals broker R1 string', () {
      expect(RelayFrameType.error, equals('error'));
    });
    test('challenge equals broker R1 string', () {
      expect(RelayFrameType.challenge, equals('challenge'));
    });
  });

  group('RelayFrame encode/decode round-trip', () {
    test('full frame survives encode then decode', () {
      final RelayFrame original = RelayFrame(
        type: RelayFrameType.route,
        serverId: 'srv-1',
        requestId: 'req-42',
        payload: <String, dynamic>{'method': 'GET', 'path': '/api/status'},
      );
      final String encoded = original.encode();
      final RelayFrame? decoded = RelayFrame.decode(encoded);
      expect(decoded, isNotNull);
      expect(decoded!.type, equals(RelayFrameType.route));
      expect(decoded.serverId, equals('srv-1'));
      expect(decoded.requestId, equals('req-42'));
      expect(decoded.payload?['method'], equals('GET'));
      expect(decoded.payload?['path'], equals('/api/status'));
    });

    test('frame without optional fields round-trips', () {
      final RelayFrame minimal = RelayFrame(type: RelayFrameType.register);
      final RelayFrame? decoded = RelayFrame.decode(minimal.encode());
      expect(decoded, isNotNull);
      expect(decoded!.type, equals(RelayFrameType.register));
      expect(decoded.serverId, isNull);
      expect(decoded.requestId, isNull);
      expect(decoded.payload, isNull);
    });
  });

  group('RelayFrame toJson null-key omission', () {
    test('null serverId/requestId/payload are absent from toJson', () {
      final RelayFrame frame = RelayFrame(type: RelayFrameType.subscribe);
      final Map<String, dynamic> json = frame.toJson();
      expect(json.containsKey('serverId'), isFalse);
      expect(json.containsKey('requestId'), isFalse);
      expect(json.containsKey('payload'), isFalse);
    });

    test('non-null fields are present in toJson', () {
      final RelayFrame frame = RelayFrame(
        type: RelayFrameType.data,
        serverId: 'x',
        requestId: 'y',
        payload: <String, dynamic>{'k': 'v'},
      );
      final Map<String, dynamic> json = frame.toJson();
      expect(json['serverId'], equals('x'));
      expect(json['requestId'], equals('y'));
      expect((json['payload'] as Map<String, dynamic>)['k'], equals('v'));
    });
  });

  group('RelayFrame.fromJson', () {
    test('fromJson populates all fields', () {
      final Map<String, dynamic> json = <String, dynamic>{
        'type': 'error',
        'serverId': 'srv-2',
        'requestId': 'req-99',
        'payload': <String, dynamic>{'message': 'oops'},
      };
      final RelayFrame frame = RelayFrame.fromJson(json);
      expect(frame.type, equals(RelayFrameType.error));
      expect(frame.serverId, equals('srv-2'));
      expect(frame.requestId, equals('req-99'));
      expect(frame.payload?['message'], equals('oops'));
    });
  });

  group('RelayFrame.decode edge cases', () {
    test('decode returns null for invalid JSON', () {
      expect(RelayFrame.decode('not-json'), isNull);
    });

    test('decode returns null for empty string', () {
      expect(RelayFrame.decode(''), isNull);
    });
  });

  group('buildRoutePayload', () {
    test('produces expected shape', () {
      final Map<String, dynamic> payload = buildRoutePayload(
        method: 'POST',
        path: '/api/command',
        headers: <String, String>{'Authorization': 'Bearer tok'},
        body: <String, dynamic>{'action': 'restart'},
      );
      expect(payload['method'], equals('POST'));
      expect(payload['path'], equals('/api/command'));
      expect(
        (payload['headers'] as Map<String, String>)['Authorization'],
        equals('Bearer tok'),
      );
      expect(
        (payload['body'] as Map<String, dynamic>)['action'],
        equals('restart'),
      );
    });

    test('null body is absent from payload', () {
      final Map<String, dynamic> payload = buildRoutePayload(
        method: 'GET',
        path: '/api/ping',
        headers: <String, String>{},
      );
      expect(payload.containsKey('body'), isFalse);
    });
  });

  group('RelayResponse.fromDataPayload', () {
    test('parses data payload with data body', () {
      final RelayResponse response = RelayResponse.fromDataPayload(
        <String, dynamic>{
          'status': 200,
          'body': <String, dynamic>{'data': <String, dynamic>{'ok': true}},
        },
      );
      expect(response.status, equals(200));
      expect(response.body['data'], isNotNull);
    });

    test('parses data payload with error body', () {
      final RelayResponse response = RelayResponse.fromDataPayload(
        <String, dynamic>{
          'status': 500,
          'body': <String, dynamic>{'error': 'internal'},
        },
      );
      expect(response.status, equals(500));
      expect(response.body['error'], equals('internal'));
    });

    test('encode then decode a data frame preserves payload shape', () {
      final Map<String, dynamic> payload = <String, dynamic>{
        'status': 201,
        'body': <String, dynamic>{'data': <String, dynamic>{'id': 'abc'}},
      };
      final RelayFrame frame = RelayFrame(
        type: RelayFrameType.data,
        requestId: 'req-1',
        payload: payload,
      );
      final RelayFrame? restored = RelayFrame.decode(frame.encode());
      expect(restored, isNotNull);
      final RelayResponse resp =
          RelayResponse.fromDataPayload(restored!.payload!);
      expect(resp.status, equals(201));
    });
  });
}
