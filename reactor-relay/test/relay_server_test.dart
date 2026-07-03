import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cryptography/cryptography.dart';
import 'package:reactor_relay/reactor_relay.dart';
import 'package:test/test.dart';
import 'package:web_socket_channel/io.dart';

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

Future<RelayFrame> _nextFrame(StreamIterator<String> iter) async {
  final bool hasNext = await iter.moveNext().timeout(const Duration(seconds: 5));
  if (!hasNext) throw StateError('stream closed unexpectedly');
  final RelayFrame? frame = RelayFrame.decode(iter.current);
  if (frame == null) throw FormatException('invalid frame: ${iter.current}');
  return frame;
}

void main() {
  group('relay_server integration', () {
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

    late HttpServer server;
    late int port;
    IOWebSocketChannel? agentCh;
    IOWebSocketChannel? appCh;
    StreamIterator<String>? agentIter;
    StreamIterator<String>? appIter;

    setUp(() async {
      server = await startRelayServer(port: 0);
      port = server.port;
      agentCh = null;
      appCh = null;
      agentIter = null;
      appIter = null;
    });

    tearDown(() async {
      await agentIter?.cancel();
      await appIter?.cancel();
      await agentCh?.sink.close();
      await appCh?.sink.close();
      await server.close(force: true);
    });

    test('end-to-end happy path: agent handshakes, app subscribes, route round-trips', () async {
      agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
      agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());

      final RelayFrame challenge = await _nextFrame(agentIter!);
      expect(challenge.type, equals(RelayFrameType.challenge));
      final String nonce = challenge.payload!['nonce'] as String;

      final String sig = await _signNonce(nonce, kp);
      agentCh!.sink.add(RelayFrame(
        type: RelayFrameType.register,
        serverId: fp,
        payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
      ).encode());

      final RelayFrame registered = await _nextFrame(agentIter!);
      expect(registered.type, equals(RelayFrameType.registered));
      expect(registered.serverId, equals(fp));

      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());

      appCh!.sink.add(RelayFrame(
        type: RelayFrameType.subscribe,
        serverId: fp,
      ).encode());

      const String requestId = 'r1';
      appCh!.sink.add(RelayFrame(
        type: RelayFrameType.route,
        serverId: fp,
        requestId: requestId,
        payload: <String, dynamic>{
          'method': 'GET',
          'path': '/api/v1/identity',
          'headers': <String, dynamic>{'Authorization': 'Bearer x'},
        },
      ).encode());

      final RelayFrame routeToAgent = await _nextFrame(agentIter!);
      expect(routeToAgent.type, equals(RelayFrameType.route));
      expect(routeToAgent.requestId, equals(requestId));
      expect(routeToAgent.payload?['method'], equals('GET'));
      expect(routeToAgent.payload?['path'], equals('/api/v1/identity'));
      final Map<String, dynamic> routeHeaders =
          routeToAgent.payload!['headers'] as Map<String, dynamic>;
      expect(routeHeaders['Authorization'], equals('Bearer x'));

      agentCh!.sink.add(RelayFrame(
        type: RelayFrameType.data,
        serverId: fp,
        requestId: requestId,
        payload: <String, dynamic>{
          'status': 200,
          'body': <String, dynamic>{
            'data': <String, dynamic>{'serverName': 'T'},
          },
        },
      ).encode());

      final RelayFrame dataToApp = await _nextFrame(appIter!);
      expect(dataToApp.type, equals(RelayFrameType.data));
      expect(dataToApp.requestId, equals(requestId));
      expect(dataToApp.payload?['status'], equals(200));
      final Map<String, dynamic> body =
          dataToApp.payload!['body'] as Map<String, dynamic>;
      final Map<String, dynamic> data =
          body['data'] as Map<String, dynamic>;
      expect(data['serverName'], equals('T'));
    });

    test('auth rejection over real socket: garbage signature closes agent channel and server stays offline', () async {
      agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
      agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());

      final RelayFrame challenge = await _nextFrame(agentIter!);
      expect(challenge.type, equals(RelayFrameType.challenge));

      agentCh!.sink.add(RelayFrame(
        type: RelayFrameType.register,
        serverId: fp,
        payload: <String, dynamic>{
          'pubKey': pubKeyBase64Url,
          'sig': _b64UrlNoPad(List<int>.filled(64, 0xFF)),
        },
      ).encode());

      bool sawError = false;
      bool streamClosed = false;
      while (true) {
        final bool hasNext = await agentIter!
            .moveNext()
            .timeout(const Duration(seconds: 5), onTimeout: () => false);
        if (!hasNext) {
          streamClosed = true;
          break;
        }
        final RelayFrame? f = RelayFrame.decode(agentIter!.current);
        if (f != null && f.type == RelayFrameType.error) {
          sawError = true;
        }
      }
      expect(sawError, isTrue, reason: 'agent should receive an error frame');
      expect(streamClosed, isTrue,
          reason: 'agent stream should be closed after auth failure');

      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());

      appCh!.sink.add(RelayFrame(
        type: RelayFrameType.subscribe,
        serverId: fp,
      ).encode());

      final RelayFrame offlineFrame = await _nextFrame(appIter!);
      expect(offlineFrame.type, equals(RelayFrameType.error));
      expect(offlineFrame.payload?['message'], equals('server offline'));
    });
  });
}
