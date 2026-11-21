import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:test/test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/model/network_info.dart';
import 'package:react_web/model/profiler.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';

import '../../tool/fake_server/fixtures.dart';
import '../../tool/fake_server/fleet.dart';
import '../../tool/fake_server/profiler_fixtures.dart';

void main() {
  late FakeFleet fleet;
  late FakeServerProfile survival;

  setUpAll(() async {
    fleet = await FakeFleet.start(port: 0, servers: 3, proxy: true);
    survival = fleet.profiles[1];
  });

  tearDownAll(() async {
    await fleet.close();
  });

  ServerCredential credential(FakeServerProfile profile, FakeToken token) =>
      ServerCredential(
        id: profile.slug,
        label: profile.label,
        host: '127.0.0.1',
        port: fleet.port,
        basePath: '/${profile.slug}',
        bearer: token.bearer,
      );

  ReactClient clientFor(ServerCredential cred) {
    final http.Client transport = http.Client();
    addTearDown(transport.close);
    return ReactClient(cred, client: transport);
  }

  Future<void> sampled(ReactClient client) async {
    final DateTime deadline = DateTime.now().add(const Duration(seconds: 5));
    while ((await client.profiler()).samples < 200) {
      if (DateTime.now().isAfter(deadline)) fail('no samples');
      await Future<void>.delayed(const Duration(milliseconds: 50));
    }
  }

  Future<Map<String, dynamic>> getData(
    FakeServerProfile profile,
    FakeToken token,
    String path,
  ) async {
    final http.Client transport = http.Client();
    addTearDown(transport.close);
    final http.Response response = await transport.get(
      Uri.parse('http://127.0.0.1:${fleet.port}/${profile.slug}/api/v1$path'),
      headers: <String, String>{'Authorization': 'Bearer ${token.bearer}'},
    );
    expect(response.statusCode, HttpStatus.ok, reason: response.body);
    return (jsonDecode(response.body) as Map<String, dynamic>)['data']
        as Map<String, dynamic>;
  }

  List<Map<String, dynamic>> nodes(Object? value) =>
      (value as List<dynamic>).cast<Map<String, dynamic>>();

  test('the profiler is off until an admin starts it', () async {
    final ReactClient admin = clientFor(
      credential(survival, fleet.adminToken(survival)),
    );
    final ReactClient viewer = clientFor(
      credential(survival, fleet.issue(survival, role: 'viewer')),
    );
    final ProfilerStatus idle = await viewer.profiler();
    expect(idle.running, isFalse);
    expect(idle.samples, 0);
    await expectLater(
      viewer.controlProfiler(const ProfilerStart()),
      throwsA(isA<ReactForbidden>()),
    );
    final ProfilerStatus started = await admin.controlProfiler(
      const ProfilerStart(intervalMs: 2, maxSeconds: 120),
    );
    expect(started.running, isTrue);
    expect(started.intervalMs, 2);
    expect(
      started.stopsAt!.difference(started.startedAt!),
      const Duration(seconds: 120),
    );
    await sampled(admin);

    final ProfilerOwners owners = await viewer.profilerOwners();
    expect(owners.owners.length, greaterThan(20));
    expect(owners.byId('React'), isNotNull);
    expect(owners.owners.first.top, isNotEmpty);
    final double share = owners.owners.fold(
      0,
      (double sum, ProfilerOwner owner) => sum + owner.percent,
    );
    expect(share, closeTo(100, 1));

    final ProfilerHot iris = await viewer.profilerHot(owner: 'Iris', limit: 5);
    expect(iris.methods, isNotEmpty);
    expect(iris.methods.length, lessThanOrEqualTo(5));
    expect(
      iris.methods.every((ProfilerHotMethod method) => method.owner == 'Iris'),
      isTrue,
    );

    final ProfilerTree tree = await viewer.profilerTree(maxDepth: 6);
    expect(tree.roots.single.method, 'Server thread');
    int depth(ProfilerNode node) => node.children.isEmpty
        ? 1
        : 1 + node.children.map(depth).reduce((int a, int b) => a > b ? a : b);
    expect(depth(tree.roots.single), lessThanOrEqualTo(6));

    final ProfilerTree pruned = await viewer.profilerTree(owner: 'WorldEdit');
    bool holds(ProfilerNode node) =>
        node.owner == 'WorldEdit' || node.children.any(holds);
    expect(pruned.roots, isNotEmpty);
    expect(pruned.roots.every(holds), isTrue);

    final List<ProfilerThread> threads = await viewer.profilerThreads();
    expect(threads.single.name, 'Server thread');

    final ProfilerStatus stopped = await admin.controlProfiler(
      const ProfilerStop(),
    );
    expect(stopped.running, isFalse);
    expect((await viewer.profiler()).samples, stopped.samples);
  });

  test('all threads, and a reset clears the samples', () async {
    final ReactClient admin = clientFor(
      credential(fleet.profiles[2], fleet.adminToken(fleet.profiles[2])),
    );
    await admin.controlProfiler(
      const ProfilerStart(intervalMs: 1, threads: ProfilerThreads.all),
    );
    await sampled(admin);
    final List<ProfilerThread> threads = await admin.profilerThreads();
    expect(threads.length, greaterThan(5));
    final ProfilerTree mantle = await admin.profilerTree(
      thread: 'Iris Mantle Writer',
    );
    expect(mantle.roots.single.method, 'Iris Mantle Writer');
    await admin.controlProfiler(const ProfilerStop());
    final ProfilerStatus reset = await admin.controlProfiler(
      const ProfilerReset(),
    );
    expect(reset.samples, 0);
  });

  test(
    'an operator runs the profiler and snapshots, a viewer cannot',
    () async {
      final FakeServerProfile edge = fleet.profiles.first;
      final ReactClient operator = clientFor(
        credential(edge, fleet.issue(edge, role: 'operator')),
      );
      final ReactClient viewer = clientFor(
        credential(edge, fleet.issue(edge, role: 'viewer')),
      );
      await expectLater(
        viewer.controlProfiler(const ProfilerStart()),
        throwsA(isA<ReactForbidden>()),
      );
      expect(
        (await operator.controlProfiler(const ProfilerStart(intervalMs: 1)))
            .running,
        isTrue,
      );
      await sampled(operator);
      final SnapshotMeta meta = await operator.createSnapshot(
        const SnapshotRequest.profile(),
      );
      await expectLater(
        viewer.createSnapshot(const SnapshotRequest.profile()),
        throwsA(isA<ReactForbidden>()),
      );
      await expectLater(
        viewer.deleteSnapshot(meta.id),
        throwsA(isA<ReactForbidden>()),
      );
      await operator.deleteSnapshot(meta.id);
      await expectLater(
        viewer.snapshot(meta.id),
        throwsA(isA<ReactNotFound>()),
      );
      expect(
        (await operator.controlProfiler(const ProfilerStop())).running,
        isFalse,
      );
    },
  );

  test(
    'numbered threads group, plumbing collapses, idle is left out',
    () async {
      final FakeServerProfile lobby = fleet.profiles[2];
      final FakeToken token = fleet.adminToken(lobby);
      final ReactClient admin = clientFor(credential(lobby, token));
      await admin.controlProfiler(
        const ProfilerStart(intervalMs: 1, threads: ProfilerThreads.all),
      );
      await sampled(admin);
      await admin.controlProfiler(const ProfilerStop());

      final Map<String, dynamic> grouped = await getData(
        lobby,
        token,
        '/profiler/tree?minPercent=0',
      );
      final List<String> roots = <String>[
        for (final Map<String, dynamic> root in nodes(grouped['roots']))
          root['method'] as String,
      ];
      expect(roots, contains('Worker-Main (12)'));
      expect(roots, contains('Netty Epoll Server IO (2)'));
      expect(
        roots.where((String name) => name.startsWith('Worker-Main-')),
        isEmpty,
      );

      final Map<String, dynamic> raw = await getData(
        lobby,
        token,
        '/profiler/tree?minPercent=0&group=false&collapse=false',
      );
      final List<Map<String, dynamic>> rawRoots = nodes(raw['roots']);
      expect(
        rawRoots.where(
          (Map<String, dynamic> root) =>
              (root['method'] as String).startsWith('Worker-Main-'),
        ),
        hasLength(12),
      );
      final int workers = rawRoots
          .where(
            (Map<String, dynamic> root) =>
                (root['method'] as String).startsWith('Worker-Main-'),
          )
          .fold(
            0,
            (int sum, Map<String, dynamic> root) =>
                sum + (root['total'] as int),
          );
      final Map<String, dynamic> pool = nodes(grouped['roots']).firstWhere(
        (Map<String, dynamic> root) => root['method'] == 'Worker-Main (12)',
      );
      expect(pool['total'], workers);
      int depth(Map<String, dynamic> node) {
        final List<Map<String, dynamic>> children = nodes(node['children']);
        return children.isEmpty
            ? 1
            : 1 + children.map(depth).reduce((int a, int b) => a > b ? a : b);
      }

      expect(
        depth(
          rawRoots.firstWhere(
            (Map<String, dynamic> root) => root['method'] == 'Worker-Main-1',
          ),
        ),
        greaterThanOrEqualTo(30),
      );

      final List<Map<String, dynamic>> folded = <Map<String, dynamic>>[];
      void collect(Map<String, dynamic> node) {
        if (node['collapsed'] != null) folded.add(node);
        nodes(node['children']).forEach(collect);
      }

      nodes(grouped['roots']).forEach(collect);
      expect(folded, isNotEmpty);
      for (final Map<String, dynamic> node in folded) {
        final List<String> chain = (node['collapsed'] as List<dynamic>)
            .cast<String>();
        expect(chain.length, greaterThanOrEqualTo(2));
        expect(node['method'], '(${chain.length} frames)');
        expect(node['owner'], 'java');
      }
      expect(
        folded.any(
          (Map<String, dynamic> node) => (node['collapsed'] as List<dynamic>)
              .contains('java.util.concurrent.ForkJoinWorkerThread.run'),
        ),
        isTrue,
      );

      bool parked(Map<String, dynamic> node) =>
          (node['method'] as String).contains('LockSupport.park') ||
          ((node['collapsed'] as List<dynamic>?) ?? const <dynamic>[]).any(
            (dynamic method) => (method as String).contains('LockSupport.park'),
          ) ||
          nodes(node['children']).any(parked);
      final Map<String, dynamic> withIdle = await getData(
        lobby,
        token,
        '/profiler/tree?minPercent=0&idle=true',
      );
      expect(nodes(grouped['roots']).any(parked), isFalse);
      expect(nodes(withIdle['roots']).any(parked), isTrue);
      expect(
        withIdle['samples'] as int,
        greaterThan(grouped['samples'] as int),
      );
      final Map<String, dynamic> owners = await getData(
        lobby,
        token,
        '/profiler/owners',
      );
      expect(owners['samples'], grouped['samples']);
      final Map<String, dynamic> threads = await getData(
        lobby,
        token,
        '/profiler/threads?idle=true',
      );
      expect(
        nodes(threads['threads']).map((Map<String, dynamic> t) => t['name']),
        contains('Worker-Main-7'),
      );
      await admin.controlProfiler(const ProfilerReset());
    },
  );

  test('owner cost splits each owner by kind', () async {
    final FakeToken token = fleet.issue(survival, role: 'viewer');
    final Map<String, dynamic> cost = await getData(
      survival,
      token,
      '/owners/cost',
    );
    expect(cost['ownerNoun'], 'plugin');
    expect(cost['measuring'], isTrue);
    final List<Map<String, dynamic>> owners = nodes(cost['owners']);
    expect(owners.length, fakeOwnerSpecs.length);
    for (int index = 1; index < owners.length; index++) {
      expect(
        owners[index - 1]['msPerSecond'] as double,
        greaterThanOrEqualTo(owners[index]['msPerSecond'] as double),
      );
    }
    double total = 0;
    for (final Map<String, dynamic> owner in owners) {
      final double ms = owner['msPerSecond'] as double;
      total += ms;
      final double kinds = (owner['kinds'] as Map<String, dynamic>).values.fold(
        0,
        (double sum, dynamic value) => sum + (value as double),
      );
      expect(kinds, closeTo(ms, 1e-9), reason: owner['id'] as String);
      expect(
        owner['samplerId'],
        'plugin-${(owner['id'] as String).toLowerCase()}',
      );
    }
    expect(cost['totalMsPerSecond'] as double, closeTo(total, 1e-9));
    final List<String> kinds = <String>[
      for (final Map<String, dynamic> kind in nodes(cost['kinds']))
        kind['kind'] as String,
    ];
    expect(kinds, <String>[
      'event',
      'tick',
      'entity',
      'block_entity',
      'worldgen',
      'chunk_loader',
      'network',
    ]);
    final double shares = nodes(cost['kinds']).fold(
      0,
      (double sum, Map<String, dynamic> kind) =>
          sum + (kind['share'] as double),
    );
    expect(shares, closeTo(1, 1e-9));
    final Map<String, dynamic> top = await getData(
      survival,
      token,
      '/owners/cost?limit=3',
    );
    expect(nodes(top['owners']), hasLength(3));
  });

  test('snapshots freeze, list, export and delete', () async {
    final ReactClient admin = clientFor(
      credential(survival, fleet.adminToken(survival)),
    );
    final ReactClient viewer = clientFor(
      credential(survival, fleet.issue(survival, role: 'viewer')),
    );
    await admin.controlProfiler(const ProfilerStart(intervalMs: 1));
    await sampled(admin);
    final SnapshotMeta profile = await admin.createSnapshot(
      const SnapshotRequest.profile(label: 'Lag at spawn'),
    );
    expect(profile.kind, SnapshotKind.profile);
    expect(profile.label, 'Lag at spawn');
    expect(profile.bytes, greaterThan(0));
    final SnapshotMeta heatmap = await admin.createSnapshot(
      const SnapshotRequest.heatmap(
        HeatmapSnapshotQuery(
          id: 'entity-pressure-heatmap',
          world: 'world',
          centerChunkX: 4,
          centerChunkZ: -2,
          radius: 6,
        ),
      ),
    );
    expect(heatmap.kind, SnapshotKind.heatmap);
    await expectLater(
      viewer.createSnapshot(const SnapshotRequest.profile()),
      throwsA(isA<ReactForbidden>()),
    );

    final List<SnapshotMeta> all = await viewer.snapshots();
    expect(all.first.id, heatmap.id);
    expect(
      (await viewer.snapshots(kind: SnapshotKind.heatmap))
          .map((SnapshotMeta meta) => meta.id),
      <String>[heatmap.id],
    );

    final SnapshotDetail frozen = await viewer.snapshot(profile.id);
    expect(frozen.profile!.owners.owners, isNotEmpty);
    expect(frozen.profile!.tree.roots, isNotEmpty);
    final SnapshotDetail grid = await viewer.snapshot(heatmap.id);
    final HeatmapGrid map = grid.heatmap!;
    expect(map.centerChunkX, 4);
    expect(map.radius, 6);

    final SnapshotExport export = await viewer.exportSnapshot(profile);
    expect(export.gzipped, isTrue);
    expect(
      export.filename,
      matches(RegExp(r'^react-profile-Skyblock-\d{8}-\d{6}\.json\.gz$')),
    );
    final Map<String, dynamic> unpacked = jsonDecode(
      utf8.decode(gzip.decode(export.bytes)),
    ) as Map<String, dynamic>;
    expect((unpacked['meta'] as Map<String, dynamic>)['id'], profile.id);

    await expectLater(
      viewer.deleteSnapshot(profile.id),
      throwsA(isA<ReactForbidden>()),
    );
    await admin.deleteSnapshot(profile.id);
    await expectLater(
      viewer.snapshot(profile.id),
      throwsA(isA<ReactNotFound>()),
    );
    await admin.controlProfiler(const ProfilerStop());
  });

  test('the profiler answers through the network proxy', () async {
    final FakeServerProfile edge = fleet.profiles.first;
    final ServerCredential proxy = credential(edge, fleet.adminToken(edge));
    final NetworkInfo network = await clientFor(proxy).network();
    final NetworkServer behind = network.servers.first;
    final ReactClient member = clientFor(
      ServerCredential.networkMemberOf(proxy, behind.name, behind.basePath),
    );
    expect((await member.profiler()).running, isFalse);
    await member.controlProfiler(const ProfilerStart(intervalMs: 1));
    await sampled(member);
    final SnapshotMeta meta = await member.createSnapshot(
      const SnapshotRequest.profile(),
    );
    final SnapshotExport export = await member.exportSnapshot(meta);
    expect(gzip.decode(export.bytes), isNotEmpty);
    await member.controlProfiler(const ProfilerStop());
  });
}
