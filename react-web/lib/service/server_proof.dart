/// The relay proof: before a pairing secret or token goes through a relay,
/// the server signs a fresh challenge with its key, and the app checks the
/// signature against the key the pairing link pinned. A relay alone cannot
/// pose as the server.
library;

import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

import '../model/pairing_link.dart';

/// What the server signs ahead of the challenge.
const String proofPrefix = 'react-web-proof/1 ';

const String challengeHeader = 'X-React-Challenge';
const String serverKeyHeader = 'X-React-Server-Key';
const String proofHeader = 'X-React-Proof';

/// The 12-byte X.509 header of an Ed25519 SubjectPublicKeyInfo.
const List<int> _ed25519Header = <int>[
  0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00, //
];

/// 32 random bytes in base64url without padding (43 characters).
String newChallenge([Random? random]) {
  final Random source = random ?? Random.secure();
  return base64Url
      .encode(List<int>.generate(32, (int _) => source.nextInt(256)))
      .replaceAll('=', '');
}

/// The value of header [name] in [headers], ignoring case.
String? headerValue(Map<String, String> headers, String name) {
  final String wanted = name.toLowerCase();
  for (final MapEntry<String, String> entry in headers.entries) {
    if (entry.key.toLowerCase() == wanted) return entry.value;
  }
  return null;
}

/// Whether [headers] prove the answer came from the server pinned by
/// [serverKey] and [fingerprint]: the same key, hashing to the fingerprint,
/// and its Ed25519 signature over [proofPrefix] and [challenge].
Future<bool> verifyServerProof(
  Map<String, String> headers, {
  required String challenge,
  required String serverKey,
  required String fingerprint,
}) async {
  final String? presented = headerValue(headers, serverKeyHeader);
  final Uint8List? proof = _decode(headerValue(headers, proofHeader));
  if (presented == null || proof == null || proof.length != 64) return false;
  if (keyFingerprint(presented) != fingerprint ||
      keyFingerprint(serverKey) != fingerprint) {
    return false;
  }
  final Uint8List? spki = _decode(presented);
  if (spki == null || spki.length != 44) return false;
  for (int i = 0; i < _ed25519Header.length; i++) {
    if (spki[i] != _ed25519Header[i]) return false;
  }
  try {
    return await Ed25519().verify(
      utf8.encode('$proofPrefix$challenge'),
      signature: Signature(
        proof,
        publicKey: SimplePublicKey(
          spki.sublist(_ed25519Header.length),
          type: KeyPairType.ed25519,
        ),
      ),
    );
  } on Object {
    return false;
  }
}

Uint8List? _decode(String? text) {
  if (text == null || !RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(text)) return null;
  try {
    return base64Url.decode(base64Url.normalize(text));
  } on FormatException {
    return null;
  }
}
