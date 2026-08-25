library;

import 'dart:convert';

import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/player_navigation.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _DualClient implements IReactClient, IHeatmapClient, IPlayerClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: 'Dual',
    version: '1.0.0',
    folia: false,
    serverId: '127.0.0.1:9696',
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  @override
  Future<List<HeatmapSummary>> heatmaps() async => <HeatmapSummary>[];

  @override
  Future<HeatmapGrid> heatmap(
    String id, {
    String? world,
    int? centerChunkX,
    int? centerChunkZ,
    int? radius,
  }) async => throw UnimplementedError();

  @override
  Future<List<OnlinePlayerInfo>> players() async => <OnlinePlayerInfo>[];

  @override
  Future<PlayerTeleportResult> teleportPlayer(
    String playerId, {
    required String worldKey,
    required int blockX,
    required int blockZ,
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
  group('FleetManager.heatmapClientFor', () {
    test('returns the heatmap-capable client for a known server id', () {
      final _DualClient dual = _DualClient();
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => dual,
      );
      expect(fm.heatmapClientFor('srv-1'), same(dual));
    });

    test('returns null for an unknown id', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _DualClient(),
      );
      expect(fm.heatmapClientFor('no-such-id'), isNull);
    });

    test('returns null when the client is not heatmap-capable', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fm.heatmapClientFor('srv-1'), isNull);
    });
  });

  group('FleetManager.playerClientFor', () {
    test('returns only a player-capable client for a known server', () {
      final _DualClient dual = _DualClient();
      final FleetManager fleet = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => dual,
      );
      expect(fleet.playerClientFor('srv-1'), same(dual));
    });

    test('returns null for a metrics-only client', () {
      final FleetManager fleet = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fleet.playerClientFor('srv-1'), isNull);
    });
  });
}
