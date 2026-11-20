library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/model/network_info.dart';
import 'package:react_web/screen/network.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';

const ReactorStylesheet _sheet = ReactorStylesheet();

const NetworkInfo _info = NetworkInfo(
  proxy: NetworkProxyInfo(
    name: 'Edge',
    kind: 'Velocity',
    version: '3.4.0',
    players: 9,
    servers: 2,
    online: 1,
    react: 1,
  ),
  servers: <NetworkServer>[
    NetworkServer(
      name: 'lobby',
      address: '127.0.0.1:25566',
      status: NetworkServerStatus.online,
      players: 9,
      pingMs: 4,
      react: true,
      tps: 19.9,
      mspt: 11.24,
      memoryUsedBytes: 1073741824,
      memoryMaxBytes: 4294967296,
      basePath: '/api/v1/network/servers/lobby',
    ),
    NetworkServer(
      name: 'vanilla',
      address: '127.0.0.1:25568',
      status: NetworkServerStatus.offline,
      players: 0,
      react: false,
      basePath: '/api/v1/network/servers/vanilla',
    ),
  ],
);

void main() {
  testServer('lists every server and links React servers to their pages', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      ArcaneThemeProvider(
        stylesheet: _sheet,
        child: NetworkView(
          info: _info,
          memberPath: (NetworkServer server) =>
              server.react ? '/server/proxy~${server.name}' : null,
        ),
      ),
    );
    final DocumentResponse res = await tester.request('/');
    expect(res.statusCode, equals(200));
    final String body = res.body;
    expect(body, contains('Velocity 3.4.0'));
    expect(body, contains('1 / 2'));
    expect(body, contains('lobby'));
    expect(body, contains('vanilla'));
    expect(body, contains('19.9'));
    expect(body, contains('11.2 ms'));
    expect(body, contains('1.0 GB / 4.0 GB'));
    expect(body, contains('Not reachable'));
    expect(body, contains('href="/server/proxy~lobby"'));
    expect(body, isNot(contains('href="/server/proxy~vanilla"')));
    expect(body, contains('Servers without React show'));
  });
}
