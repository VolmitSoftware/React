import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/profiler.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/profiler_view.dart';

class _FakeProfilerClient implements IProfilerClient {
  ProfilerStatus status = ProfilerStatus.idle;
  final List<String> calls = <String>[];
  ProfilerCommand? lastCommand;
  Object? refusal;

  @override
  Future<ProfilerStatus> profiler() async {
    calls.add('status');
    return status;
  }

  @override
  Future<ProfilerStatus> controlProfiler(ProfilerCommand command) async {
    lastCommand = command;
    final Object? refused = refusal;
    if (refused != null) throw refused;
    status = ProfilerStatus(
      running: command is ProfilerStart,
      intervalMs: 10,
      threads: ProfilerThreads.server,
      samples: status.samples,
      durationMs: 0,
    );
    return status;
  }

  @override
  Future<ProfilerOwners> profilerOwners({bool idle = false}) async {
    calls.add('owners${idle ? ':idle' : ''}');
    return ProfilerOwners.empty;
  }

  @override
  Future<ProfilerHot> profilerHot({
    String? owner,
    int limit = 50,
    bool idle = false,
  }) async {
    calls.add('hot:${owner ?? ''}');
    return ProfilerHot.empty;
  }

  @override
  Future<ProfilerTree> profilerTree({
    String? owner,
    String? thread,
    double minPercent = 0.5,
    int maxDepth = 64,
    bool idle = false,
  }) async {
    calls.add('tree:${owner ?? ''}${idle ? ':idle' : ''}');
    return const ProfilerTree(10, <ProfilerNode>[
      ProfilerNode(
        method: 'Server thread',
        owner: 'minecraft',
        ownerName: 'Minecraft',
        total: 10,
        self: 0,
      ),
    ]);
  }

  @override
  Future<List<ProfilerThread>> profilerThreads({bool idle = false}) async {
    calls.add('threads');
    return const <ProfilerThread>[];
  }
}

ProfilerStatus _stopped(int samples) => ProfilerStatus(
  running: false,
  intervalMs: 10,
  threads: ProfilerThreads.server,
  samples: samples,
  durationMs: 1000,
);

void main() {
  test('the refresh pick is kept per browser, paused included', () {
    final FleetStorage storage = InMemoryFleetStorage();
    final ProfilerViewController first = ProfilerViewController(
      _FakeProfilerClient(),
      storage: storage,
    );
    addTearDown(first.dispose);
    expect(first.refresh, ProfilerRefresh.s2);
    first.setRefresh(ProfilerRefresh.paused);
    final ProfilerViewController second = ProfilerViewController(
      _FakeProfilerClient(),
      storage: storage,
    );
    addTearDown(second.dispose);
    expect(second.refresh, ProfilerRefresh.paused);
    expect(ProfilerRefresh.parse('nonsense'), ProfilerRefresh.s2);
  });

  test('a broken storage falls back to the default', () {
    final ProfilerViewController controller = ProfilerViewController(
      _FakeProfilerClient(),
      storage: _ThrowingStorage(),
    );
    addTearDown(controller.dispose);
    expect(controller.refresh, ProfilerRefresh.s2);
    controller.setRefresh(ProfilerRefresh.s5);
    expect(controller.refresh, ProfilerRefresh.s5);
  });

  test('a stopped profile is fetched again only when it moved', () async {
    final _FakeProfilerClient client = _FakeProfilerClient()
      ..status = _stopped(10);
    final ProfilerViewController controller = ProfilerViewController(client);
    addTearDown(controller.dispose);
    await controller.load();
    expect(
      client.calls,
      containsAll(<String>['owners', 'hot:', 'tree:', 'threads']),
    );
    client.calls.clear();
    await controller.load();
    expect(client.calls, <String>['status']);
    client.calls.clear();
    await controller.load(force: true);
    expect(client.calls, contains('tree:'));
    expect(controller.data!.tree.roots.single.method, 'Server thread');
  });

  test('an owner view asks for its own methods and tree', () async {
    final _FakeProfilerClient client = _FakeProfilerClient()
      ..status = _stopped(10);
    final ProfilerViewController controller = ProfilerViewController(
      client,
      owner: 'Iris',
    );
    addTearDown(controller.dispose);
    await controller.load();
    expect(client.calls, containsAll(<String>['hot:Iris', 'tree:Iris']));
    expect(client.calls, isNot(contains('threads')));
    expect(controller.threads!.single.name, 'Server thread');
  });

  test('no samples, no view requests', () async {
    final _FakeProfilerClient client = _FakeProfilerClient();
    final ProfilerViewController controller = ProfilerViewController(client);
    addTearDown(controller.dispose);
    await controller.load();
    expect(client.calls, <String>['status']);
    expect(controller.data!.tree.roots, isEmpty);
  });

  test('a start goes to the server and the view reloads', () async {
    final _FakeProfilerClient client = _FakeProfilerClient()
      ..status = _stopped(10);
    final ProfilerViewController controller = ProfilerViewController(client);
    addTearDown(controller.dispose);
    expect(
      await controller.command(const ProfilerStart(intervalMs: 5)),
      isTrue,
    );
    expect(client.lastCommand, isA<ProfilerStart>());
    expect(controller.status!.running, isTrue);
    await controller.load();
    expect(client.calls, contains('tree:'));
  });

  test('listening polls at the picked pace; paused does not', () async {
    final _FakeProfilerClient client = _FakeProfilerClient();
    final ProfilerViewController controller = ProfilerViewController(
      client,
      storage: InMemoryFleetStorage(),
    );
    addTearDown(controller.dispose);
    controller.setRefresh(ProfilerRefresh.paused);
    void listener() {}
    controller.addListener(listener);
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(client.calls, <String>['status']);
    await Future<void>.delayed(const Duration(milliseconds: 1100));
    expect(client.calls, <String>['status']);
    controller.setRefresh(ProfilerRefresh.s1);
    await Future<void>.delayed(const Duration(milliseconds: 1150));
    expect(client.calls.length, 2);
    controller.removeListener(listener);
    await Future<void>.delayed(const Duration(milliseconds: 1100));
    expect(client.calls.length, 2);
  });

  test('the idle switch asks every view again with idle samples', () async {
    final _FakeProfilerClient client = _FakeProfilerClient()
      ..status = _stopped(10);
    final ProfilerViewController controller = ProfilerViewController(client);
    addTearDown(controller.dispose);
    await controller.load();
    client.calls.clear();
    controller.setIdle(true);
    await controller.load();
    expect(controller.idle, isTrue);
    expect(client.calls, containsAll(<String>['owners:idle', 'tree::idle']));
  });

  test('a refused command is kept for the toast', () async {
    final _FakeProfilerClient client = _FakeProfilerClient()
      ..refusal = StateError('scope_missing');
    final ProfilerViewController controller = ProfilerViewController(client);
    addTearDown(controller.dispose);
    expect(await controller.command(const ProfilerStart()), isFalse);
    expect(controller.commandError, isA<StateError>());
    expect(controller.busy, isFalse);
  });
}

class _ThrowingStorage implements FleetStorage {
  @override
  String? read(String key) => throw StateError('blocked');

  @override
  void remove(String key) => throw StateError('blocked');

  @override
  void write(String key, String value) => throw StateError('blocked');
}
