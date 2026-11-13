import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:test/test.dart';

/// An Ed25519 SubjectPublicKeyInfo: the 12-byte header and 32 key bytes.
final List<int> _keyBytes = <int>[
  0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00, //
  for (int i = 0; i < 32; i++) (i * 7 + 3) % 256,
];
final String _serverKey = base64Url.encode(_keyBytes).replaceAll('=', '');
final String _fingerprint = sha256.convert(_keyBytes).toString();

Map<String, Object?> _payload([
  Map<String, Object?> changes = const <String, Object?>{},
]) => <String, Object?>{
  'v': 1,
  'directUrl': 'http://127.0.0.1:9822',
  'relayUrl': 'ws://127.0.0.1:9821',
  'fingerprint': _fingerprint,
  'serverKey': _serverKey,
  'tokenId': 'tok_AbCdEfGhIjKlMnOp',
  'secret': 'S' * 43,
  'expiresAt': '2026-11-13T10:10:00Z',
  'serverName': 'Lobby',
  'scopes': <String>['read', 'op:execute'],
  'role': 'operator',
  'lifetimeSeconds': 604800,
  'label': 'laptop',
  ...changes,
}..removeWhere((String key, Object? value) => value == null);

String _code([Map<String, Object?> changes = const <String, Object?>{}]) =>
    'RCT3.${base64Url.encode(utf8.encode(jsonEncode(_payload(changes)))).replaceAll('=', '')}';

void main() {
  group('linkCodeIn', () {
    test('finds the code in a whole link, a bare code and wrapped text', () {
      final String code = _code();
      expect(linkCodeIn('http://127.0.0.1:8823/pair#$code'), code);
      expect(linkCodeIn('  $code  '), code);
      final String wrapped = '${code.substring(0, 30)}\n${code.substring(30)}';
      expect(linkCodeIn('Link ready: http://x/pair#$wrapped'), code);
    });

    test('is null for text without a code', () {
      expect(linkCodeIn(''), isNull);
      expect(linkCodeIn('RCT2.abc'), isNull);
      expect(linkCodeIn('https://react.volmitsoftware.com/pair'), isNull);
    });
  });

  group('PairingLink.parse', () {
    test('reads every field of a link', () {
      final PairingLink link = PairingLink.parse(
        'http://127.0.0.1:8823/pair#${_code()}',
      )!;
      expect(link.directUrl, 'http://127.0.0.1:9822');
      expect(link.relayUrl, 'ws://127.0.0.1:9821');
      expect(link.fingerprint, _fingerprint);
      expect(link.serverKey, _serverKey);
      expect(link.tokenId, 'tok_AbCdEfGhIjKlMnOp');
      expect(link.secret, 'S' * 43);
      expect(link.expiresAt, DateTime.utc(2026, 11, 13, 10, 10));
      expect(link.serverName, 'Lobby');
      expect(link.scopes, <String>['read', 'op:execute']);
      expect(link.role, 'operator');
      expect(link.lifetime, const Duration(days: 7));
      expect(link.label, 'laptop');
    });

    test('takes a link with one path only', () {
      expect(
        PairingLink.parse(_code(<String, Object?>{'relayUrl': null}))!.relayUrl,
        isNull,
      );
      expect(
        PairingLink.parse(_code(<String, Object?>{'directUrl': null}))!
            .directUrl,
        isNull,
      );
    });

    test('refuses a link whose key does not hash to its fingerprint', () {
      expect(
        PairingLink.parse(_code(<String, Object?>{'fingerprint': 'a' * 64})),
        isNull,
      );
    });

    test('refuses unknown fields, a missing path and malformed values', () {
      expect(PairingLink.parse(_code(<String, Object?>{'extra': 1})), isNull);
      expect(
        PairingLink.parse(
          _code(<String, Object?>{'directUrl': null, 'relayUrl': null}),
        ),
        isNull,
      );
      expect(PairingLink.parse(_code(<String, Object?>{'v': 2})), isNull);
      expect(
        PairingLink.parse(_code(<String, Object?>{'tokenId': 'abc'})),
        isNull,
      );
      expect(
        PairingLink.parse(_code(<String, Object?>{'secret': 'short'})),
        isNull,
      );
      expect(
        PairingLink.parse(
          _code(<String, Object?>{'directUrl': 'javascript:alert(1)'}),
        ),
        isNull,
      );
      expect(
        PairingLink.parse(
          _code(<String, Object?>{'relayUrl': 'https://relay.example.com'}),
        ),
        isNull,
      );
      expect(
        PairingLink.parse(_code(<String, Object?>{'expiresAt': 'soon'})),
        isNull,
      );
      expect(
        PairingLink.parse(_code(<String, Object?>{'scopes': 'read'})),
        isNull,
      );
      expect(PairingLink.parse('RCT3.!!!'), isNull);
      expect(PairingLink.parse('RCT3.${'A' * 5000}'), isNull);
    });

    test('a custom scope set has no role', () {
      final PairingLink link = PairingLink.parse(
        _code(<String, Object?>{
          'role': null,
          'scopes': <String>['read', 'console:read'],
        }),
      )!;
      expect(link.role, isNull);
      expect(link.scopes, <String>['read', 'console:read']);
    });
  });

  group('browserName', () {
    test('names the browser and the system from the user agent', () {
      expect(
        browserName(
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36',
        ),
        'Chrome on macOS',
      );
      expect(
        browserName(
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0',
        ),
        'Firefox on Windows',
      );
      expect(
        browserName(
          'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
        ),
        'Safari on iOS',
      );
      expect(
        browserName(
          'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 Edg/131.0',
        ),
        'Edge on Linux',
      );
      expect(browserName('curl/8.0'), 'React Web');
    });
  });

  group('isLocalNetworkHost', () {
    test('knows loopback, private and link-local addresses', () {
      for (final String host in <String>[
        'localhost',
        '127.0.0.1',
        '::1',
        '10.1.2.3',
        '172.16.0.1',
        '192.168.1.5',
        '169.254.0.9',
        'box.local',
      ]) {
        expect(isLocalNetworkHost(host), isTrue, reason: host);
      }
      for (final String host in <String>[
        '172.32.0.1',
        '8.8.8.8',
        'play.example.com',
      ]) {
        expect(isLocalNetworkHost(host), isFalse, reason: host);
      }
    });

    test(
      'an https page reaching plain http on the local network may be blocked',
      () {
        expect(
          browserMayBlock(
            'https://react.volmitsoftware.com',
            'http://127.0.0.1:9822',
          ),
          isTrue,
        );
        expect(
          browserMayBlock('http://127.0.0.1:8823', 'http://127.0.0.1:9822'),
          isFalse,
        );
        expect(
          browserMayBlock(
            'https://react.volmitsoftware.com',
            'https://play.example.com',
          ),
          isFalse,
        );
      },
    );
  });
}
