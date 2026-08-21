import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cryptography/cryptography.dart';
import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';
import 'package:web_socket_channel/io.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

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
  final bool hasNext = await iter.moveNext().timeout(
    const Duration(seconds: 5),
  );
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
    IOWebSocketChannel? appChB;
    StreamIterator<String>? agentIter;
    StreamIterator<String>? appIter;
    StreamIterator<String>? appIterB;

    setUp(() async {
      server = await startRelayServer(port: 0);
      port = server.port;
      agentCh = null;
      appCh = null;
      appChB = null;
      agentIter = null;
      appIter = null;
      appIterB = null;
    });

    tearDown(() async {
      await agentIter?.cancel();
      await appIter?.cancel();
      await appIterB?.cancel();
      await agentCh?.sink.close();
      await appCh?.sink.close();
      await appChB?.sink.close();
      await server.close(force: true);
    });

    Future<void> restartServer({
      RelayLimits limits = const RelayLimits(),
      Iterable<String>? allowedAppOrigins,
    }) async {
      await server.close(force: true);
      server = await startRelayServer(
        port: 0,
        limits: limits,
        allowedAppOrigins: allowedAppOrigins,
      );
      port = server.port;
    }

    Future<void> connectAndRegisterAgent() async {
      agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
      agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());
      final RelayFrame challenge = await _nextFrame(agentIter!);
      final String nonce = challenge.payload!['nonce'] as String;
      final String sig = await _signNonce(nonce, kp);
      agentCh!.sink.add(
        RelayFrame(
          type: RelayFrameType.register,
          serverId: fp,
          payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
        ).encode(),
      );
      final RelayFrame registered = await _nextFrame(agentIter!);
      expect(registered.type, RelayFrameType.registered);
    }

    test(
      'end-to-end happy path: agent handshakes, app subscribes, route round-trips',
      () async {
        agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
        agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());

        final RelayFrame challenge = await _nextFrame(agentIter!);
        expect(challenge.type, equals(RelayFrameType.challenge));
        final String nonce = challenge.payload!['nonce'] as String;

        final String sig = await _signNonce(nonce, kp);
        agentCh!.sink.add(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{'pubKey': pubKeyBase64Url, 'sig': sig},
          ).encode(),
        );

        final RelayFrame registered = await _nextFrame(agentIter!);
        expect(registered.type, equals(RelayFrameType.registered));
        expect(registered.serverId, equals(fp));

        appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());

        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );

        const String requestId = 'r1';
        appCh!.sink.add(
          RelayFrame(
            type: RelayFrameType.route,
            serverId: fp,
            requestId: requestId,
            payload: <String, dynamic>{
              'method': 'GET',
              'path': '/api/v1/identity',
              'headers': <String, dynamic>{'Authorization': 'Bearer x'},
            },
          ).encode(),
        );

        final RelayFrame routeToAgent = await _nextFrame(agentIter!);
        expect(routeToAgent.type, equals(RelayFrameType.route));
        expect(routeToAgent.requestId, isNot(equals(requestId)));
        expect(routeToAgent.payload?['method'], equals('GET'));
        expect(routeToAgent.payload?['path'], equals('/api/v1/identity'));
        final Map<String, dynamic> routeHeaders =
            routeToAgent.payload!['headers'] as Map<String, dynamic>;
        expect(routeHeaders['Authorization'], equals('Bearer x'));

        agentCh!.sink.add(
          RelayFrame(
            type: RelayFrameType.data,
            serverId: fp,
            requestId: routeToAgent.requestId,
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{
                'data': <String, dynamic>{'serverName': 'T'},
              },
            },
          ).encode(),
        );

        final RelayFrame dataToApp = await _nextFrame(appIter!);
        expect(dataToApp.type, equals(RelayFrameType.data));
        expect(dataToApp.requestId, equals(requestId));
        expect(dataToApp.payload?['status'], equals(200));
        final Map<String, dynamic> body =
            dataToApp.payload!['body'] as Map<String, dynamic>;
        final Map<String, dynamic> data = body['data'] as Map<String, dynamic>;
        expect(data['serverName'], equals('T'));
      },
    );

    test(
      'auth rejection over real socket: garbage signature closes agent channel and server stays offline',
      () async {
        agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
        agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());

        final RelayFrame challenge = await _nextFrame(agentIter!);
        expect(challenge.type, equals(RelayFrameType.challenge));

        agentCh!.sink.add(
          RelayFrame(
            type: RelayFrameType.register,
            serverId: fp,
            payload: <String, dynamic>{
              'pubKey': pubKeyBase64Url,
              'sig': _b64UrlNoPad(List<int>.filled(64, 0xFF)),
            },
          ).encode(),
        );

        bool sawError = false;
        bool streamClosed = false;
        while (true) {
          final bool hasNext = await agentIter!.moveNext().timeout(
            const Duration(seconds: 5),
            onTimeout: () => false,
          );
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
        expect(
          streamClosed,
          isTrue,
          reason: 'agent stream should be closed after auth failure',
        );

        appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());

        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );

        final RelayFrame offlineFrame = await _nextFrame(appIter!);
        expect(offlineFrame.type, equals(RelayFrameType.error));
        expect(offlineFrame.payload?['message'], equals('server offline'));
      },
    );

    test(
      'isolates two browser sockets that reuse the same request ID',
      () async {
        await connectAndRegisterAgent();
        appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appChB = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());
        appIterB = StreamIterator<String>(appChB!.stream.cast<String>());
        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );
        appChB!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );

        for (final MapEntry<IOWebSocketChannel, String> entry
            in <MapEntry<IOWebSocketChannel, String>>[
              MapEntry<IOWebSocketChannel, String>(appCh!, 'A'),
              MapEntry<IOWebSocketChannel, String>(appChB!, 'B'),
            ]) {
          entry.key.sink.add(
            RelayFrame(
              type: RelayFrameType.route,
              serverId: fp,
              requestId: 'same-id',
              payload: <String, dynamic>{
                'method': 'GET',
                'path': '/api/v1/identity',
                'headers': <String, dynamic>{'X-Caller': entry.value},
              },
            ).encode(),
          );
        }

        final RelayFrame firstRoute = await _nextFrame(agentIter!);
        final RelayFrame secondRoute = await _nextFrame(agentIter!);
        final Map<String, RelayFrame> routesByCaller = <String, RelayFrame>{};
        for (final RelayFrame route in <RelayFrame>[firstRoute, secondRoute]) {
          final Map<String, dynamic> headers =
              route.payload!['headers'] as Map<String, dynamic>;
          routesByCaller[headers['X-Caller'] as String] = route;
        }
        expect(routesByCaller['A']!.requestId, isNot('same-id'));
        expect(routesByCaller['B']!.requestId, isNot('same-id'));
        expect(
          routesByCaller['A']!.requestId,
          isNot(routesByCaller['B']!.requestId),
        );

        for (final String caller in <String>['B', 'A']) {
          agentCh!.sink.add(
            RelayFrame(
              type: RelayFrameType.data,
              serverId: fp,
              requestId: routesByCaller[caller]!.requestId,
              payload: <String, dynamic>{
                'status': 200,
                'body': <String, dynamic>{'caller': caller},
              },
            ).encode(),
          );
        }

        final RelayFrame responseA = await _nextFrame(appIter!);
        final RelayFrame responseB = await _nextFrame(appIterB!);
        expect(responseA.requestId, 'same-id');
        expect(responseB.requestId, 'same-id');
        expect(responseA.payload!['body'], <String, dynamic>{'caller': 'A'});
        expect(responseB.payload!['body'], <String, dynamic>{'caller': 'B'});
      },
    );

    test('health endpoint returns aggregate non-sensitive state', () async {
      final HttpClient client = HttpClient();
      try {
        final HttpClientRequest request = await client.get(
          '127.0.0.1',
          port,
          '/healthz',
        );
        final HttpClientResponse response = await request.close();
        final String body = await utf8.decoder.bind(response).join();
        final Map<String, dynamic> decoded =
            jsonDecode(body) as Map<String, dynamic>;

        expect(response.statusCode, HttpStatus.ok);
        expect(response.headers.contentType?.mimeType, 'application/json');
        expect(
          response.headers.value(HttpHeaders.cacheControlHeader),
          'no-store',
        );
        expect(decoded, <String, dynamic>{
          'status': 'ok',
          'onlineAgents': 0,
          'appSessions': 0,
          'pendingRequests': 0,
        });
      } finally {
        client.close(force: true);
      }
    });

    test(
      'browser origin allowlist rejects one origin and accepts another',
      () async {
        await restartServer(
          allowedAppOrigins: const <String>['https://react.example.com'],
        );
        final IOWebSocketChannel rejected = IOWebSocketChannel.connect(
          'ws://127.0.0.1:$port/app',
          headers: <String, dynamic>{'Origin': 'https://evil.example.com'},
        );
        await expectLater(
          rejected.ready,
          throwsA(isA<WebSocketChannelException>()),
        );

        appCh = IOWebSocketChannel.connect(
          'ws://127.0.0.1:$port/app',
          headers: <String, dynamic>{'Origin': 'https://react.example.com'},
        );
        await appCh!.ready;
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());
        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );
        final RelayFrame offline = await _nextFrame(appIter!);
        expect(offline.payload!['message'], 'server offline');
      },
    );

    test(
      'agent handshake timeout sends an error and closes the socket',
      () async {
        await restartServer(
          limits: const RelayLimits(
            handshakeTimeout: Duration(milliseconds: 30),
          ),
        );
        agentCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/agent');
        agentIter = StreamIterator<String>(agentCh!.stream.cast<String>());
        expect((await _nextFrame(agentIter!)).type, RelayFrameType.challenge);

        final RelayFrame error = await _nextFrame(agentIter!);
        expect(error.type, RelayFrameType.error);
        expect(error.payload!['message'], contains('timed out'));
        expect(
          await agentIter!.moveNext().timeout(const Duration(seconds: 2)),
          isFalse,
        );
      },
    );

    test(
      'app subscription timeout sends an error and closes the socket',
      () async {
        await restartServer(
          limits: const RelayLimits(
            subscriptionTimeout: Duration(milliseconds: 30),
          ),
        );
        appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());

        final RelayFrame error = await _nextFrame(appIter!);
        expect(error.type, RelayFrameType.error);
        expect(error.payload!['message'], contains('timed out'));
        expect(
          await appIter!.moveNext().timeout(const Duration(seconds: 2)),
          isFalse,
        );
      },
    );

    test('oversized text frame is rejected before JSON decoding', () async {
      await restartServer(limits: const RelayLimits(maxFrameBytes: 1024));
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());
      appCh!.sink.add(
        jsonEncode(<String, dynamic>{
          'type': RelayFrameType.subscribe,
          'serverId': fp,
          'payload': <String, dynamic>{'padding': 'x' * 2048},
        }),
      );

      final RelayFrame error = await _nextFrame(appIter!);
      expect(error.type, RelayFrameType.error);
      expect(error.payload!['message'], contains('frame size limit'));
    });

    test('per-connection message rate limit closes a flooding app', () async {
      await restartServer(
        limits: const RelayLimits(
          maxMessagesPerWindow: 1,
          messageRateWindow: Duration(minutes: 1),
        ),
      );
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());
      appCh!.sink.add(
        RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
      );
      expect(
        (await _nextFrame(appIter!)).payload!['message'],
        'server offline',
      );

      appCh!.sink.add(
        RelayFrame(
          type: RelayFrameType.route,
          serverId: fp,
          requestId: 'flood',
          payload: <String, dynamic>{
            'method': 'GET',
            'path': '/api/v1/identity',
            'headers': <String, dynamic>{},
          },
        ).encode(),
      );

      final RelayFrame error = await _nextFrame(appIter!);
      expect(error.type, RelayFrameType.error);
      expect(error.payload!['message'], contains('message rate limit'));
    });
  });
}
