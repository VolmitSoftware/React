library;

import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/happy_eyeballs_client.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _FakeClient implements IReactClient {
  final String serverId;
  int identityCallCount = 0;

  _FakeClient({this.serverId = 'any'});

  @override
  Future<IdentityInfo> identity() async {
    identityCallCount++;
    return IdentityInfo(
      serverName: 'Fake',
      version: '1.0',
      folia: false,
      serverId: serverId,
    );
  }

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

ServerCredential _cred({
  required String id,
  String host = 'localhost',
  int port = 7979,
  String? relayUrl,
  String? fingerprint,
}) => ServerCredential(
  id: id,
  label: host.isNotEmpty ? host : (fingerprint ?? relayUrl ?? 'server'),
  host: host,
  port: port,
  bearer: 'tid.tsig',
  relayUrl: relayUrl,
  fingerprint: fingerprint,
);

void main() {
  group('FleetManager relay transport', () {
    test(
      '(1) credential with relayUrl + fingerprint + relayClientFactory '
      'builds a manager and resolved client is HappyEyeballsClient',
      () async {
        final _FakeClient directClient = _FakeClient(serverId: 'fp:01');
        final _FakeClient relayClient = _FakeClient(serverId: 'fp:01');
        final FleetManager fm = FleetManager(
          storage: InMemoryFleetStorage(),
          clientFactory: (_) => directClient,
          relayClientFactory: (_) => relayClient,
        );
        final ServerCredential cred = _cred(
          id: 'srv-1',
          host: '127.0.0.1',
          port: 7979,
          relayUrl: 'wss://relay.test',
          fingerprint: 'fp:01',
        );
        await fm.add(cred);
        expect(fm.managerFor('srv-1'), isNotNull);
        expect(fm.resolvedClientFor('srv-1'), isA<HappyEyeballsClient>());
      },
    );

    test('(2) relay-only credential (empty host) adds successfully when '
        'relayClientFactory identity returns pinned fingerprint', () async {
      final _FakeClient relayClient = _FakeClient(serverId: 'fp:02');
      final FleetManager fm = FleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FakeClient(serverId: 'fp:02'),
        relayClientFactory: (_) => relayClient,
      );
      final ServerCredential cred = _cred(
        id: 'srv-2',
        host: '',
        port: 0,
        relayUrl: 'wss://relay.test',
        fingerprint: 'fp:02',
      );
      await fm.add(cred);
      expect(fm.managerFor('srv-2'), isNotNull);
      expect(fm.servers.length, equals(1));
    });

    test('(3) direct-only credential (relayUrl null) behaves as before; '
        'relayClientFactory is never called', () async {
      int relayFactoryCalls = 0;
      final FleetManager fm = FleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FakeClient(serverId: 'direct:03'),
        relayClientFactory: (_) {
          relayFactoryCalls++;
          return _FakeClient(serverId: 'direct:03');
        },
      );
      final ServerCredential cred = _cred(
        id: 'srv-3',
        host: '127.0.0.1',
        port: 7979,
      );
      await fm.add(cred);
      expect(fm.managerFor('srv-3'), isNotNull);
      expect(
        relayFactoryCalls,
        equals(0),
        reason: 'relayClientFactory must not be called for a direct credential',
      );
    });

    test('(4) relayClientFactory null + relayUrl set + usable host uses direct '
        'leg alone and add() succeeds', () async {
      final FleetManager fm = FleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FakeClient(serverId: 'direct:04'),
      );
      final ServerCredential cred = _cred(
        id: 'srv-4',
        host: '127.0.0.1',
        port: 7979,
        relayUrl: 'wss://relay.test',
      );
      await fm.add(cred);
      expect(fm.managerFor('srv-4'), isNotNull);
      expect(fm.servers.length, equals(1));
    });
  });
}
