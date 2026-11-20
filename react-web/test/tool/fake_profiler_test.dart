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
