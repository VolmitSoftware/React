import 'dart:convert';

import 'package:cryptography/cryptography.dart';
import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';

String _buildPubKeyBase64Url(List<int> rawPub) {
  final List<int> spki = HandshakeVerifier.kEd25519SpkiPrefix + rawPub;
  return base64Url.encode(spki).replaceAll('=', '');
}

String _b64UrlNoPad(List<int> bytes) =>
    base64Url.encode(bytes).replaceAll('=', '');

List<int> _b64UrlDecode(String value) {
  final int paddingNeeded = (4 - value.length % 4) % 4;
  return base64Url.decode(value + '=' * paddingNeeded);
}

Future<String> _signNonce(String nonceBase64Url, SimpleKeyPair kp) async {
  final Ed25519 algo = Ed25519();
  final List<int> nonceBytes = _b64UrlDecode(nonceBase64Url);
  final Signature sig = await algo.sign(nonceBytes, keyPair: kp);
  return _b64UrlNoPad(sig.bytes);
}

class _FakeSink implements RelaySink {
  final List<RelayFrame> sent = <RelayFrame>[];
  bool closed = false;
  String? closeMessage;

  @override
  void send(RelayFrame frame) => sent.add(frame);

  @override
  void closeWithError(String message) {
    closed = true;
    closeMessage = message;
  }
}

