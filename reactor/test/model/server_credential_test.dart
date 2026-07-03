library;

import 'package:test/test.dart';
import 'package:reactor/model/server_credential.dart';

void main() {
  group('ServerCredential.copyWith', () {
    const ServerCredential base = ServerCredential(
      id: 'srv-1',
      label: 'Old',
      host: 'localhost',
      port: 7979,
      bearer: 'tok',
      secure: false,
    );

    test('copyWith(label) keeps id/host/port/bearer/secure, changes label', () {
      final ServerCredential updated = base.copyWith(label: 'New');
      expect(updated.id, equals('srv-1'));
      expect(updated.label, equals('New'));
      expect(updated.host, equals('localhost'));
      expect(updated.port, equals(7979));
      expect(updated.bearer, equals('tok'));
      expect(updated.secure, isFalse);
    });

    test('copyWith() with no args returns equal-by-fields credential', () {
      final ServerCredential copy = base.copyWith();
      expect(copy.id, equals(base.id));
      expect(copy.label, equals(base.label));
      expect(copy.host, equals(base.host));
      expect(copy.port, equals(base.port));
      expect(copy.bearer, equals(base.bearer));
      expect(copy.secure, equals(base.secure));
    });

    test('toJson reflects the new label', () {
      final ServerCredential updated = base.copyWith(label: 'Renamed');
      final Map<String, dynamic> json = updated.toJson();
      expect(json['label'], equals('Renamed'));
      expect(json['id'], equals('srv-1'));
    });
  });

  group('ServerCredential relay fields', () {
    test('new credential without relay fields has all three null', () {
      const ServerCredential cred = ServerCredential(
        id: 'srv-1',
        label: 'Test',
        host: 'localhost',
        port: 7979,
        bearer: 'tok',
      );
      expect(cred.relayUrl, isNull);
      expect(cred.serverPubKey, isNull);
      expect(cred.fingerprint, isNull);
    });

    test('copyWith preserves relay fields when overriding other field', () {
      const ServerCredential cred = ServerCredential(
        id: 'srv-1',
        label: 'Test',
        host: 'localhost',
        port: 7979,
        bearer: 'tok',
        relayUrl: 'wss://relay.example.com',
        serverPubKey: 'AAABBB',
        fingerprint: 'aa:bb:cc',
      );
      final ServerCredential copy = cred.copyWith(label: 'New');
      expect(copy.relayUrl, equals('wss://relay.example.com'));
      expect(copy.serverPubKey, equals('AAABBB'));
      expect(copy.fingerprint, equals('aa:bb:cc'));
    });

    test('copyWith overrides relay fields individually', () {
      const ServerCredential cred = ServerCredential(
        id: 'srv-1',
        label: 'Test',
        host: 'localhost',
        port: 7979,
        bearer: 'tok',
        relayUrl: 'wss://old.relay.com',
        serverPubKey: 'OldKey',
        fingerprint: 'old:fp',
      );
      final ServerCredential copy = cred.copyWith(
        relayUrl: 'wss://new.relay.com',
        serverPubKey: 'NewKey',
        fingerprint: 'new:fp',
      );
      expect(copy.relayUrl, equals('wss://new.relay.com'));
      expect(copy.serverPubKey, equals('NewKey'));
      expect(copy.fingerprint, equals('new:fp'));
    });

    test('toJson/fromJson round-trip the three new fields', () {
      const ServerCredential cred = ServerCredential(
        id: 'srv-1',
        label: 'Test',
        host: 'localhost',
        port: 7979,
        bearer: 'tok',
        relayUrl: 'wss://relay.example.com',
        serverPubKey: 'pubkey123',
        fingerprint: 'ab:cd:ef',
      );
      final Map<String, dynamic> json = cred.toJson();
      final ServerCredential restored = ServerCredential.fromJson(json);
      expect(restored.relayUrl, equals('wss://relay.example.com'));
      expect(restored.serverPubKey, equals('pubkey123'));
      expect(restored.fingerprint, equals('ab:cd:ef'));
    });

    test('fromJson with no relay keys decodes to null fields', () {
      final Map<String, dynamic> json = <String, dynamic>{
        'id': 'srv-1',
        'label': 'Old',
        'host': 'localhost',
        'port': 7979,
        'bearer': 'tok',
        'secure': false,
      };
      final ServerCredential cred = ServerCredential.fromJson(json);
      expect(cred.relayUrl, isNull);
      expect(cred.serverPubKey, isNull);
      expect(cred.fingerprint, isNull);
    });
  });
}
