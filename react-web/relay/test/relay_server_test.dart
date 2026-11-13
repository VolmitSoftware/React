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

String _identityRoute(String serverId, String requestId) => RelayFrame(
  type: RelayFrameType.route,
  serverId: serverId,
  requestId: requestId,
  payload: <String, dynamic>{
    'method': 'GET',
    'path': '/api/v1/identity',
    'headers': <String, dynamic>{'Authorization': 'Bearer x'},
  },
).encode();

String _okReply(String serverId, String requestId) => RelayFrame(
  type: RelayFrameType.data,
  serverId: serverId,
  requestId: requestId,
  payload: <String, dynamic>{
    'status': 200,
    'body': <String, dynamic>{'ok': true},
  },
).encode();

Future<Map<String, dynamic>> _health(int port) async {
  final HttpClient client = HttpClient();
  try {
    final HttpClientRequest request = await client.get(
      '127.0.0.1',
      port,
      '/healthz',
    );
    final HttpClientResponse response = await request.close();
    return jsonDecode(await utf8.decoder.bind(response).join())
        as Map<String, dynamic>;
  } finally {
    client.close(force: true);
  }
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
        final RelayFrame status = await _nextFrame(appIter!);
        expect(status.type, RelayFrameType.status);
        expect(status.online, isTrue);

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

        final RelayFrame status = await _nextFrame(appIter!);
        expect(status.type, RelayFrameType.status);
        expect(status.online, isFalse);
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
        expect((await _nextFrame(appIter!)).online, isTrue);
        expect((await _nextFrame(appIterB!)).online, isTrue);

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
        expect((await _nextFrame(appIter!)).online, isFalse);
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

    test('a restarting relay closes every agent and app with 4012', () async {
      final RelayConnections connections = RelayConnections();
      await server.close(force: true);
      server = await startRelayServer(port: 0, connections: connections);
      port = server.port;
      await connectAndRegisterAgent();
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      await appCh!.ready;
      final Completer<int?> agentClosed = Completer<int?>();
      final Completer<int?> appClosed = Completer<int?>();
      agentIter!.moveNext().then((_) {
        if (!agentClosed.isCompleted) agentClosed.complete(agentCh!.closeCode);
      });
      final Completer<void> subscribed = Completer<void>();
      appCh!.stream.listen((Object? _) {
        if (!subscribed.isCompleted) subscribed.complete();
      }, onDone: () => appClosed.complete(appCh!.closeCode));
      // A subscribed browser is sent the agent's offline status while the
      // agent closes; it must still get the restart close.
      appCh!.sink.add(
        RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
      );
      await subscribed.future.timeout(const Duration(seconds: 5));
      expect(connections.openCount, 2);

      await connections.closeAll();

      expect(
        await agentClosed.future.timeout(const Duration(seconds: 5)),
        relayRestartCloseCode,
      );
      expect(
        await appClosed.future.timeout(const Duration(seconds: 5)),
        relayRestartCloseCode,
      );
      expect(connections.openCount, 0);
    });

    test('a refused route says why with a code', () async {
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());
      appCh!.sink.add(
        RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
      );
      expect((await _nextFrame(appIter!)).online, isFalse);
      expect((await _nextFrame(appIter!)).payload!['code'], 'server_offline');
      appCh!.sink.add(_identityRoute(fp, 'r1'));
      final RelayFrame refused = await _nextFrame(appIter!);
      expect(refused.payload!['status'], 503);
      final Map<String, dynamic> body =
          refused.payload!['body'] as Map<String, dynamic>;
      expect(body['error'], <String, dynamic>{
        'code': 'server_offline',
        'message': 'server offline',
      });
    });

    test('subscribers see the agent go offline and come back online', () async {
      await connectAndRegisterAgent();
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());
      appCh!.sink.add(
        RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
      );
      expect(
        (await _nextFrame(appIter!)).encode(),
        '{"type":"status","serverId":"$fp","online":true,'
        '"payload":{"online":true}}',
      );

      await agentIter!.cancel();
      await agentCh!.sink.close();
      expect(
        (await _nextFrame(appIter!)).encode(),
        '{"type":"status","serverId":"$fp","online":false,'
        '"payload":{"online":false}}',
      );

      await connectAndRegisterAgent();
      final RelayFrame online = await _nextFrame(appIter!);
      expect(online.type, RelayFrameType.status);
      expect(online.online, isTrue);
    });

    test(
      'over-rate app routes get a 429 reply and the socket stays open',
      () async {
        await restartServer(
          limits: const RelayLimits(
            maxAppMessagesPerWindow: 2,
            messageRateWindow: Duration(minutes: 1),
          ),
        );
        await connectAndRegisterAgent();
        appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
        appIter = StreamIterator<String>(appCh!.stream.cast<String>());
        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );
        expect((await _nextFrame(appIter!)).online, isTrue);

        appCh!.sink.add(_identityRoute(fp, 'r1'));
        final RelayFrame routed = await _nextFrame(agentIter!);
        agentCh!.sink.add(_okReply(fp, routed.requestId!));
        expect((await _nextFrame(appIter!)).payload!['status'], 200);

        appCh!.sink.add(_identityRoute(fp, 'r2'));
        expect(
          (await _nextFrame(appIter!)).encode(),
          '{"type":"data","serverId":"$fp","requestId":"r2","payload":'
          '{"status":429,"body":{"error":{"code":"relay_rate_limited","message":"relay rate limit exceeded"}}}}',
        );

        appCh!.sink.add(
          RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
        );
        appCh!.sink.add(_identityRoute(fp, 'r3'));
        final RelayFrame third = await _nextFrame(appIter!);
        expect(third.requestId, 'r3');
        expect(third.payload!['status'], 429);

        final Map<String, dynamic> health = await _health(port);
        expect(health['appSessions'], 1);
        expect(health['pendingRequests'], 0);
      },
    );

    test('an agent over its own message rate is closed', () async {
      await restartServer(
        limits: const RelayLimits(
          maxAgentMessagesPerWindow: 1,
          messageRateWindow: Duration(minutes: 1),
        ),
      );
      await connectAndRegisterAgent();
      agentCh!.sink.add(_okReply(fp, 'unsolicited'));

      final RelayFrame error = await _nextFrame(agentIter!);
      expect(error.type, RelayFrameType.error);
      expect(error.payload!['message'], contains('message rate limit'));
      expect(
        await agentIter!.moveNext().timeout(const Duration(seconds: 2)),
        isFalse,
      );
    });

    for (final int burstSize in <int>[300, 1000]) {
      test(
        'a burst of $burstSize routes gets exactly one reply each without closing the app',
        () async {
          await connectAndRegisterAgent();
          unawaited(() async {
            while (await agentIter!.moveNext()) {
              final RelayFrame? route = RelayFrame.decode(agentIter!.current);
              if (route?.type != RelayFrameType.route) continue;
              agentCh!.sink.add(_okReply(fp, route!.requestId!));
            }
          }());
          appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
          appIter = StreamIterator<String>(appCh!.stream.cast<String>());
          appCh!.sink.add(
            RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
          );
          expect((await _nextFrame(appIter!)).online, isTrue);

          for (int index = 0; index < burstSize; index++) {
            appCh!.sink.add(_identityRoute(fp, 'burst-$index'));
          }
          final Map<String, int> statusByRequest = <String, int>{};
          int rateLimited = 0;
          for (int index = 0; index < burstSize; index++) {
            final RelayFrame reply = await _nextFrame(appIter!);
            expect(reply.type, RelayFrameType.data);
            expect(statusByRequest.containsKey(reply.requestId), isFalse);
            statusByRequest[reply.requestId!] = reply.payload!['status'] as int;
            final Map<String, dynamic> body =
                reply.payload!['body'] as Map<String, dynamic>;
            final Object? error = body['error'];
            if (error is Map<String, dynamic> &&
                error['message'] == 'relay rate limit exceeded') {
              rateLimited++;
            }
          }
          expect(statusByRequest, hasLength(burstSize));
          expect(statusByRequest.values.toSet(), <int>{200, 429});
          expect(rateLimited, greaterThan(0));

          await Future<void>.delayed(const Duration(milliseconds: 1100));
          appCh!.sink.add(_identityRoute(fp, 'after-burst'));
          final RelayFrame after = await _nextFrame(appIter!);
          expect(after.requestId, 'after-burst');
          expect(after.payload!['status'], 200);
        },
      );
    }

    test('an agent with 1000 queued requests is not closed', () async {
      await restartServer(
        limits: const RelayLimits(
          maxAppMessagesPerWindow: 2000,
          maxPendingRequestsPerApp: 1000,
        ),
      );
      await connectAndRegisterAgent();
      appCh = IOWebSocketChannel.connect('ws://127.0.0.1:$port/app');
      appIter = StreamIterator<String>(appCh!.stream.cast<String>());
      appCh!.sink.add(
        RelayFrame(type: RelayFrameType.subscribe, serverId: fp).encode(),
      );
      expect((await _nextFrame(appIter!)).online, isTrue);

      for (int index = 0; index < 1000; index++) {
        appCh!.sink.add(_identityRoute(fp, 'queued-$index'));
      }
      final Set<String> routed = <String>{};
      for (int index = 0; index < 1000; index++) {
        final RelayFrame frame = await _nextFrame(agentIter!);
        expect(frame.type, RelayFrameType.route);
        routed.add(frame.requestId!);
      }
      expect(routed, hasLength(1000));

      final Map<String, dynamic> health = await _health(port);
      expect(health['onlineAgents'], 1);
      expect(health['pendingRequests'], 1000);
    });
  });
}
