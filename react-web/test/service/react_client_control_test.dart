import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/monotonic_counter.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

void main() {
  const ServerCredential cred = ServerCredential(
    id: 'srv1',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'tok-abc',
  );

  group('ReactClient.features()', () {
    test('GETs /api/v1/features and decodes ControlItems with knobs', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'mob-stacking',
            'name': 'Mob Stacking',
            'category': 'performance',
            'enabled': true,
            'description': 'Stack mobs',
            'knobs': <Map<String, dynamic>>[
              <String, dynamic>{
                'key': 'maxStack',
                'label': 'Max Stack',
                'type': 'int',
                'value': 32,
              },
            ],
          },
          <String, dynamic>{
            'id': 'chunk-scan',
            'name': 'Chunk Scan',
            'category': 'performance',
            'enabled': false,
            'description': 'Scan chunks',
            'knobs': <Map<String, dynamic>>[
              <String, dynamic>{
                'key': 'interval',
                'label': 'Interval',
                'type': 'int',
                'value': 20,
              },
            ],
          },
        ],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/features'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<ControlItem> items = await client.features();

      expect(items.length, equals(2));
      expect(items[0].id, equals('mob-stacking'));
      expect(items[0].enabled, isTrue);
      expect(items[0].knobs.length, equals(1));
      expect(items[0].knobs[0].key, equals('maxStack'));
      expect(items[0].knobs[0].intValue, equals(32));
      expect(items[1].id, equals('chunk-scan'));
      expect(items[1].enabled, isFalse);
    });
  });

  group('ReactClient.feature()', () {
    test('404 throws ReactNotFound with server message', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'error': <String, dynamic>{'message': 'unknown feature: nope'},
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.url.path, equals('/api/v1/features/nope'));
        return http.Response(
          jsonEncode(body),
          404,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.feature('nope'),
        throwsA(
          isA<ReactNotFound>().having(
            (ReactNotFound e) => e.message,
            'message',
            contains('unknown feature: nope'),
          ),
        ),
      );
    });

    test('200 returns decoded ControlItem', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'mob-stacking',
          'name': 'Mob Stacking',
          'category': 'performance',
          'enabled': true,
          'description': 'Stack mobs',
          'knobs': <dynamic>[],
        },
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ControlItem item = await client.feature('mob-stacking');

      expect(item.id, equals('mob-stacking'));
      expect(item.enabled, isTrue);
    });
  });

  group('ReactClient.setFeatureEnabled()', () {
    test('PUTs with X-React-Counter header and returns ControlItem', () async {
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'mob-stacking',
          'name': 'Mob Stacking',
          'category': 'performance',
          'enabled': false,
          'description': 'Stack mobs',
          'knobs': <dynamic>[],
        },
      };

      http.Request? captured;
      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(
          jsonEncode(responseBody),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final MonotonicCounter counter = MonotonicCounter(
        InMemoryFleetStorage(),
        clock: () => 7000,
      );
      final ReactClient client = ReactClient(
        cred,
        client: mock,
        counter: counter,
      );
      final ControlItem item = await client.setFeatureEnabled(
        'mob-stacking',
        false,
      );

      expect(captured, isNotNull);
      expect(captured!.method, equals('PUT'));
      expect(captured!.url.path, equals('/api/v1/features/mob-stacking'));
      expect(captured!.headers['authorization'], equals('Bearer tok-abc'));
      expect(captured!.headers['x-react-counter'], equals('7000'));
      final Map<String, dynamic> sentBody =
          jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(sentBody, equals(<String, Object>{'enabled': false}));
      expect(item.enabled, isFalse);
    });
  });

  group('ReactClient monotonic counter across mutations', () {
    test('second mutation header > first mutation header', () async {
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'mob-stacking',
          'name': 'Mob Stacking',
          'category': 'performance',
          'enabled': true,
          'description': '',
          'knobs': <dynamic>[],
        },
      };

      final List<String> capturedCounters = <String>[];
      final MockClient mock = MockClient((http.Request req) async {
        final String? header = req.headers['x-react-counter'];
        if (header != null) {
          capturedCounters.add(header);
        }
        return http.Response(
          jsonEncode(responseBody),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      await client.setFeatureEnabled('mob-stacking', true);
      await client.setFeatureEnabled('mob-stacking', false);

      expect(capturedCounters.length, equals(2));
      expect(
        int.parse(capturedCounters[1]),
        greaterThan(int.parse(capturedCounters[0])),
      );
    });
  });

  group('ReactClient.setFeatureConfig()', () {
    test('PUTs to /features/{id}/config with knob body', () async {
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'x',
          'name': 'X',
          'category': 'performance',
          'enabled': true,
          'description': '',
          'knobs': <Map<String, dynamic>>[
            <String, dynamic>{
              'key': 'maxStack',
              'label': 'Max Stack',
              'type': 'int',
              'value': 64,
            },
          ],
        },
      };

      http.Request? captured;
      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(
          jsonEncode(responseBody),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final ControlItem item = await client.setFeatureConfig(
        'x',
        <String, Object?>{'maxStack': 64},
      );

      expect(captured!.method, equals('PUT'));
      expect(captured!.url.path, equals('/api/v1/features/x/config'));
      final Map<String, dynamic> sentBody =
          jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(sentBody, equals(<String, Object>{'maxStack': 64}));
      expect(captured!.headers['x-react-counter'], isNotNull);
      expect(item.knobs[0].intValue, equals(64));
    });
  });

  group('ReactClient error envelopes', () {
    test('403 throws ReactForbidden with server message', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'error': <String, dynamic>{'message': 'missing op:execute'},
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
          jsonEncode(body),
          403,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.setFeatureEnabled('mob-stacking', true),
        throwsA(
          isA<ReactForbidden>().having(
            (ReactForbidden e) => e.message,
            'message',
            contains('missing op:execute'),
          ),
        ),
      );
    });

    test('409 throws ReactConflict with server message', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'error': <String, dynamic>{'message': 'counter replay'},
      };

      final MockClient mock = MockClient((http.Request req) async {
        return http.Response(
          jsonEncode(body),
          409,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);

      await expectLater(
        client.setFeatureEnabled('mob-stacking', true),
        throwsA(
          isA<ReactConflict>().having(
            (ReactConflict e) => e.message,
            'message',
            contains('counter replay'),
          ),
        ),
      );
    });
  });

  group('ReactClient.tweaks()', () {
    test('GETs /api/v1/tweaks and decodes ControlItems', () async {
      final Map<String, dynamic> body = <String, dynamic>{
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 'tweak-a',
            'name': 'Tweak A',
            'category': 'runtime',
            'enabled': true,
            'description': '',
            'knobs': <dynamic>[],
          },
          <String, dynamic>{
            'id': 'tweak-b',
            'name': 'Tweak B',
            'category': 'runtime',
            'enabled': false,
            'description': '',
            'knobs': <dynamic>[],
          },
        ],
      };

      final MockClient mock = MockClient((http.Request req) async {
        expect(req.method, equals('GET'));
        expect(req.url.path, equals('/api/v1/tweaks'));
        expect(req.headers['authorization'], equals('Bearer tok-abc'));
        return http.Response(
          jsonEncode(body),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final List<ControlItem> items = await client.tweaks();

      expect(items.length, equals(2));
      expect(items[0].id, equals('tweak-a'));
      expect(items[1].id, equals('tweak-b'));
    });
  });

  group('ReactClient.worlds()', () {
    test(
      'GETs /api/v1/worlds and decodes WorldSettings with pressureMode',
      () async {
        final Map<String, dynamic> body = <String, dynamic>{
          'data': <Map<String, dynamic>>[
            <String, dynamic>{
              'name': 'world',
              'pressureMode': 'NORMAL',
              'budgetMs': 40.0,
              'panicMs': 60.0,
              'releaseMs': 30.0,
            },
            <String, dynamic>{
              'name': 'world_nether',
              'pressureMode': 'PRESSURE',
              'budgetMs': 35.0,
              'panicMs': 55.0,
              'releaseMs': 25.0,
            },
          ],
        };

        final MockClient mock = MockClient((http.Request req) async {
          expect(req.method, equals('GET'));
          expect(req.url.path, equals('/api/v1/worlds'));
          expect(req.headers['authorization'], equals('Bearer tok-abc'));
          return http.Response(
            jsonEncode(body),
            200,
            headers: <String, String>{'content-type': 'application/json'},
          );
        });

        final ReactClient client = ReactClient(cred, client: mock);
        final List<WorldSettings> settings = await client.worlds();

        expect(settings.length, equals(2));
        expect(settings[0].name, equals('world'));
        expect(settings[0].pressureMode, equals(PressureMode.normal));
        expect(settings[1].name, equals('world_nether'));
        expect(settings[1].pressureMode, equals(PressureMode.pressure));
      },
    );
  });

  group('ReactClient.setWorld()', () {
    test('PUTs only non-null fields and decodes WorldSettings', () async {
      final Map<String, dynamic> responseBody = <String, dynamic>{
        'data': <String, dynamic>{
          'name': 'world',
          'pressureMode': 'NORMAL',
          'budgetMs': 45.0,
          'panicMs': 60.0,
          'releaseMs': 30.0,
        },
      };

      http.Request? captured;
      final MockClient mock = MockClient((http.Request req) async {
        captured = req;
        return http.Response(
          jsonEncode(responseBody),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final ReactClient client = ReactClient(cred, client: mock);
      final WorldSettings ws = await client.setWorld('world', budgetMs: 45.0);

      expect(captured!.method, equals('PUT'));
      expect(captured!.url.path, equals('/api/v1/worlds/world'));
      final Map<String, dynamic> sentBody =
          jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(sentBody, equals(<String, Object>{'budgetMs': 45.0}));
      expect(sentBody.containsKey('panicMs'), isFalse);
      expect(sentBody.containsKey('releaseMs'), isFalse);
      expect(captured!.headers['x-react-counter'], isNotNull);
      expect(ws.budgetMs, equals(45.0));
      expect(ws.name, equals('world'));
    });
  });
}
