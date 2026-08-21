library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _FullClient implements IReactClient, IControlClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: 'Full',
    version: '1.0.0',
    folia: false,
    serverId: '127.0.0.1:9696',
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  @override
  Future<List<ControlItem>> features() async => <ControlItem>[];

  @override
  Future<ControlItem> feature(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setFeatureEnabled(String id, bool enabled) async =>
      throw UnimplementedError();

  @override
  Future<ControlItem> setFeatureConfig(
    String id,
    Map<String, Object?> knobs,
  ) async => throw UnimplementedError();

  @override
  Future<List<ControlItem>> tweaks() async => <ControlItem>[];

  @override
  Future<ControlItem> tweak(String id) async => throw UnimplementedError();

  @override
  Future<ControlItem> setTweakEnabled(String id, bool enabled) async =>
      throw UnimplementedError();

  @override
  Future<ControlItem> setTweakConfig(
    String id,
    Map<String, Object?> knobs,
  ) async => throw UnimplementedError();

  @override
  Future<List<WorldSettings>> worlds() async => <WorldSettings>[];

  @override
  Future<WorldSettings> setWorld(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async => throw UnimplementedError();
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
  group('FleetManager.controlClientFor', () {
    test('returns the control-capable client for a known server id', () {
      final _FullClient full = _FullClient();
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => full,
      );
      expect(fm.controlClientFor('srv-1'), same(full));
    });

    test('returns null for an unknown id', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FullClient(),
      );
      expect(fm.controlClientFor('no-such-id'), isNull);
    });

    test('returns null when the client is not control-capable', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fm.controlClientFor('srv-1'), isNull);
    });
  });
}
