import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/model/relay_frame.dart';
import 'package:react_web/service/pairing_redeemer.dart';
import 'package:react_web/service/relay_connection_interface.dart';
import 'package:react_web/service/server_proof.dart';
import 'package:test/test.dart';

import '../support/test_server_key.dart';

/// A relay that hands each request to [answer]; records what went out.
class _Relay implements IRelayConnection {
  _Relay(this.answer);

  final Future<RelayResponse> Function(
    String method,
    String path,
    Map<String, String> headers,
    Object? body,
  )
  answer;
  final List<(String, String, Map<String, String>, Object?)> sent =
      <(String, String, Map<String, String>, Object?)>[];
  bool closed = false;

  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) {
    sent.add((method, path, Map<String, String>.of(headers), body));
    return answer(method, path, headers, body);
  }

  @override
  Future<void> close() async => closed = true;
}

http.Response _json(
  int status,
  Object body, {
  Map<String, String> headers = const <String, String>{},
}) => http.Response(
  jsonEncode(body),
  status,
  headers: <String, String>{'content-type': 'application/json', ...headers},
);

Map<String, Object?> _error(
  String code, [
  Map<String, Object?> extras = const <String, Object?>{},
]) => <String, Object?>{
  'error': <String, Object?>{'code': code, 'message': code, ...extras},
};

