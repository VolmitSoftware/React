library;

import 'dart:async';
import 'dart:convert';

import 'package:test/test.dart';

import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/fleet_export.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/memory_fleet_storage.dart';

class _StubClient implements IReactClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
        serverName: 'Stub',
        version: '1.0.0',
        folia: false,
        serverId: '127.0.0.1:9696',
      );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

Map<String, dynamic> _credJson({
  String id = 'srv-1',
  String label = 'Test',
  String host = 'localhost',
  int port = 7979,
  String bearer = 'tok1',
}) => <String, dynamic>{
      'id': id,
      'label': label,
      'host': host,
      'port': port,
      'bearer': bearer,
      'secure': false,
    };

FleetManager _seeded({
  required InMemoryFleetStorage storage,
  List<Map<String, dynamic>> creds = const <Map<String, dynamic>>[],
}) {
  if (creds.isNotEmpty) {
    storage.write(FleetManager.storageKey, jsonEncode(creds));
  }
  return FleetManager(storage: storage, clientFactory: (_) => _StubClient());
}

void main() {
  group('FleetManager.importReplace', () {
    test('replaces existing servers with incoming creds', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(
        storage: storage,
        creds: <Map<String, dynamic>>[_credJson(id: 'old-1', label: 'Old')],
      );

      final List<ServerCredential> incoming = <ServerCredential>[
        const ServerCredential(
            id: 'new-1', label: 'New1', host: 'h1', port: 8080, bearer: 'b1'),
        const ServerCredential(
            id: 'new-2', label: 'New2', host: 'h2', port: 8081, bearer: 'b2'),
      ];

      fm.importReplace(incoming);

      expect(fm.servers.length, equals(2));
      expect(fm.servers.map((ServerCredential c) => c.id),
          containsAll(<String>['new-1', 'new-2']));
      expect(fm.managerFor('old-1'), isNull);
      expect(fm.managerFor('new-1'), isNotNull);
      expect(fm.managerFor('new-2'), isNotNull);
    });

    test('sets activeServer to the first incoming credential', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(storage: storage);

      fm.importReplace(<ServerCredential>[
        const ServerCredential(
            id: 'a', label: 'A', host: 'h1', port: 8080, bearer: 'ba'),
        const ServerCredential(
            id: 'b', label: 'B', host: 'h2', port: 8081, bearer: 'bb'),
      ]);

      expect(fm.activeServer?.id, equals('a'));
    });

    test('persists so a new FleetManager reads the replaced fleet', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(
        storage: storage,
        creds: <Map<String, dynamic>>[_credJson(id: 'old-1', label: 'Old')],
      );

      fm.importReplace(<ServerCredential>[
        const ServerCredential(
            id: 'new-1', label: 'Alpha', host: 'alpha', port: 7979, bearer: 'bnew'),
      ]);

      final FleetManager fm2 =
          FleetManager(storage: storage, clientFactory: (_) => _StubClient());
      expect(fm2.servers.length, equals(1));
      expect(fm2.servers.first.id, equals('new-1'));
      expect(fm2.servers.first.label, equals('Alpha'));
    });

    test('with empty list clears fleet and sets activeServer null', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(
        storage: storage,
        creds: <Map<String, dynamic>>[_credJson(id: 'old-1')],
      );

      fm.importReplace(<ServerCredential>[]);

      expect(fm.servers, isEmpty);
      expect(fm.activeServer, isNull);

      final FleetManager fm2 =
          FleetManager(storage: storage, clientFactory: (_) => _StubClient());
      expect(fm2.servers, isEmpty);
    });

    test('persists only once per call (not per-credential)', () {
      final _CountingStorage storage = _CountingStorage();
      final FleetManager fm =
          FleetManager(storage: storage, clientFactory: (_) => _StubClient());
      storage.writeCount = 0;

      fm.importReplace(<ServerCredential>[
        const ServerCredential(
            id: 'x', label: 'X', host: 'h', port: 9090, bearer: 'bx'),
        const ServerCredential(
            id: 'y', label: 'Y', host: 'h', port: 9091, bearer: 'by'),
      ]);

      expect(storage.writeCount, equals(1));
    });
  });

  group('FleetManager.remove persists', () {
    test('after remove a fresh FleetManager does not contain the removed id',
        () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(
        storage: storage,
        creds: <Map<String, dynamic>>[
          _credJson(id: 'srv-1', label: 'First'),
          _credJson(id: 'srv-2', label: 'Second', port: 7980, bearer: 'tok2'),
        ],
      );

      fm.remove('srv-1');

      final FleetManager fm2 =
          FleetManager(storage: storage, clientFactory: (_) => _StubClient());
      expect(fm2.servers.length, equals(1));
      expect(fm2.servers.first.id, equals('srv-2'));
    });

    test('remove disposes the manager for the removed id', () async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seeded(
        storage: storage,
        creds: <Map<String, dynamic>>[_credJson(id: 'srv-1')],
      );

      final ConnectionManager mgr = fm.managerFor('srv-1')!;
      final Completer<void> closed = Completer<void>();
      mgr.snapshots.listen(null, onDone: closed.complete);

      fm.remove('srv-1');

      await closed.future;
      expect(fm.managerFor('srv-1'), isNull);
    });
  });

  group('buildFleetExportJson / parseFleetImportJson round-trip', () {
    test('serialized export round-trips correctly', () {
      final List<ServerCredential> servers = <ServerCredential>[
        const ServerCredential(
            id: 'id-1',
            label: 'Alpha',
            host: 'alpha.example.com',
            port: 7979,
            bearer: 'tok-alpha'),
        const ServerCredential(
            id: 'id-2',
            label: 'Beta',
            host: 'beta.example.com',
            port: 8080,
            bearer: 'tok-beta'),
      ];

      final String json = buildFleetExportJson(servers);
      final FleetParseResult result = parseFleetImportJson(json);

      expect(result.ok, isTrue);
      expect(result.servers.length, equals(2));
      expect(result.servers[0].id, equals('id-1'));
      expect(result.servers[0].host, equals('alpha.example.com'));
      expect(result.servers[0].bearer, equals('tok-alpha'));
      expect(result.servers[1].id, equals('id-2'));
      expect(result.servers[1].port, equals(8080));
    });

    test('built json has correct envelope fields', () {
      final String json = buildFleetExportJson(<ServerCredential>[
        const ServerCredential(
            id: 'x', label: 'X', host: 'h', port: 1234, bearer: 'bx'),
      ]);

      final Map<String, dynamic> envelope =
          jsonDecode(json) as Map<String, dynamic>;
      expect(envelope['kind'], equals('reactor-fleet'));
      expect(envelope['version'], equals(1));
      expect(envelope['exportedAt'], isA<int>());
      expect(envelope['servers'], isA<List<dynamic>>());
    });
  });

  group('parseFleetImportJson validation', () {
    test('rejects invalid JSON', () {
      final FleetParseResult result =
          parseFleetImportJson('not valid json {{{{');
      expect(result.ok, isFalse);
      expect(result.error, isNotNull);
    });

    test('rejects non-object JSON', () {
      final FleetParseResult result = parseFleetImportJson('[1,2,3]');
      expect(result.ok, isFalse);
    });

    test('rejects foreign kind', () {
      final String json = jsonEncode(<String, dynamic>{
        'kind': 'something-else',
        'servers': <dynamic>[],
      });
      final FleetParseResult result = parseFleetImportJson(json);
      expect(result.ok, isFalse);
      expect(result.error, contains('reactor-fleet'));
    });

    test('rejects missing servers list', () {
      final String json = jsonEncode(<String, dynamic>{
        'kind': 'reactor-fleet',
        'version': 1,
      });
      final FleetParseResult result = parseFleetImportJson(json);
      expect(result.ok, isFalse);
    });

    test('imports only valid entries and skips malformed ones', () {
      final String json = jsonEncode(<String, dynamic>{
        'kind': 'reactor-fleet',
        'version': 1,
        'exportedAt': 0,
        'servers': <dynamic>[
          <String, dynamic>{
            'id': 'good-1',
            'label': 'Good',
            'host': 'h1',
            'port': 7979,
            'bearer': 'b1',
            'secure': false,
          },
          <String, dynamic>{'label': 'Bad missing id host port bearer'},
          'not-an-object',
        ],
      });
      final FleetParseResult result = parseFleetImportJson(json);
      expect(result.ok, isTrue);
      expect(result.servers.length, equals(1));
      expect(result.servers.first.id, equals('good-1'));
      expect(result.skipped, equals(2));
    });

    test('aborts with error when zero valid servers found', () {
      final String json = jsonEncode(<String, dynamic>{
        'kind': 'reactor-fleet',
        'version': 1,
        'exportedAt': 0,
        'servers': <dynamic>[
          <String, dynamic>{'host': 'h1'},
        ],
      });
      final FleetParseResult result = parseFleetImportJson(json);
      expect(result.ok, isFalse);
      expect(result.error, isNotNull);
      expect(result.servers, isEmpty);
    });

    test('aborts with error when servers list is empty', () {
      final String json = jsonEncode(<String, dynamic>{
        'kind': 'reactor-fleet',
        'version': 1,
        'exportedAt': 0,
        'servers': <dynamic>[],
      });
      final FleetParseResult result = parseFleetImportJson(json);
      expect(result.ok, isFalse);
      expect(result.error, isNotNull);
    });
  });
}

class _CountingStorage implements FleetStorage {
  final Map<String, String> _data = <String, String>{};
  int writeCount = 0;

  @override
  String? read(String key) => _data[key];

  @override
  void write(String key, String value) {
    writeCount++;
    _data[key] = value;
  }

  @override
  void remove(String key) => _data.remove(key);
}
