library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/network_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _StubClient implements IReactClient {
  _StubClient(this.cred);

  final ServerCredential cred;

  @override
  Future<void> close() async {}

  @override
  Future<IdentityInfo> identity() async =>
      IdentityInfo(serverName: cred.label, folia: false, serverId: '');

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

const ServerCredential _proxy = ServerCredential(
  id: 'proxy',
  label: 'Proxy',
  host: '10.0.0.5',
  port: 9696,
  bearer: 'tok_a.secret',
  basePath: '/edge/',
  fingerprint: 'proxy-key',
  role: 'operator',
  scopes: <String>['read', 'op:execute'],
  remember: false,
);

NetworkServer _server(String name, {bool react = true}) => NetworkServer(
  name: name,
  address: '127.0.0.1:25566',
  status: NetworkServerStatus.online,
  players: 1,
  react: react,
  basePath: '/network/${Uri.encodeComponent(name)}',
);

FleetManager _fleet(InMemoryFleetStorage storage) => FleetManager(
  storage: storage,
  clientFactory: (ServerCredential cred) => _StubClient(cred),
);

void main() {
  group('ServerCredential.networkMemberOf', () {
    test('reaches the server through the proxy with its pairing', () {
      final ServerCredential member = ServerCredential.networkMemberOf(
        _proxy,
        'Lobby One',
        '/network/Lobby%20One',
      );
      expect(member.id, 'proxy~lobby-one');
      expect(member.label, 'Lobby One');
      expect(member.host, _proxy.host);
      expect(member.port, _proxy.port);
      expect(member.bearer, _proxy.bearer);
      expect(member.basePath, '/edge/network/Lobby%20One');
      expect(member.fingerprint, isNull);
      expect(member.linked, isTrue);
      expect(member.remember, isFalse);
      expect(member.role, 'operator');
      expect(member.tokenId, 'tok_a');
      expect(member.networkOf, 'proxy');
      expect(member.networkServer, 'Lobby One');
      expect(
        member.directEndpoint('api/v1/metrics').toString(),
        'http://10.0.0.5:9696/edge/network/Lobby%20One/api/v1/metrics',
      );
    });

    test('keeps its network fields through storage', () {
      final ServerCredential member = ServerCredential.networkMemberOf(
        _proxy,
        'lobby',
        '/network/lobby',
      );
      final ServerCredential read = ServerCredential.fromJson(
        jsonDecode(jsonEncode(member.toJson())) as Map<String, dynamic>,
      );
      expect(read.networkOf, 'proxy');
      expect(read.networkServer, 'lobby');
      expect(read.basePath, '/edge/network/lobby');
      expect(read.linked, isTrue);
      expect(_proxy.networkMember, isFalse);
      expect(read.networkMember, isTrue);
    });
  });

  group('FleetManager.syncNetwork', () {
    test('lists React servers only and leaves the rest out', () {
      final FleetManager fleet = _fleet(InMemoryFleetStorage());
      fleet.add(_proxy);
      final ({List<String> opened, List<String> removed}) changed = fleet
          .syncNetwork('proxy', <NetworkServer>[
            _server('lobby'),
            _server('survival'),
            _server('vanilla', react: false),
          ]);
      expect(changed.opened, <String>['proxy~lobby', 'proxy~survival']);
      expect(changed.removed, isEmpty);
      expect(
        fleet.membersOf('proxy').map((ServerCredential c) => c.label),
        <String>['lobby', 'survival'],
      );
      expect(fleet.sessionFor('proxy~lobby'), isNotNull);
    });

    test('an unchanged network keeps every session', () {
      final FleetManager fleet = _fleet(InMemoryFleetStorage());
      fleet.add(_proxy);
      fleet.syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      final Object? session = fleet.sessionFor('proxy~lobby');
      final ({List<String> opened, List<String> removed}) changed = fleet
          .syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      expect(changed.opened, isEmpty);
      expect(changed.removed, isEmpty);
      expect(identical(fleet.sessionFor('proxy~lobby'), session), isTrue);
    });

    test('drops servers that left the network or lost React', () {
      final FleetManager fleet = _fleet(InMemoryFleetStorage());
      fleet.add(_proxy);
      fleet.syncNetwork('proxy', <NetworkServer>[
        _server('lobby'),
        _server('survival'),
      ]);
      final ({List<String> opened, List<String> removed}) changed = fleet
          .syncNetwork('proxy', <NetworkServer>[
            _server('lobby'),
            _server('survival', react: false),
          ]);
      expect(changed.removed, <String>['proxy~survival']);
      expect(fleet.sessionFor('proxy~survival'), isNull);
      expect(fleet.membersOf('proxy'), hasLength(1));
    });

    test('removing the proxy removes its servers', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fleet = _fleet(storage);
      fleet.add(_proxy);
      fleet.syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      fleet.remove('proxy');
      expect(fleet.servers, isEmpty);
      expect(fleet.sessionFor('proxy~lobby'), isNull);
      expect(_fleet(storage).servers, isEmpty);
    });

    test('a new pairing of the proxy moves its servers to the new bearer', () {
      final FleetManager fleet = _fleet(InMemoryFleetStorage());
      fleet.add(_proxy);
      fleet.syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      fleet.add(_proxy.copyWith(bearer: 'tok_b.other', port: 9797));
      final ServerCredential member = fleet.membersOf('proxy').single;
      expect(member.bearer, 'tok_b.other');
      expect(member.port, 9797);
      expect(member.basePath, '/edge/network/lobby');
    });

    test('members are stored and come back on load', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fleet = _fleet(storage);
      fleet.add(_proxy.copyWith(remember: true));
      fleet.syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      final FleetManager reloaded = _fleet(storage);
      expect(reloaded.membersOf('proxy').single.label, 'lobby');
    });

    test('a member is never synced as a proxy', () {
      final FleetManager fleet = _fleet(InMemoryFleetStorage());
      fleet.add(_proxy);
      fleet.syncNetwork('proxy', <NetworkServer>[_server('lobby')]);
      final ({List<String> opened, List<String> removed}) changed = fleet
          .syncNetwork('proxy~lobby', <NetworkServer>[_server('inner')]);
      expect(changed.opened, isEmpty);
    });
  });
}
