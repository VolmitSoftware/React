library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/log_line.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_revisions.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/direct_web_socket_handshake.dart';
import 'package:react_web/service/monotonic_counter.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/react_status.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

const ServerCredential _cred = ServerCredential(
  id: 'srv',
  label: 'Srv',
  host: 'localhost',
  port: 9696,
  bearer: 'tid.sig',
);

http.Response _json(Object body, int status, {Map<String, String>? headers}) =>
    http.Response(
      jsonEncode(body),
      status,
      headers: <String, String>{
        'content-type': 'application/json',
        ...?headers,
      },
    );

final Map<String, dynamic> _item = <String, dynamic>{
  'data': <String, dynamic>{
    'id': 'snow',
    'name': 'Snow',
    'category': 'World',
    'enabled': true,
  },
};

void main() {
  group('reactErrorFor (M4)', () {
    test('maps each status to a typed error', () {
      expect(reactErrorFor(200, null), isNull);
      expect(reactErrorFor(400, null), isA<ReactBadRequest>());
      expect(reactErrorFor(401, null), isA<ReactAuthException>());
      expect(reactErrorFor(401, null), isNot(isA<ReactRevoked>()));
      expect(reactErrorFor(403, null), isA<ReactForbidden>());
      expect(reactErrorFor(404, null), isA<ReactNotFound>());
      expect(reactErrorFor(409, null), isA<ReactConflict>());
      expect(reactErrorFor(409, null), isNot(isA<ReactCounterConflict>()));
      expect(reactErrorFor(429, null), isA<ReactRateLimited>());
      expect(
        reactErrorFor(503, null),
        isA<ReactUnavailable>().having(
          (ReactUnavailable e) => e.status,
          'status',
          503,
        ),
      );
    });

    test('401 token_revoked is ReactRevoked', () {
      expect(
        reactErrorFor(401, <String, dynamic>{
          'error': 'token_revoked',
          'message': 'Token revoked',
        }),
        isA<ReactRevoked>(),
      );
    });

    test('401 token_revoked and token_expired both end the pairing', () {
      for (final String code in <String>['token_revoked', 'token_expired']) {
        expect(
          reactErrorFor(401, <String, dynamic>{
            'error': <String, dynamic>{'code': code, 'message': code},
          }),
          isA<ReactRevoked>(),
          reason: code,
        );
      }
      expect(
        reactErrorFor(401, <String, dynamic>{
          'error': <String, dynamic>{'code': 'unauthorized'},
        }),
        isNot(isA<ReactRevoked>()),
      );
    });

    test('409 counter_conflict carries lastCounter (both shapes)', () {
      final Exception? flat = reactErrorFor(409, <String, dynamic>{
        'error': 'counter_conflict',
        'lastCounter': 42,
        'message': 'Replayed or non-increasing X-React-Counter',
      });
      final Exception? nested = reactErrorFor(409, <String, dynamic>{
        'error': <String, dynamic>{
          'code': 'counter_conflict',
          'lastCounter': 7,
        },
      });
      expect((flat! as ReactCounterConflict).lastCounter, 42);
      expect((nested! as ReactCounterConflict).lastCounter, 7);
    });

    test('every kind of error maps to one message key', () {
      expect(reactErrorKind(const ReactRevoked()), ReactErrorKind.revoked);
      expect(
        reactErrorKind(const ReactWrongServer()),
        ReactErrorKind.wrongServer,
      );
      expect(reactErrorKind(const ReactForbidden()), ReactErrorKind.forbidden);
      expect(
        reactErrorKind(const ReactCounterConflict(1)),
        ReactErrorKind.counterConflict,
      );
      expect(
        reactErrorKind(const ReactRateLimited()),
        ReactErrorKind.rateLimited,
      );
      expect(reactErrorKind(StateError('x')), ReactErrorKind.unexpected);
    });

    test('Retry-After parses seconds and the wait is capped at 5 s', () {
      expect(parseRetryAfter('3'), const Duration(seconds: 3));
      expect(parseRetryAfter('Wed, 21 Oct 2026 07:28:00 GMT'), isNull);
      expect(
        rateLimitDelay(const Duration(seconds: 30)),
        const Duration(seconds: 5),
      );
      expect(rateLimitDelay(null), const Duration(seconds: 1));
    });
  });

  group('ReactClient counter conflict (H5)', () {
    test('a 409 counter_conflict re-bases and retries once', () async {
      final List<String> counters = <String>[];
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final ReactClient client = ReactClient(
        _cred,
        counter: MonotonicCounter(storage, clock: () => 1000),
        client: MockClient((http.Request request) async {
          counters.add(request.headers['X-React-Counter']!);
          if (counters.length == 1) {
            return _json(<String, dynamic>{
              'error': 'counter_conflict',
              'lastCounter': 5000,
            }, 409);
          }
          return _json(_item, 200);
        }),
      );
      final ControlItem item = await client.setTweakEnabled('snow', true);
      expect(item.enabled, isTrue);
      expect(counters, <String>['1000', '5001']);
    });

    test('a second conflict is reported, not retried again', () async {
      int calls = 0;
      final ReactClient client = ReactClient(
        _cred,
        client: MockClient((http.Request request) async {
          calls++;
          return _json(<String, dynamic>{
            'error': 'counter_conflict',
            'lastCounter': 1 << 50,
          }, 409);
        }),
      );
      await expectLater(
        client.setTweakEnabled('snow', true),
        throwsA(isA<ReactCounterConflict>()),
      );
      expect(calls, 2);
    });

    test('counter = max(stored + 1, now): a later clock wins', () {
      int now = 100;
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final MonotonicCounter counter = MonotonicCounter(
        storage,
        clock: () => now,
      );
      expect(counter.next('a'), 100);
      now = 5000;
      expect(counter.next('a'), 5000);
      counter.rebase('a', 9000);
      expect(counter.next('a'), 9001);
    });

    test('two tabs sharing storage never send the same counter', () {
      final InMemoryFleetStorage shared = InMemoryFleetStorage();
      final MonotonicCounter tabA = MonotonicCounter(shared, clock: () => 50);
      final MonotonicCounter tabB = MonotonicCounter(shared, clock: () => 50);
      final List<int> sent = <int>[
        tabA.next('a'),
        tabB.next('a'),
        tabA.next('a'),
        tabB.next('a'),
      ];
      expect(sent.toSet(), hasLength(4));
      expect(sent, orderedEquals(<int>[...sent]..sort()));
    });
  });

  group('ReactClient 429 (M4)', () {
    test(
      'a GET waits Retry-After (capped) and retries at most twice',
      () async {
        final List<Duration> waits = <Duration>[];
        int calls = 0;
        final ReactClient client = ReactClient(
          _cred,
          sleep: (Duration d) async => waits.add(d),
          client: MockClient((http.Request request) async {
            calls++;
            return _json(
              <String, dynamic>{
                'error': <String, dynamic>{'message': 'Too many requests'},
              },
              429,
              headers: <String, String>{'retry-after': '30'},
            );
          }),
        );
        await expectLater(client.tweaks(), throwsA(isA<ReactRateLimited>()));
        expect(calls, 3);
        expect(waits, <Duration>[
          const Duration(seconds: 5),
          const Duration(seconds: 5),
        ]);
      },
    );

    test('a GET succeeds after one 429', () async {
      int calls = 0;
      final ReactClient client = ReactClient(
        _cred,
        sleep: (Duration d) async {},
        client: MockClient((http.Request request) async {
          calls++;
          if (calls == 1) {
            return _json(<String, dynamic>{}, 429);
          }
          return _json(<String, dynamic>{'data': <Object?>[]}, 200);
        }),
      );
      expect(await client.tweaks(), isEmpty);
      expect(calls, 2);
    });

    test('a mutation answered 429 is not retried', () async {
      int calls = 0;
      final ReactClient client = ReactClient(
        _cred,
        sleep: (Duration d) async {},
        client: MockClient((http.Request request) async {
          calls++;
          return _json(<String, dynamic>{}, 429);
        }),
      );
      await expectLater(
        client.setTweakEnabled('snow', true),
        throwsA(isA<ReactRateLimited>()),
      );
      expect(calls, 1);
    });

    test('403 on a GET is ReactForbidden, not unavailable', () async {
      final ReactClient client = ReactClient(
        _cred,
        client: MockClient(
          (http.Request request) async => _json(<String, dynamic>{
            'error': <String, dynamic>{'message': 'Missing scope console:read'},
          }, 403),
        ),
      );
      await expectLater(client.logs(), throwsA(isA<ReactForbidden>()));
    });
  });

  group('ReactClient new routes', () {
    test(
      'actionTicket reads the bare object (plugin) and a data envelope',
      () async {
        final ReactClient bare = ReactClient(
          _cred,
          client: MockClient((http.Request request) async {
            expect(request.url.path, '/api/v1/actions/tickets/t-1');
            return _json(<String, dynamic>{
              'id': 't-1',
              'state': 'done',
              'message': 'Collected garbage',
            }, 200);
          }),
        );
        final ActionTicketStatus status = await bare.actionTicket('t-1');
        expect(status.state, ActionTicketState.done);
        expect(status.message, 'Collected garbage');

        final ReactClient wrapped = ReactClient(
          _cred,
          client: MockClient(
            (http.Request request) async => _json(<String, dynamic>{
              'data': <String, dynamic>{'id': 't-1', 'state': 'running'},
            }, 200),
          ),
        );
        expect(
          (await wrapped.actionTicket('t-1')).state,
          ActionTicketState.running,
        );
      },
    );

    test('logLines numbers plain strings from lastSeq', () async {
      final ReactClient client = ReactClient(
        _cred,
        client: MockClient((http.Request request) async {
          expect(request.url.queryParameters['after'], '7');
          return _json(<String, dynamic>{
            'data': <String>['a', 'b', 'c'],
            'lastSeq': 10,
          }, 200);
        }),
      );
      final List<LogLine> lines = await client.logLines(limit: 50, after: 7);
      expect(lines.map((LogLine l) => l.seq), <int>[8, 9, 10]);
    });

    test('metrics polls the compact body and reads revisions', () async {
      final ReactClient client = ReactClient(
        _cred,
        client: MockClient((http.Request request) async {
          expect(request.url.queryParameters['compact'], '1');
          return _json(<String, dynamic>{
            'data': <String, dynamic>{
              'sequence': 4,
              'capturedAtMs': 1000,
              'samplers': <Object?>[],
              'revisions': <String, int>{'tweaks': 3, 'features': 9},
            },
          }, 200);
        }),
      );
      final ServerSnapshot snapshot = await client.metrics();
      expect(snapshot.revisions![RevisionKind.tweaks], 3);
      expect(snapshot.revisions![RevisionKind.config], isNull);
    });
  });

  group('DirectWebSocketHandshake', () {
    test('logs socket asks for batches; the token stays out of the URL', () {
      final DirectWebSocketHandshake logs = DirectWebSocketHandshake(
        _cred,
        'ws/logs',
        query: const <String, String>{'batch': '1'},
      );
      expect(logs.endpoint.toString(), 'ws://localhost:9696/ws/logs?batch=1');
      expect(logs.endpoint.toString(), isNot(contains('tid.sig')));
      expect(
        () => DirectWebSocketHandshake(
          _cred,
          'ws/logs',
          query: const <String, String>{'token': 'tid.sig'},
        ),
        throwsArgumentError,
      );
    });
  });
}
