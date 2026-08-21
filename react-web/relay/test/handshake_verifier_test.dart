import 'dart:convert';

import 'package:cryptography/cryptography.dart';
import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';

String _b64UrlNoPad(List<int> bytes) =>
    base64Url.encode(bytes).replaceAll('=', '');

List<int> _b64UrlDecode(String value) {
  final int paddingNeeded = (4 - value.length % 4) % 4;
  return base64Url.decode(value + '=' * paddingNeeded);
}

void main() {
  group('HandshakeVerifier', () {
    late Ed25519 algo;
    late HandshakeVerifier verifier;
    late SimpleKeyPair kp;
    late SimplePublicKey pubKey;
    late String pubKeyBase64Url;
    late String nonceBase64Url;
    late List<int> nonceBytes;
    late String signatureBase64Url;

    setUpAll(() async {
      algo = Ed25519();
      verifier = HandshakeVerifier();
      kp = await algo.newKeyPair();
      pubKey = await kp.extractPublicKey();

      final List<int> rawPub = pubKey.bytes;
      final List<int> spki = HandshakeVerifier.kEd25519SpkiPrefix + rawPub;
      pubKeyBase64Url = _b64UrlNoPad(spki);

      nonceBase64Url = verifier.issueNonce();
      nonceBytes = _b64UrlDecode(nonceBase64Url);

      final Signature sig = await algo.sign(nonceBytes, keyPair: kp);
      signatureBase64Url = _b64UrlNoPad(sig.bytes);
    });

    test(
      'verify returns true for correct nonce, pubKey, and signature',
      () async {
        final bool result = await verifier.verify(
          nonceBase64Url: nonceBase64Url,
          pubKeyBase64Url: pubKeyBase64Url,
          signatureBase64Url: signatureBase64Url,
        );
        expect(result, isTrue);
      },
    );

    test('verify returns false when signature is tampered', () async {
      final List<int> sigBytes = _b64UrlDecode(signatureBase64Url);
      sigBytes[0] ^= 0xff;
      final String tamperedSig = _b64UrlNoPad(sigBytes);

      final bool result = await verifier.verify(
        nonceBase64Url: nonceBase64Url,
        pubKeyBase64Url: pubKeyBase64Url,
        signatureBase64Url: tamperedSig,
      );
      expect(result, isFalse);
    });

    test('verify returns false when nonce is different', () async {
      final String differentNonce = verifier.issueNonce();

      final bool result = await verifier.verify(
        nonceBase64Url: differentNonce,
        pubKeyBase64Url: pubKeyBase64Url,
        signatureBase64Url: signatureBase64Url,
      );
      expect(result, isFalse);
    });

    test('verify returns false for malformed pubKeyBase64Url', () async {
      final bool result = await verifier.verify(
        nonceBase64Url: nonceBase64Url,
        pubKeyBase64Url: '!!!not-valid-base64!!!',
        signatureBase64Url: signatureBase64Url,
      );
      expect(result, isFalse);
    });

    test(
      'verify rejects a public key with a non-Ed25519 SPKI prefix',
      () async {
        final List<int> invalidSpki = _b64UrlDecode(pubKeyBase64Url);
        invalidSpki[0] ^= 0xff;
        final bool result = await verifier.verify(
          nonceBase64Url: nonceBase64Url,
          pubKeyBase64Url: _b64UrlNoPad(invalidSpki),
          signatureBase64Url: signatureBase64Url,
        );
        expect(result, isFalse);
      },
    );

    test('verify rejects nonces and signatures with invalid lengths', () async {
      final bool shortNonce = await verifier.verify(
        nonceBase64Url: _b64UrlNoPad(List<int>.filled(31, 0)),
        pubKeyBase64Url: pubKeyBase64Url,
        signatureBase64Url: signatureBase64Url,
      );
      final bool shortSignature = await verifier.verify(
        nonceBase64Url: nonceBase64Url,
        pubKeyBase64Url: pubKeyBase64Url,
        signatureBase64Url: _b64UrlNoPad(List<int>.filled(63, 0)),
      );
      expect(shortNonce, isFalse);
      expect(shortSignature, isFalse);
    });

    test(
      'issueNonce emits URL-safe base64 without padding or standard chars',
      () {
        final String nonce = verifier.issueNonce();
        expect(nonce.contains('='), isFalse);
        expect(nonce.contains('+'), isFalse);
        expect(nonce.contains('/'), isFalse);
      },
    );

    test('issueNonce returns distinct values on successive calls', () {
      final String n1 = verifier.issueNonce();
      final String n2 = verifier.issueNonce();
      expect(n1, isNot(equals(n2)));
    });

    test('issueNonce decodes to exactly 32 bytes', () {
      final String nonce = verifier.issueNonce();
      final List<int> bytes = _b64UrlDecode(nonce);
      expect(bytes.length, equals(32));
    });

    test(
      'CROSS-LANGUAGE VECTOR: verify accepts a fixed Java-compatible (nonce, pubKey, sig)',
      () async {
        const String vectorNonceBase64Url =
            '__79_Pv6-fj39vX08_Lx8O_u7ezr6uno5-bl5OPi4eA';
        const String vectorPubKeyBase64Url =
            'MCowBQYDK2VwAyEAA6EHv_POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg';
        const String vectorSignatureBase64Url =
            'FKJzdLTgP9ZC137K-bnUbp5CfXVzFN6Y5yy691hrJFv-sCzOcFTkPauJLld7gD7z5zWD25KFbuRuSPCB6zX6Aw';

        final bool result = await verifier.verify(
          nonceBase64Url: vectorNonceBase64Url,
          pubKeyBase64Url: vectorPubKeyBase64Url,
          signatureBase64Url: vectorSignatureBase64Url,
        );
        expect(result, isTrue);
      },
    );

    test(
      'CROSS-LANGUAGE VECTOR: fingerprint of fixed pubKey matches shared hex',
      () {
        const String vectorPubKeyBase64Url =
            'MCowBQYDK2VwAyEAA6EHv_POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg';
        const String vectorFingerprintHex =
            'a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5';

        expect(
          fingerprintOfBase64(vectorPubKeyBase64Url),
          equals(vectorFingerprintHex),
        );
      },
    );
  });
}
