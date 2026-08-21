library;

import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:test/test.dart';
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

  group('RelayIdentity.fingerprint', () {
    test('computes full lowercase SHA-256 hex over X509 bytes', () {
      final String fp = RelayIdentity.fingerprint(pubKeyBase64);
      expect(fp, equals(expectedFingerprint));
      expect(fp, hasLength(64));
    });

    test('matches hardcoded vector for known input', () {
      final List<int> known = <int>[
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
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x08,
        0x09,
        0x0a,
        0x0b,
        0x0c,
        0x0d,
        0x0e,
        0x0f,
        0x10,
        0x11,
        0x12,
        0x13,
        0x14,
        0x15,
        0x16,
        0x17,
        0x18,
        0x19,
        0x1a,
        0x1b,
        0x1c,
        0x1d,
        0x1e,
        0x1f,
        0x20,
      ];
      final String expected = buildExpectedFingerprint(known);
      final String actual = RelayIdentity.fingerprint(base64Url.encode(known));
      expect(actual, equals(expected));
    });

    test('returns false-safe empty string on bad base64 input', () {
      expect(
        () => RelayIdentity.fingerprint('!!!not-base64!!!'),
        returnsNormally,
      );
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

  group('RelayIdentity.codeConsistent', () {
    test('both null returns true', () {
      expect(
        RelayIdentity.codeConsistent(serverPubKey: null, fingerprint: null),
        isTrue,
      );
    });

    test('matching pubkey/fingerprint pair returns true', () {
      final String fp = RelayIdentity.fingerprint(pubKeyBase64);
      expect(
        RelayIdentity.codeConsistent(
          serverPubKey: pubKeyBase64,
          fingerprint: fp,
        ),
        isTrue,
      );
    });

    test('altered fingerprint returns false', () {
      expect(
        RelayIdentity.codeConsistent(
          serverPubKey: pubKeyBase64,
          fingerprint: 'tampered:fingerprint:value',
        ),
        isFalse,
      );
    });

    test('pubkey set but fingerprint null returns false', () {
      expect(
        RelayIdentity.codeConsistent(
          serverPubKey: pubKeyBase64,
          fingerprint: null,
        ),
        isFalse,
      );
    });

    test('fingerprint set but pubkey null returns false', () {
      expect(
        RelayIdentity.codeConsistent(
          serverPubKey: null,
          fingerprint: 'aa:bb:cc:dd:ee:ff:00:11',
        ),
        isFalse,
      );
    });

    test('never throws on decode error', () {
      expect(
        () => RelayIdentity.codeConsistent(
          serverPubKey: '!!!bad-base64!!!',
          fingerprint: 'some:fp',
        ),
        returnsNormally,
      );
    });
  });
}
