import 'dart:convert';

import 'package:cryptography/cryptography.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/service/server_proof.dart';
import 'package:test/test.dart';

const List<int> _spkiHeader = <int>[
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

String _b64(List<int> bytes) => base64Url.encode(bytes).replaceAll('=', '');

void main() {
  late SimpleKeyPair pair;
  late String serverKey;

  setUpAll(() async {
    pair = await Ed25519().newKeyPairFromSeed(
      List<int>.generate(32, (int i) => i + 1),
    );
    final SimplePublicKey public = await pair.extractPublicKey();
    serverKey = _b64(<int>[..._spkiHeader, ...public.bytes]);
  });

  Future<Map<String, String>> signed(
    String challenge, {
    String prefix = 'react-web-proof/1 ',
  }) async {
    final Signature signature = await Ed25519().sign(
      utf8.encode('$prefix$challenge'),
      keyPair: pair,
    );
    return <String, String>{
      'X-React-Server-Key': serverKey,
      'X-React-Proof': _b64(signature.bytes),
    };
  }

  test('a challenge is 43 base64url characters and new every time', () {
    final String one = newChallenge();
    expect(one, matches(RegExp(r'^[A-Za-z0-9_-]{43}$')));
    expect(newChallenge(), isNot(one));
  });

  test('accepts the pinned key signing this challenge', () async {
    final String challenge = newChallenge();
    expect(
      await verifyServerProof(
        await signed(challenge),
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: keyFingerprint(serverKey),
      ),
      isTrue,
    );
  });

  test('reads the headers whatever their case', () async {
    final String challenge = newChallenge();
    final Map<String, String> headers = <String, String>{
      for (final MapEntry<String, String> entry in (await signed(
        challenge,
      )).entries)
        entry.key.toLowerCase(): entry.value,
    };
    expect(
      await verifyServerProof(
        headers,
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: keyFingerprint(serverKey),
      ),
      isTrue,
    );
  });

  test('refuses another challenge, prefix, key or no proof', () async {
    final String challenge = newChallenge();
    final String fingerprint = keyFingerprint(serverKey);
    expect(
      await verifyServerProof(
        await signed(newChallenge()),
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: fingerprint,
      ),
      isFalse,
    );
    expect(
      await verifyServerProof(
        await signed(challenge, prefix: 'react-relay/1 '),
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: fingerprint,
      ),
      isFalse,
    );
    expect(
      await verifyServerProof(
        const <String, String>{},
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: fingerprint,
      ),
      isFalse,
    );
    final SimpleKeyPair other = await Ed25519().newKeyPairFromSeed(
      List<int>.filled(32, 9),
    );
    final String otherKey = _b64(<int>[
      ..._spkiHeader,
      ...(await other.extractPublicKey()).bytes,
    ]);
    final Signature forged = await Ed25519().sign(
      utf8.encode('react-web-proof/1 $challenge'),
      keyPair: other,
    );
    expect(
      await verifyServerProof(
        <String, String>{
          'X-React-Server-Key': otherKey,
          'X-React-Proof': _b64(forged.bytes),
        },
        challenge: challenge,
        serverKey: serverKey,
        fingerprint: fingerprint,
      ),
      isFalse,
    );
  });
}
