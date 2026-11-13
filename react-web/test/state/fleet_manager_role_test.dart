library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _RoleCapableClient implements IReactClient, IRoleClient {
  @override
  Future<void> close() async {}

  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: 'RoleServer',
    folia: false,
    serverId: '127.0.0.1:9696',
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  int asked = 0;
  bool failNext = false;

  @override
  Future<RoleInfo> whoami() async {
    asked++;
    if (failNext) {
      failNext = false;
      throw StateError('offline');
    }
    return RoleInfo.fromJson(<String, dynamic>{
      'role': 'admin',
      'scopes': <String>['read', 'op:execute', 'admin'],
    });
  }
}

class _MetricsOnlyClient implements IReactClient {
  @override
  Future<void> close() async {}

  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: 'MetricsOnly',
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
}) => <String, dynamic>{
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
  group('ServerSession.roles', () {
    test('asks /whoami once for every page of the session', () async {
      final _RoleCapableClient client = _RoleCapableClient();
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => client,
      );
      final IRoleClient roles = fm.sessionFor('srv-1')!.roles!;
      expect((await roles.whoami()).role, 'admin');
      expect((await roles.whoami()).role, 'admin');
      expect(identical(fm.sessionFor('srv-1')!.roles, roles), isTrue);
      expect(client.asked, 1);
    });

    test('asks again after a failed answer', () async {
      final _RoleCapableClient client = _RoleCapableClient()..failNext = true;
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => client,
      );
      final IRoleClient roles = fm.sessionFor('srv-1')!.roles!;
      await expectLater(roles.whoami(), throwsStateError);
      expect((await roles.whoami()).role, 'admin');
      expect(client.asked, 2);
    });

    test('is null when the client has no role route', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fm.sessionFor('srv-1')!.roles, isNull);
    });
  });
}
