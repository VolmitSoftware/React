import 'dart:convert';

import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';

void main() {
  group('RelayFrame', () {
    test('round-trips all fields through encode/decode', () {
      final RelayFrame original = RelayFrame(
        type: RelayFrameType.route,
        serverId: 's1',
        requestId: 'r1',
        payload: <String, dynamic>{'method': 'GET'},
      );

      final String encoded = original.encode();
      final RelayFrame? decoded = RelayFrame.decode(encoded);

      expect(decoded, isNotNull);
      expect(decoded!.type, equals(RelayFrameType.route));
      expect(decoded.serverId, equals('s1'));
      expect(decoded.requestId, equals('r1'));
      expect(decoded.payload, equals(<String, dynamic>{'method': 'GET'}));
    });

    test('decode returns null for non-JSON input', () {
      expect(RelayFrame.decode('not json'), isNull);
    });

    test('decode returns null when type key is missing', () {
      final String json = jsonEncode(<String, dynamic>{'serverId': 'x'});
      expect(RelayFrame.decode(json), isNull);
    });

    test('decode returns null when type is empty string', () {
      final String json = jsonEncode(<String, dynamic>{
        'type': '',
        'serverId': 'x',
      });
      expect(RelayFrame.decode(json), isNull);
    });

    test('toJson omits null optional keys', () {
      final RelayFrame frame = RelayFrame(type: RelayFrameType.data);
      final Map<String, dynamic> json = frame.toJson();

      expect(json.containsKey('serverId'), isFalse);
      expect(json.containsKey('requestId'), isFalse);
      expect(json.containsKey('payload'), isFalse);
    });

    test('toJson includes type', () {
      final RelayFrame frame = RelayFrame(type: RelayFrameType.register);
      final Map<String, dynamic> json = frame.toJson();
      expect(json['type'], equals(RelayFrameType.register));
    });

    test('RelayFrameType constants have expected values', () {
      expect(RelayFrameType.register, equals('register'));
      expect(RelayFrameType.subscribe, equals('subscribe'));
      expect(RelayFrameType.route, equals('route'));
      expect(RelayFrameType.data, equals('data'));
      expect(RelayFrameType.error, equals('error'));
      expect(RelayFrameType.challenge, equals('challenge'));
    });

    test('fromJson reconstructs all fields', () {
      final Map<String, dynamic> json = <String, dynamic>{
        'type': 'data',
        'serverId': 'sv',
        'requestId': 'rq',
        'payload': <String, dynamic>{'k': 'v'},
      };
      final RelayFrame frame = RelayFrame.fromJson(json);
      expect(frame.type, equals('data'));
      expect(frame.serverId, equals('sv'));
      expect(frame.requestId, equals('rq'));
      expect(frame.payload, equals(<String, dynamic>{'k': 'v'}));
    });

    test('decode returns null when payload is wrong type (not a map)', () {
      expect(
        RelayFrame.decode('{"type":"route","payload":"not-a-map"}'),
        isNull,
      );
    });

    test('decode returns null when serverId is wrong type (not a string)', () {
      expect(RelayFrame.decode('{"type":"route","serverId":42}'), isNull);
    });

    test('decode returns null when requestId is wrong type (not a string)', () {
      expect(RelayFrame.decode('{"type":"route","requestId":true}'), isNull);
    });

    test('decode rejects unknown top-level fields', () {
      expect(RelayFrame.decode('{"type":"route","unexpected":true}'), isNull);
    });

    test('decode enforces an optional UTF-8 byte limit', () {
      final String encoded = RelayFrame(
        type: RelayFrameType.route,
        payload: <String, dynamic>{'value': 'x' * 256},
      ).encode();
      expect(RelayFrame.decode(encoded, maximumBytes: 64), isNull);
      expect(RelayFrame.decode(encoded, maximumBytes: 1024), isNotNull);
    });

    test('decode rejects empty and overlong identifiers', () {
      expect(RelayFrame.decode('{"type":"route","requestId":""}'), isNull);
      final String overlong = 'x' * (RelayFrame.maxRequestIdCharacters + 1);
      expect(
        RelayFrame.decode(
          jsonEncode(<String, dynamic>{
            'type': RelayFrameType.route,
            'requestId': overlong,
          }),
        ),
        isNull,
      );
    });
  });
}