void main() {
  late TestServerKey key;
  final DateTime now = DateTime.utc(2026, 11, 13, 10);

  setUpAll(() async => key = await TestServerKey.create());

  PairingLink link({
    String? directUrl = 'http://127.0.0.1:9822',
    String? relayUrl,
    DateTime? expiresAt,
  }) => key.link(
    directUrl: directUrl,
    relayUrl: relayUrl,
    expiresAt: expiresAt ?? now.add(const Duration(minutes: 10)),
  );

  PairingRedeemer redeemer({
    http.Client? client,
    _Relay? relay,
    String pageOrigin = 'http://127.0.0.1:8823',
  }) => PairingRedeemer(
    httpClient: () =>
        client ??
        MockClient(
          (http.Request request) async => throw http.ClientException('refused'),
        ),
    openRelay: relay == null ? null : (String url, String fingerprint) => relay,
    pageOrigin: pageOrigin,
    now: () => now,
  );

  _Relay provingRelay(
    FutureOr<RelayResponse> Function(
      String path,
      Map<String, String> headers,
      Object? body,
    )
    pair,
  ) => _Relay((
    String method,
    String path,
    Map<String, String> headers,
    Object? body,
  ) async {
    final String? challenge = headerValue(headers, challengeHeader);
    final Map<String, String> proof = challenge == null
        ? const <String, String>{}
        : await key.proof(challenge);
    if (path == '/api/v1/ping') {
      return RelayResponse(
        status: 200,
        body: <String, dynamic>{
          'data': <String, dynamic>{'protocolVersion': 3},
        },
        headers: proof,
      );
    }
    final RelayResponse answer = await pair(path, headers, body);
    return RelayResponse(
      status: answer.status,
      body: answer.body,
      headers: proof,
    );
  });

  test('redeems on the direct path and sends the browser name', () async {
    late Map<String, dynamic> sent;
    final MockClient client = MockClient((http.Request request) async {
      expect(request.method, 'POST');
      expect(request.url.toString(), 'http://127.0.0.1:9822/api/v1/pair');
      expect(
        request.headers.keys.map((String k) => k.toLowerCase()),
        isNot(contains('authorization')),
      );
      sent = jsonDecode(request.body) as Map<String, dynamic>;
      return _json(200, <String, Object?>{'data': key.pairAnswer()});
    });
    final PairedToken token = await redeemer(client: client)
        .redeem(link(), client: 'Chrome on macOS');
    expect(sent, <String, Object?>{
      'tokenId': 'tok_AbCdEfGhIjKlMnOp',
      'secret': 'S' * 43,
      'client': 'Chrome on macOS',
    });
    expect(token.bearer, TestServerKey.bearer);
    expect(token.role, 'operator');
    expect(token.scopes, <String>['read', 'op:execute']);
    expect(token.expiresAt, DateTime.utc(2026, 11, 20, 10));
    expect(token.lifetime, const Duration(days: 7));
    expect(token.serverName, 'Lobby');
    expect(token.path, PairPath.direct);
  });

  test('a base path in the direct address is kept', () async {
    final MockClient client = MockClient((http.Request request) async {
      expect(request.url.toString(), 'http://127.0.0.1:8824/s1/api/v1/pair');
      return _json(200, <String, Object?>{'data': key.pairAnswer()});
    });
    await redeemer(client: client)
        .redeem(link(directUrl: 'http://127.0.0.1:8824/s1'));
  });

  test('each refusal code is its own failure', () async {
    final Map<String, PairingFailure> cases = <String, PairingFailure>{
      'pairing_used': PairingFailure.used,
      'pairing_expired': PairingFailure.expired,
      'pairing_revoked': PairingFailure.revoked,
      'pairing_unknown': PairingFailure.unknown,
      'rate_limited': PairingFailure.busy,
      'internal': PairingFailure.other,
    };
    for (final MapEntry<String, PairingFailure> entry in cases.entries) {
      final MockClient client = MockClient(
        (http.Request request) async => _json(410, _error(entry.key)),
      );
      await expectLater(
        redeemer(client: client).redeem(link()),
        throwsA(
          isA<PairingRefused>().having(
            (PairingRefused e) => e.failure,
            'failure',
            entry.value,
          ),
        ),
        reason: entry.key,
      );
    }
  });

  test(
    'a spent link needs a new one; an origin or a silent server does not',
    () {
      expect(const PairingRefused(PairingFailure.used).needsNewLink, isTrue);
      expect(const PairingRefused(PairingFailure.expired).needsNewLink, isTrue);
      expect(
        const PairingRefused(PairingFailure.originNotAllowed).needsNewLink,
        isFalse,
      );
      expect(
        const PairingRefused(PairingFailure.unreachable).needsNewLink,
        isFalse,
      );
    },
  );

  test('origin_not_allowed carries the origin and the config key', () async {
    final MockClient client = MockClient(
      (http.Request request) async => _json(
        403,
        _error('origin_not_allowed', <String, Object?>{
          'origin': 'http://127.0.0.1:8823',
          'configKey': 'allowedOrigins',
          'configFile': 'plugins/React/web.toml',
        }),
      ),
    );
    await expectLater(
      redeemer(client: client).redeem(link()),
      throwsA(
        isA<PairingRefused>()
            .having(
              (PairingRefused e) => e.failure,
              'failure',
              PairingFailure.originNotAllowed,
            )
            .having(
              (PairingRefused e) => e.origin,
              'origin',
              'http://127.0.0.1:8823',
            )
            .having((PairingRefused e) => e.configKey, 'key', 'allowedOrigins')
            .having(
              (PairingRefused e) => e.configFile,
              'file',
              'plugins/React/web.toml',
            ),
      ),
    );
  });

  test('a link past its time is refused before anything is sent', () async {
    int calls = 0;
    final MockClient client = MockClient((http.Request request) async {
      calls++;
      return _json(200, <String, Object?>{'data': key.pairAnswer()});
    });
    await expectLater(
      redeemer(client: client)
          .redeem(link(expiresAt: now.subtract(const Duration(seconds: 1)))),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.expired,
        ),
      ),
    );
    expect(calls, 0);
  });

  test('an answer from another server is a wrong server', () async {
    final MockClient client = MockClient(
      (http.Request request) async => _json(200, <String, Object?>{
        'data': <String, Object?>{...key.pairAnswer(), 'fingerprint': 'f' * 64},
      }),
    );
    await expectLater(
      redeemer(client: client).redeem(link()),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.wrongServer,
        ),
      ),
    );
  });

  test('no answer on the only path: the server did not answer', () async {
    await expectLater(
      redeemer().redeem(link()),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.unreachable,
        ),
      ),
    );
  });

  test(
    'no answer from an https page to a local address: the browser blocked it',
    () async {
      await expectLater(
        redeemer(pageOrigin: 'https://react.volmitsoftware.com').redeem(link()),
        throwsA(
          isA<PairingRefused>().having(
            (PairingRefused e) => e.failure,
            'failure',
            PairingFailure.localNetworkBlocked,
          ),
        ),
      );
    },
  );

  test(
    'the relay is used when the direct path does not answer, after the proof',
    () async {
      final _Relay relay = provingRelay((
        String path,
        Map<String, String> headers,
        Object? body,
      ) {
        expect(path, '/api/v1/pair');
        expect(jsonDecode(body! as String), containsPair('secret', 'S' * 43));
        return RelayResponse(
          status: 200,
          body: <String, dynamic>{'data': key.pairAnswer()},
        );
      });
      final PairedToken token = await redeemer(relay: relay)
          .redeem(link(relayUrl: 'ws://127.0.0.1:9821'));
      expect(token.path, PairPath.relay);
      expect(relay.sent.first.$2, '/api/v1/ping');
      expect(relay.sent.first.$3.keys, isNot(contains('Authorization')));
      expect(relay.closed, isTrue);
    },
  );

  test('a relay without the server proof never sees the secret', () async {
    final _Relay relay = _Relay(
      (
        String method,
        String path,
        Map<String, String> headers,
        Object? body,
      ) async => RelayResponse(
        status: 200,
        body: <String, dynamic>{'data': <String, dynamic>{}},
        headers: <String, String>{
          serverKeyHeader: key.serverKey,
          proofHeader: 'A' * 86,
        },
      ),
    );
    await expectLater(
      redeemer(relay: relay)
          .redeem(link(directUrl: null, relayUrl: 'ws://127.0.0.1:9821')),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.wrongServer,
        ),
      ),
    );
    expect(
      relay.sent.map(
        (((String, String, Map<String, String>, Object?) each) => each.$2),
      ),
      <String>['/api/v1/ping'],
    );
  });

  test('a relay whose server is offline: the server did not answer', () async {
    final _Relay relay = _Relay(
      (
        String method,
        String path,
        Map<String, String> headers,
        Object? body,
      ) async => RelayResponse(
        status: 503,
        body: _error('server_offline').cast<String, dynamic>(),
      ),
    );
    await expectLater(
      redeemer(relay: relay)
          .redeem(link(directUrl: null, relayUrl: 'ws://127.0.0.1:9821')),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.unreachable,
        ),
      ),
    );
  });

  test(
    'a refused origin on the direct path still pairs through the relay',
    () async {
      final MockClient client = MockClient(
        (http.Request request) async =>
            _json(403, _error('origin_not_allowed')),
      );
      final _Relay relay = provingRelay(
        (String path, Map<String, String> headers, Object? body) =>
            RelayResponse(
              status: 200,
              body: <String, dynamic>{'data': key.pairAnswer()},
            ),
      );
      final PairedToken token = await redeemer(
        client: client,
        relay: relay,
      ).redeem(link(relayUrl: 'ws://127.0.0.1:9821'));
      expect(token.path, PairPath.relay);
    },
  );

  test('when both paths fail the server refusal wins over silence', () async {
    final MockClient client = MockClient(
      (http.Request request) async => _json(410, _error('pairing_used')),
    );
    final _Relay relay = _Relay(
      (
        String method,
        String path,
        Map<String, String> headers,
        Object? body,
      ) async => RelayResponse(
        status: 503,
        body: _error('server_offline').cast<String, dynamic>(),
      ),
    );
    await expectLater(
      redeemer(
        client: client,
        relay: relay,
      ).redeem(link(relayUrl: 'ws://127.0.0.1:9821')),
      throwsA(
        isA<PairingRefused>().having(
          (PairingRefused e) => e.failure,
          'failure',
          PairingFailure.used,
        ),
      ),
    );
  });
}
