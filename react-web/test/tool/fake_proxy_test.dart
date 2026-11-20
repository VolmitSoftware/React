import 'package:http/http.dart' as http;
import 'package:test/test.dart';

import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/network_info.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_summary.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

import '../../tool/fake_server/fixtures.dart';
import '../../tool/fake_server/fleet.dart';

void main() {
  late FakeFleet fleet;
  late FakeServerProfile edge;
  late ServerCredential proxy;

  setUpAll(() async {
    fleet = await FakeFleet.start(port: 0, servers: 3, proxy: true);
    edge = fleet.profiles.first;
    proxy = ServerCredential(
      id: 'edge',
      label: edge.label,
      host: '127.0.0.1',
      port: fleet.port,
      basePath: '/${edge.slug}',
      bearer: fleet.adminToken(edge).bearer,
    );
  });

  tearDownAll(() async {
    await fleet.close();
  });

  ReactClient clientFor(ServerCredential cred) {
    final http.Client transport = http.Client();
    addTearDown(transport.close);
    return ReactClient(cred, client: transport);
  }

  test('the proxy says proxy and lists the servers behind it', () async {
    final ReactClient client = clientFor(proxy);
    final IdentityInfo identity = await client.identity();
    expect(identity.proxy, isTrue);
    expect(identity.loader, 'Velocity');
    final NetworkInfo network = await client.network();
    expect(network.proxy.kind, 'Velocity');
    expect(network.servers.map((NetworkServer s) => s.name), <String>[
      's2',
      's3',
      'legacy',
    ]);
    expect(network.servers.last.react, isFalse);
    expect(network.servers.last.online, isFalse);
    expect(network.servers.first.tps, isNotNull);
    expect(network.servers.first.memoryMaxBytes, isNotNull);
  });

  test('a server behind the proxy answers with the proxy pairing', () async {
    final NetworkInfo network = await clientFor(proxy).network();
    final NetworkServer first = network.servers.first;
    final ReactClient member = clientFor(
      ServerCredential.networkMemberOf(proxy, first.name, first.basePath),
    );
    final ServerSummary summary = await member.summary();
    expect(summary.serverName, fleet.profiles[1].label);
    expect((await member.identity()).proxy, isFalse);
  });

  test('a server that is not a proxy has no network', () async {
    final FakeServerProfile behind = fleet.profiles[1];
    final ReactClient client = clientFor(
      ServerCredential(
        id: 'direct',
        label: behind.label,
        host: '127.0.0.1',
        port: fleet.port,
        basePath: '/${behind.slug}',
        bearer: fleet.adminToken(behind).bearer,
      ),
    );
    await expectLater(client.network(), throwsA(isA<ReactNotFound>()));
  });
}
