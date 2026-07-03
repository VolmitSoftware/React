import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:reactor_relay/reactor_relay.dart';
import 'package:test/test.dart';

void main() {
  group('fingerprint', () {
    test('deterministic vector: 44 zero bytes matches inline sha256 computation',
        () {
      final List<int> input = List<int>.filled(44, 0);

      final List<int> sha256Bytes = sha256.convert(input).bytes;
      final String expected = sha256Bytes
          .map((int b) => b.toRadixString(16).padLeft(2, '0'))
          .join();

      expect(fingerprint(input), equals(expected));
    });

    test('output is 64-char lowercase hex SHA-256', () {
      final List<int> input = List<int>.filled(44, 0);
      final String result = fingerprint(input);

      expect(result.length, equals(64));
      expect(RegExp(r'^[0-9a-f]{64}$').hasMatch(result), isTrue);
    });

    test('decodeServerPubKey returns null for a malformed string', () {
      expect(decodeServerPubKey('not-valid-base64!@#'), isNull);
    });

    test('decodeServerPubKey returns null for empty string', () {
      expect(decodeServerPubKey(''), isNull);
    });

    test('fingerprintOfBase64 round-trips through decodeServerPubKey', () {
      final List<int> rawBytes = List<int>.generate(44, (int i) => i);
      final String b64 = base64UrlEncode(rawBytes).replaceAll('=', '');

      final List<int>? decoded = decodeServerPubKey(b64);
      expect(decoded, isNotNull);
      expect(fingerprintOfBase64(b64), equals(fingerprint(decoded!)));
    });
  });
}
