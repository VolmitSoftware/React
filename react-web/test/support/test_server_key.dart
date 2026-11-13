/// A server key for tests: an Ed25519 pair, its link fields, pairing links
/// and the relay proof the server would sign.
library;

import 'dart:convert';

import 'package:cryptography/cryptography.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/service/server_proof.dart';

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

class TestServerKey {
  TestServerKey._(this._pair, this.serverKey);

  final SimpleKeyPair _pair;

  /// The SubjectPublicKeyInfo in base64url, as links carry it.
  final String serverKey;

  String get fingerprint => keyFingerprint(serverKey);

  /// The bearer [pairAnswer] hands out.
  static final String bearer = 'tok_AbCdEfGhIjKlMnOp.${'B' * 43}';

  static Future<TestServerKey> create([int seed = 1]) async {
    final SimpleKeyPair pair = await Ed25519().newKeyPairFromSeed(
      List<int>.generate(32, (int i) => (seed * 31 + i) % 256),
    );
    final SimplePublicKey public = await pair.extractPublicKey();
    return TestServerKey._(pair, _b64(<int>[..._spkiHeader, ...public.bytes]));
  }

  /// The headers the server adds to an answer for [challenge].
  Future<Map<String, String>> proof(String challenge) async {
    final Signature signature = await Ed25519().sign(
      utf8.encode('$proofPrefix$challenge'),
      keyPair: _pair,
    );
    return <String, String>{
      serverKeyHeader: serverKey,
      proofHeader: _b64(signature.bytes),
    };
  }

  /// A pairing link's code for this key.
  String code({
    String? directUrl = 'http://127.0.0.1:9822',
    String? relayUrl,
    String tokenId = 'tok_AbCdEfGhIjKlMnOp',
    DateTime? expiresAt,
    String serverName = 'Lobby',
    List<String> scopes = const <String>['read', 'op:execute'],
    String? role = 'operator',
    String? label,
  }) {
    final Map<String, Object?> payload = <String, Object?>{
      'v': 1,
      'directUrl': ?directUrl,
      'relayUrl': ?relayUrl,
      'fingerprint': fingerprint,
      'serverKey': serverKey,
      'tokenId': tokenId,
      'secret': 'S' * 43,
      'expiresAt':
          (expiresAt ?? DateTime.now().toUtc().add(const Duration(minutes: 10)))
              .toIso8601String(),
      'serverName': serverName,
      'scopes': scopes,
      'role': ?role,
      'lifetimeSeconds': 604800,
      'label': ?label,
    };
    return '$pairingPrefix${_b64(utf8.encode(jsonEncode(payload)))}';
  }

  PairingLink link({
    String? directUrl = 'http://127.0.0.1:9822',
    String? relayUrl,
    DateTime? expiresAt,
    String? role = 'operator',
    List<String> scopes = const <String>['read', 'op:execute'],
  }) => PairingLink.parse(
    code(
      directUrl: directUrl,
      relayUrl: relayUrl,
      expiresAt: expiresAt,
      role: role,
      scopes: scopes,
    ),
  )!;

  /// The `data` of a successful `POST /api/v1/pair`.
  Map<String, Object?> pairAnswer({String role = 'operator'}) =>
      <String, Object?>{
        'token': bearer,
        'tokenId': 'tok_AbCdEfGhIjKlMnOp',
        'label': 'laptop',
        'role': role,
        'scopes': <String>['read', 'op:execute'],
        'expiresAt': '2026-11-20T10:00:00Z',
        'lifetimeSeconds': 604800,
        'fingerprint': fingerprint,
        'serverName': 'Lobby',
      };
}
