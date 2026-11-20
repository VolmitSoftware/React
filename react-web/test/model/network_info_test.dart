library;

import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/network_info.dart';

void main() {
  test('reads the proxy and every server of a network answer', () {
    final NetworkInfo info = NetworkInfo.fromJson(<String, dynamic>{
      'proxy': <String, dynamic>{
        'name': 'Hub proxy',
        'kind': 'Velocity',
        'version': '3.4.0',
        'players': 12,
        'servers': 3,
        'online': 2,
        'react': 1,
      },
      'servers': <dynamic>[
        <String, dynamic>{
          'name': 'lobby',
          'address': '127.0.0.1:25566',
          'status': 'online',
          'players': 8,
          'pingMs': 3,
          'react': true,
          'tps': 19.98,
          'mspt': 12.5,
          'memoryUsedBytes': 1073741824,
          'memoryMaxBytes': 4294967296,
          'platform': 'bukkit',
          'loader': 'Paper',
          'version': 'Paper 26.3',
          'lastSeenMs': 1700000000000,
          'basePath': '/api/v1/network/servers/lobby',
        },
        <String, dynamic>{
          'name': 'old world',
          'address': '127.0.0.1:25568',
          'status': 'offline',
          'players': 0,
          'pingMs': null,
          'react': false,
          'tps': null,
        },
        'not a server',
      ],
    });
    expect(info.proxy.kind, 'Velocity');
    expect(info.proxy.online, 2);
    expect(info.servers, hasLength(2));
    final NetworkServer lobby = info.servers.first;
    expect(lobby.online, isTrue);
    expect(lobby.react, isTrue);
    expect(lobby.tps, closeTo(19.98, 1e-9));
    expect(lobby.memoryMaxBytes, 4294967296);
    expect(lobby.loader, 'Paper');
    final NetworkServer old = info.servers.last;
    expect(old.status, NetworkServerStatus.offline);
    expect(old.pingMs, isNull);
    expect(old.react, isFalse);
    expect(old.basePath, '/api/v1/network/servers/old%20world');
  });

  test('an unknown status reads as unknown', () {
    final NetworkServer server = NetworkServer.fromJson(<String, dynamic>{
      'name': 'a',
      'status': 'starting',
    });
    expect(server.status, NetworkServerStatus.unknown);
    expect(server.players, 0);
  });

  test('an answer without a proxy is refused', () {
    expect(
      () => NetworkInfo.fromJson(<String, dynamic>{'servers': <dynamic>[]}),
      throwsFormatException,
    );
  });

  test('a proxy identity says proxy', () {
    final IdentityInfo identity = IdentityInfo.fromJson(<String, dynamic>{
      'serverName': 'Proxy',
      'folia': false,
      'serverId': '0.0.0.0:25565',
      'platform': 'proxy',
      'loader': 'Velocity',
    });
    expect(identity.proxy, isTrue);
    expect(identity.modded, isFalse);
  });
}
