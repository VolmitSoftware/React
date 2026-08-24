library;

import 'dart:collection';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/app/reactor_app.dart';
import 'package:react_web/model/environment_info.dart';
import 'package:react_web/screen/environment.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/operate_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

EnvironmentInfo _fakeInfo() => EnvironmentInfo(
  sections: LinkedHashMap<String, Map<String, Object?>>.from(
    <String, Map<String, Object?>>{
      'cpu': <String, Object?>{'model': 'Apple M3', 'cores': 8},
      'memory': <String, Object?>{'usedMb': 18000},
      'jvm': <String, Object?>{'version': '25'},
      'server': <String, Object?>{'brand': 'Purpur'},
    },
  ),
  disks: const <EnvironmentDisk>[
    EnvironmentDisk(
      name: 'disk0',
      model: 'Fast Disk',
      sizeBytes: 1000,
      readBytes: 400,
      writeBytes: 250,
      reads: 4,
      writes: 2,
      queueLength: 0,
      transferTimeMillis: 10,
      timestampMillis: 100,
    ),
  ],
  mounts: const <EnvironmentMount>[
    EnvironmentMount(
      name: 'root',
      mount: '/',
      description: 'Root',
      type: 'apfs',
      totalBytes: 1000,
      freeBytes: 300,
      usableBytes: 250,
    ),
  ],
  network: const <EnvironmentNetworkInterface>[
    EnvironmentNetworkInterface(
      name: 'en0',
      displayName: 'Primary network',
      mtu: 1500,
      macAddress: '',
      ipv4Addresses: <String>['127.0.0.1'],
      ipv6Addresses: <String>[],
      speedBitsPerSecond: 1000000000,
      receivedBytes: 800,
      sentBytes: 500,
      receivedPackets: 8,
      sentPackets: 5,
      receiveErrors: 0,
      sendErrors: 0,
      receiveDrops: 0,
      collisions: 0,
      timestampMillis: 100,
    ),
  ],
);

Widget _wrapView(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('EnvironmentScreen route wiring', () {
    test('kRouteServerEnvironment constant is correct', () {
      expect(kRouteServerEnvironment, equals('/server/:id/environment'));
    });

    test('buildReactorRoutes includes environment route', () {
      final List<RouteBase> routes = buildReactorRoutes();
      final bool hasEnv = routes.any(
        (RouteBase r) => r is Route && r.path == kRouteServerEnvironment,
      );
      expect(
        hasEnv,
        isTrue,
        reason: 'per-server Environment tab requires route',
      );
    });
  });

  group('ReactorShell sidebar', () {
    testServer('renders Environment sidebar item for a connected server', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          ReactorShell(
            currentPath: '/server/srv1/environment',
            servers: const <ServerEntry>[
              ServerEntry(
                id: 'srv1',
                name: 'Test Server',
                state: ConnState.live,
              ),
            ],
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Environment'),
        isTrue,
        reason: 'Environment sidebar item must appear for connected server',
      );
    });
  });

  group('EnvironmentView', () {
    testServer('renders Apple M3 value from cpu section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Apple M3'),
        isTrue,
        reason: 'cpu.model value must appear in rendered HTML',
      );
    });

    testServer('renders cores value 8 from cpu section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('8'),
        isTrue,
        reason: 'cpu.cores value must appear in rendered HTML',
      );
    });

    testServer('renders usedMb value 18000 from memory section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('18000'),
        isTrue,
        reason: 'memory.usedMb value must appear in rendered HTML',
      );
    });

    testServer('renders Purpur value from server section', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Purpur'),
        isTrue,
        reason: 'server.brand value must appear in rendered HTML',
      );
    });

    testServer('renders CPU section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('CPU'),
        isTrue,
        reason: 'CPU section heading must appear in rendered HTML',
      );
    });

    testServer('renders Memory section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Memory'),
        isTrue,
        reason: 'Memory section heading must appear in rendered HTML',
      );
    });

    testServer('renders JVM section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('JVM'),
        isTrue,
        reason: 'JVM section heading must appear in rendered HTML',
      );
    });

    testServer('renders Server section heading', (ServerTester tester) async {
      tester.pumpComponent(_wrapView(EnvironmentView(info: _fakeInfo())));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Server'),
        isTrue,
        reason: 'Server section heading must appear in rendered HTML',
      );
    });

    testServer('renders hoverable Disk and Network charts', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          EnvironmentView(
            info: _fakeInfo(),
            diskReadHistory: const <double>[0, 128],
            diskWriteHistory: const <double>[0, 64],
            networkReceiveHistory: const <double>[0, 256],
            networkSendHistory: const <double>[0, 96],
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body.contains('Read / write throughput'), isTrue);
      expect(res.body.contains('Receive / send throughput'), isTrue);
      expect(res.body.contains('reactor-chart-hit-target'), isTrue);
      expect(res.body.contains('Primary network'), isTrue);
    });
  });

  group('EnvironmentScreen null client', () {
    testServer('renders live-connection note when client is null', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrapView(
          OperateScope(
            client: null,
            logSocketFactory: null,
            child: const EnvironmentScreen(),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Environment data requires a live connection.'),
        isTrue,
        reason:
            'live-connection note must appear when OperateScope client is null',
      );
    });
  });
}
