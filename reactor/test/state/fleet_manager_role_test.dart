library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/role_info.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/memory_fleet_storage.dart';

class _RoleCapableClient implements IReactClient, IRoleClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
        serverName: 'RoleServer',
        version: '1.0.0',
        folia: false,
        serverId: '127.0.0.1:9696',
      );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  @override
  Future<RoleInfo> whoami() async => RoleInfo.fromJson(<String, dynamic>{
        'role': 'admin',
        'scopes': <String>['read', 'op:execute', 'admin'],
      });
}

class _MetricsOnlyClient implements IReactClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
        serverName: 'MetricsOnly',
        version: '1.0.0',
        folia: false,
        serverId: '127.0.0.1:9696',
      );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

Map<String, dynamic> _credJson({
  String id = 'srv-1',
  String label = 'TestServer',
  String host = 'localhost',
  int port = 7979,
  String bearer = 'tid1.tsig1',
}) =>
    <String, dynamic>{
      'id': id,
      'label': label,
      'host': host,
      'port': port,
      'bearer': bearer,
      'secure': false,
    };

FleetManager _seededFleetManager({
  required FleetStorage storage,
  required ReactClientFactory clientFactory,
  String id = 'srv-1',
}) {
  storage.write(
    FleetManager.storageKey,
    jsonEncode(<Map<String, dynamic>>[_credJson(id: id)]),
  );
  return FleetManager(storage: storage, clientFactory: clientFactory);
}

void main() {
  group('FleetManager.roleClientFor', () {
    test('returns the role-capable client for a known server id', () {
      final _RoleCapableClient client = _RoleCapableClient();
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => client,
      );
      expect(fm.roleClientFor('srv-1'), same(client));
    });

    test('returns null for an unknown id', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _RoleCapableClient(),
      );
      expect(fm.roleClientFor('no-such-id'), isNull);
    });

    test('returns null when the client is not role-capable', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fm.roleClientFor('srv-1'), isNull);
    });
  });
}