void main() {
  group('AgentSession', () {
    late Ed25519 algo;
    late SimpleKeyPair kp;
    late String pubKeyBase64Url;
    late String fp;

    setUpAll(() async {
      algo = Ed25519();
      kp = await algo.newKeyPair();
      final SimplePublicKey pubKey = await kp.extractPublicKey();
      pubKeyBase64Url = _buildPubKeyBase64Url(pubKey.bytes);
      fp = fingerprintOfBase64(pubKeyBase64Url);
    });

    test(
      'AUTH ACCEPT: valid handshake registers server and sends registered frame',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();
        final _FakeSink sink = _FakeSink();
        final AgentSession session = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sink,
        );

        expect(sink.sent, hasLength(1));
        final RelayFrame challenge = sink.sent.first;
        expect(challenge.type, equals(RelayFrameType.challenge));
        final String nonce = challenge.payload!['nonce'] as String;

        final String sig = await _signNonce(nonce, kp);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );

        expect(broker.isOnline(fp), isTrue);
        expect(sink.sent, hasLength(2));
        final RelayFrame registered = sink.sent.last;
        expect(registered.type, equals(RelayFrameType.registered));
        expect(registered.serverId, equals(fp));
      },
    );

    test(
      'AUTH REJECTION: register with signature over wrong nonce is rejected',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();
        final _FakeSink sink = _FakeSink();
        final AgentSession session = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sink,
        );

        final String wrongNonce = verifier.issueNonce();
        final String sig = await _signNonce(wrongNonce, kp);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );

        expect(broker.isOnline(fp), isFalse);
        expect(sink.closed, isTrue);
        expect(sink.closeMessage, contains('verification failed'));
      },
    );

    test(
      'registration rejects a serverId that does not match the public key',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();
        final _FakeSink sink = _FakeSink();
        final AgentSession session = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sink,
        );

        final String nonce = sink.sent.first.payload!['nonce'] as String;
        final String sig = await _signNonce(nonce, kp);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: 'wrong:server:id',
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );

        expect(broker.isOnline(fp), isFalse);
        expect(broker.isOnline('wrong:server:id'), isFalse);
        expect(sink.closed, isTrue);
        expect(sink.closeMessage, contains('does not match'));
      },
    );

    test(
      'REPLAY REJECTION: sig from session A rejected in session B',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();

        final _FakeSink sinkA = _FakeSink();
        final AgentSession sessionA = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sinkA,
        );
        final String nonceA = sinkA.sent.first.payload!['nonce'] as String;
        final String sigA = await _signNonce(nonceA, kp);

        final _FakeSink sinkB = _FakeSink();
        final AgentSession sessionB = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sinkB,
        );

        await sessionB.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sigA},
          ),
        );

        expect(broker.isOnline(fp), isFalse);
        expect(sinkB.closed, isTrue);

        sessionA.onDisconnect();
      },
    );

    test(
      'second register after success closes and unregisters the agent',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();
        final _FakeSink sink = _FakeSink();
        final AgentSession session = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: sink,
        );

        final String nonce = sink.sent.first.payload!['nonce'] as String;
        final String sig = await _signNonce(nonce, kp);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );
        expect(broker.isOnline(fp), isTrue);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );

        expect(sink.closed, isTrue);
        expect(sink.closeMessage, contains('unsupported frame type'));
        expect(broker.isOnline(fp), isFalse);
      },
    );

    test('onDisconnect unregisters the server from broker', () async {
      final RelayBroker broker = RelayBroker();
      final HandshakeVerifier verifier = HandshakeVerifier();
      final _FakeSink sink = _FakeSink();
      final AgentSession session = AgentSession(
        broker: broker,
        verifier: verifier,
        sink: sink,
      );

      final String nonce = sink.sent.first.payload!['nonce'] as String;
      final String sig = await _signNonce(nonce, kp);

      await session.onFrame(
        RelayFrame(
          type: RelayFrameType.register,
          serverId: fp,
          payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
        ),
      );
      expect(broker.isOnline(fp), isTrue);

      session.onDisconnect();
      expect(broker.isOnline(fp), isFalse);
    });

    test(
      'correlated data frames after registration reach the originating app',
      () async {
        final RelayBroker broker = RelayBroker();
        final HandshakeVerifier verifier = HandshakeVerifier();
        final _FakeSink agentSink = _FakeSink();
        final AgentSession session = AgentSession(
          broker: broker,
          verifier: verifier,
          sink: agentSink,
        );

        final String nonce = agentSink.sent.first.payload!['nonce'] as String;
        final String sig = await _signNonce(nonce, kp);

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ),
        );

        final _FakeSink appSink = _FakeSink();
        broker.subscribeApp(fp, appSink);
        broker.routeFromApp(
          fp,
          appSink,
          RelayFrame(
            type: RelayFrameType.route,
            serverId: fp,
            requestId: 'client-request',
            payload: <String, dynamic>{
              'method': 'GET',
              'path': '/api/v1/metrics',
              'headers': <String, dynamic>{'Authorization': 'Bearer test'},
            },
          ),
        );
        final RelayFrame routed = agentSink.sent.last;

        await session.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            serverId: fp,
            requestId: routed.requestId,
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{'metrics': 42},
            },
          ),
        );

        expect(appSink.sent, hasLength(1));
        expect(appSink.sent.first.type, equals(RelayFrameType.data));
        expect(appSink.sent.first.requestId, equals('client-request'));
      },
    );

    test('closes an agent that sends data before registering', () async {
      final RelayBroker broker = RelayBroker();
      final _FakeSink sink = _FakeSink();
      final AgentSession session = AgentSession(
        broker: broker,
        verifier: HandshakeVerifier(),
        sink: sink,
      );

      await session.onFrame(const RelayFrame(type: RelayFrameType.data));

      expect(sink.closed, isTrue);
      expect(sink.closeMessage, contains('must register'));
    });

    test('closes an agent that misses the handshake deadline', () async {
      final RelayBroker broker = RelayBroker();
      final _FakeSink sink = _FakeSink();
      AgentSession(
        broker: broker,
        verifier: HandshakeVerifier(),
        sink: sink,
        handshakeTimeout: const Duration(milliseconds: 20),
      );

      await Future<void>.delayed(const Duration(milliseconds: 60));

      expect(sink.closed, isTrue);
      expect(sink.closeMessage, contains('timed out'));
    });
  });
}
