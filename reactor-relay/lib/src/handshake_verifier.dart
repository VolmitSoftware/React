import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';
import 'package:reactor_relay/src/fingerprint.dart';

class HandshakeVerifier {
  static const List<int> kEd25519SpkiPrefix = <int>[
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
  ];

  final Random _random = Random.secure();

  String issueNonce() {
    final List<int> bytes =
        List<int>.generate(32, (_) => _random.nextInt(256));
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  Future<bool> verify({
    required String nonceBase64Url,
    required String pubKeyBase64Url,
    required String signatureBase64Url,
  }) async {
    try {
      final List<int>? nonceBytes = decodeServerPubKey(nonceBase64Url);
      if (nonceBytes == null) return false;

      final List<int>? spkiBytes = decodeServerPubKey(pubKeyBase64Url);
      if (spkiBytes == null) return false;
      if (spkiBytes.length < 32) return false;

      final List<int> rawKey = spkiBytes.sublist(spkiBytes.length - 32);

      final List<int>? sigBytes = decodeServerPubKey(signatureBase64Url);
      if (sigBytes == null) return false;

      final Ed25519 algo = Ed25519();
      final SimplePublicKey publicKey =
          SimplePublicKey(rawKey, type: KeyPairType.ed25519);
      final Signature signature =
          Signature(sigBytes, publicKey: publicKey);

      return await algo.verify(nonceBytes, signature: signature);
    } catch (_) {
      return false;
    }
  }
}
