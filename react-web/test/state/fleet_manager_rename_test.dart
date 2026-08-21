library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

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
  String label = 'Old',
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
  String label = 'Old',
}) {
  storage.write(
    FleetManager.storageKey,
    jsonEncode(<Map<String, dynamic>>[_credJson(id: id, label: label)]),
  );
  return FleetManager(storage: storage, clientFactory: clientFactory);
}

void main() {
  group('FleetManager.rename', () {
    test('rename updates the label in servers list', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seededFleetManager(
        storage: storage,
        clientFactory: (_) => _StubClient(),
      );

      fm.rename('srv-1', 'New');

      final ServerCredential cred = fm.servers.firstWhere(
        (ServerCredential c) => c.id == 'srv-1',
      );
      expect(cred.label, equals('New'));
    });

    test('rename persists so a new FleetManager reads the new label', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seededFleetManager(
        storage: storage,
        clientFactory: (_) => _StubClient(),
      );

      fm.rename('srv-1', 'New');

      final FleetManager fm2 = FleetManager(
        storage: storage,
        clientFactory: (_) => _StubClient(),
      );
      final ServerCredential cred = fm2.servers.firstWhere(
        (ServerCredential c) => c.id == 'srv-1',
      );
      expect(cred.label, equals('New'));
    });

    test('managerFor still returns non-null after rename', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seededFleetManager(
        storage: storage,
        clientFactory: (_) => _StubClient(),
      );

      fm.rename('srv-1', 'New');

      expect(fm.managerFor('srv-1'), isNotNull);
    });

    test('rename with unknown id is a no-op', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final FleetManager fm = _seededFleetManager(
        storage: storage,
        clientFactory: (_) => _StubClient(),
      );

      expect(() => fm.rename('nope', 'X'), returnsNormally);
      expect(fm.servers.length, equals(1));
      expect(fm.servers.first.label, equals('Old'));
    });
  });
}
