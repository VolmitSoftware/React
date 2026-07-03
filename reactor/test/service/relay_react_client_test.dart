library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/relay_frame.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/react_exceptions.dart';
import 'package:reactor/service/relay_connection.dart';
import 'package:reactor/service/relay_react_client.dart';

class _FakeRelayConnection implements IRelayConnection {
  String? lastMethod;
  String? lastPath;
  Map<String, String>? lastHeaders;

  final Map<String, RelayResponse> _responses;

  _FakeRelayConnection(this._responses);

  @override
  Future<RelayResponse> request({
    required String method,
    required String path,
    required Map<String, String> headers,
    Object? body,
  }) async {
    lastMethod = method;
    lastPath = path;
    lastHeaders = Map<String, String>.from(headers);
    final RelayResponse? response = _responses[path];
    if (response == null) {
      return RelayResponse(
        status: 404,
        body: <String, dynamic>{
          'error': <String, dynamic>{'message': 'not found'}
        },
      );
    }
    return response;
  }

  @override
  Future<void> close() async {}
}

void main() {
  const ServerCredential cred = ServerCredential(
    id: 'srv-1',
    label: 'Test',
    host: 'localhost',
    port: 9696,
    bearer: 'test-bearer-token',
  );

  group('RelayReactClient.identity()', () {
    test('returns IdentityInfo from 200 and sends GET with Authorization header',
        () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'data': <String, dynamic>{
                'serverName': 'T',
                'version': '2.0',
                'folia': true,
                'serverId': 'aa:bb',
              }
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);
      final IdentityInfo info = await client.identity();

      expect(info.serverName, equals('T'));
      expect(info.version, equals('2.0'));
      expect(info.folia, isTrue);
      expect(info.serverId, equals('aa:bb'));

      expect(conn.lastMethod, equals('GET'));
      expect(conn.lastPath, equals('/api/v1/identity'));
      expect(conn.lastHeaders?['Authorization'],
          equals('Bearer test-bearer-token'));
    });
  });

  group('RelayReactClient.metrics()', () {
    test('returns ServerSnapshot from 200 with data list', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/metrics': RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'data': <Map<String, dynamic>>[
                <String, dynamic>{
                  'id': 'cpu',
                  'name': 'CPU',
                  'value': 1.0,
                  'suffix': '%',
                  'min': 0.0,
                  'max': 100.0,
                  'display': '1',
                  'history': <double>[],
                },
              ],
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);
      final ServerSnapshot snapshot = await client.metrics();

      expect(snapshot.byId.containsKey('cpu'), isTrue);
      expect(snapshot.byId['cpu']!.value, equals(1.0));
    });
  });

  group('RelayReactClient error handling', () {
    test('401 throws ReactAuthException', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 401,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'Unauthorized'}
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(
        client.identity(),
        throwsA(isA<ReactAuthException>()),
      );
    });

    test('503 throws ReactUnavailable', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 503,
            body: <String, dynamic>{
              'error': <String, dynamic>{'message': 'Service unavailable'}
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(
        client.identity(),
        throwsA(isA<ReactUnavailable>()),
      );
    });

    test('200 with missing data field throws ReactUnavailable', () async {
      final _FakeRelayConnection conn = _FakeRelayConnection(
        <String, RelayResponse>{
          '/api/v1/identity': RelayResponse(
            status: 200,
            body: <String, dynamic>{
              'other': 'stuff',
            },
          ),
        },
      );

      final RelayReactClient client = RelayReactClient(conn, cred);

      await expectLater(
        client.identity(),
        throwsA(isA<ReactUnavailable>()),
      );
    });
  });
}
