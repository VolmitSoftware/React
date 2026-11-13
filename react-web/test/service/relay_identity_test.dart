library;

import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:test/test.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/service/relay_identity.dart';

void main() {
  const List<int> spkiPrefix = <int>[
    0x30,
    0x2a,
    0x30,
    0x05,
    0x06,
    0x03,
    0x2b,
    0x65,
    0x70,
    0x03,
    0x21,
    0x00,
  ];

  final List<int> rawKey = List<int>.generate(32, (int i) => i + 1);

  final List<int> x509Bytes = <int>[...spkiPrefix, ...rawKey];

  String buildExpectedFingerprint(List<int> bytes) {
    final List<int> digest = sha256.convert(bytes).bytes;
    return digest
        .map((int byte) => byte.toRadixString(16).padLeft(2, '0'))
        .join();
  }

  final String expectedFingerprint = buildExpectedFingerprint(x509Bytes);

  final String pubKeyBase64 = base64Url.encode(x509Bytes);

  group('keyFingerprint', () {
    test('computes full lowercase SHA-256 hex over X509 bytes', () {
      final String fp = keyFingerprint(pubKeyBase64);
      expect(fp, equals(expectedFingerprint));
      expect(fp, hasLength(64));
    });

    test('is empty for text that is not base64url', () {
      expect(keyFingerprint('!!!not-base64!!!'), isEmpty);
    });
  });

  group('RelayIdentity.pinMatches', () {
    test('null pinnedFingerprint always matches (legacy direct mode)', () {
      expect(
        RelayIdentity.pinMatches(
          pinnedFingerprint: null,
          identityServerId: 'aa:bb:cc:dd:ee:ff:00:11',
        ),
        isTrue,
      );
    });

    test('matching fingerprint returns true', () {
      const String fp = 'aa:bb:cc:dd:ee:ff:00:11';
      expect(
        RelayIdentity.pinMatches(pinnedFingerprint: fp, identityServerId: fp),
        isTrue,
      );
    });

    test('mismatched fingerprint returns false', () {
      expect(
        RelayIdentity.pinMatches(
          pinnedFingerprint: 'aa:bb:cc:dd:ee:ff:00:11',
          identityServerId: 'ff:ee:dd:cc:bb:aa:11:00',
        ),
        isFalse,
      );
    });
  });
}
