import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/profiler.dart';
import '../service/react_client.dart';
import 'fleet_manager.dart';

/// How often the live profiler view asks again.
enum ProfilerRefresh {
  paused(0),
  s1(1),
  s2(2),
  s5(5),
  s10(10);

  const ProfilerRefresh(this.seconds);

  final int seconds;

  static ProfilerRefresh parse(String? value) {
    for (final ProfilerRefresh refresh in values) {
      if (refresh.name == value) return refresh;
    }
    return ProfilerRefresh.s2;
  }
}

/// Where the viewer's refresh pick is kept, per browser.
const String profilerRefreshStorageKey = 'reactor.profiler.refresh';

ProfilerRefresh readProfilerRefresh(FleetStorage? storage) {
  try {
    return ProfilerRefresh.parse(storage?.read(profilerRefreshStorageKey));
  } on Object {
    return ProfilerRefresh.s2;
  }
}

void writeProfilerRefresh(FleetStorage? storage, ProfilerRefresh refresh) {
  try {
    storage?.write(profilerRefreshStorageKey, refresh.name);
  } on Object {
    // Private mode or storage switched off: the pick lasts this page.
  }
}

/// The live profiler for one view: the whole server, or one [owner] (its
/// hot methods, its pruned call tree and the threads that tree runs on).
/// Polls while something listens and [refresh] is not paused; a stopped
/// profiler is fetched again only when its sample count moved.
class ProfilerViewController extends ChangeNotifier {
  ProfilerViewController(this._client, {this.owner, FleetStorage? storage})
    : _storage = storage,
      _refresh = readProfilerRefresh(storage);

  final IProfilerClient _client;
  final FleetStorage? _storage;

  /// The owner this view narrows to; null for the whole server.
  final String? owner;

  ProfilerRefresh _refresh;
  bool _idle = false;
  ProfilerStatus? _status;
  ProfilerOwners? _owners;
  ProfilerHot? _hot;
  ProfilerTree? _tree;
  List<ProfilerThread>? _threads;
  Object? _error;
  Object? _commandError;
  bool _loading = false;
  bool _busy = false;
  bool _disposed = false;
  int _listeners = 0;
  int _loadedSamples = -1;
  Future<void>? _inFlight;
  Timer? _timer;
  DateTime? _updatedAt;

  ProfilerRefresh get refresh => _refresh;

  /// Whether samples of parked, waiting and sleeping threads are kept.
  bool get idle => _idle;
  ProfilerStatus? get status => _status;
  ProfilerOwners? get owners => _owners;
  ProfilerHot? get hot => _hot;
  ProfilerTree? get tree => _tree;
  List<ProfilerThread>? get threads => _threads;

  /// The last failed load; cleared by the next good one.
  Object? get error => _error;

  /// The last failed start, stop or reset.
  Object? get commandError => _commandError;
  bool get loading => _loading;

  /// A start, stop or reset is on its way.
  bool get busy => _busy;
  DateTime? get updatedAt => _updatedAt;

  /// Everything loaded, as one profile (what a snapshot would freeze).
  ProfileData? get data {
    final ProfilerStatus? status = _status;
    final ProfilerTree? tree = _tree;
    if (status == null || tree == null) return null;
    return ProfileData(
      status: status,
      owners: _owners ?? ProfilerOwners.empty,
      hot: _hot ?? ProfilerHot.empty,
      tree: tree,
      threads: _threads ?? profilerThreadsOf(tree),
    );
  }

  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    if (++_listeners == 1) {
      unawaited(load());
      _schedule();
    }
  }

  @override
  void removeListener(void Function() listener) {
    super.removeListener(listener);
    if (_listeners > 0 && --_listeners == 0) {
      _timer?.cancel();
      _timer = null;
    }
  }

  void setRefresh(ProfilerRefresh refresh) {
    if (refresh == _refresh) return;
    _refresh = refresh;
    writeProfilerRefresh(_storage, refresh);
    _schedule();
    _notify();
  }

  void setIdle(bool idle) {
    if (idle == _idle) return;
    _idle = idle;
    _notify();
    unawaited(load(force: true));
  }

  void _schedule() {
    _timer?.cancel();
    _timer = null;
    if (_disposed || _listeners == 0 || _refresh == ProfilerRefresh.paused) {
      return;
    }
    _timer = Timer.periodic(
      Duration(seconds: _refresh.seconds),
      (Timer _) => unawaited(load()),
    );
  }

  /// Loads now; [force] refetches the views even when nothing moved.
  /// Calls while one is running share it.
  Future<void> load({bool force = false}) {
    final Future<void>? running = _inFlight;
    if (running != null) {
      return force ? running.then((void _) => load(force: true)) : running;
    }
    final Future<void> pending = _load(force)
        .whenComplete(() => _inFlight = null);
    _inFlight = pending;
    return pending;
  }

  Future<void> _load(bool force) async {
    if (_disposed) return;
    _loading = true;
    _notify();
    try {
      final ProfilerStatus status = await _client.profiler();
      if (_disposed) return;
      _status = status;
      if (force || status.running || status.samples != _loadedSamples) {
        await _loadViews(status);
      }
      _error = null;
      _updatedAt = DateTime.now();
    } on Object catch (error) {
      _error = error;
    } finally {
      _loading = false;
      _notify();
    }
  }

  Future<void> _loadViews(ProfilerStatus status) async {
    if (status.samples == 0) {
      _owners = ProfilerOwners.empty;
      _hot = ProfilerHot.empty;
      _tree = ProfilerTree.empty;
      _threads = const <ProfilerThread>[];
      _loadedSamples = 0;
      return;
    }
    final String? only = owner;
    final bool idle = _idle;
    final Future<ProfilerOwners> owners = _client.profilerOwners(idle: idle);
    final Future<ProfilerHot> hot = _client.profilerHot(
      owner: only,
      idle: idle,
    );
    final Future<ProfilerTree> tree = _client.profilerTree(
      owner: only,
      minPercent: only == null ? 0.5 : 0.1,
      idle: idle,
    );
    final Future<List<ProfilerThread>>? threads = only == null
        ? _client.profilerThreads(idle: idle)
        : null;
    final ProfilerTree loadedTree = await tree;
    _owners = await owners;
    _hot = await hot;
    _tree = loadedTree;
    _threads = threads == null ? profilerThreadsOf(loadedTree) : await threads;
    _loadedSamples = status.samples;
  }

  /// Starts, stops or resets; true when the server took it.
  Future<bool> command(ProfilerCommand command) async {
    if (_busy || _disposed) return false;
    _busy = true;
    _commandError = null;
    _notify();
    try {
      _status = await _client.controlProfiler(command);
      _busy = false;
      unawaited(load(force: true));
      return true;
    } on Object catch (error) {
      _commandError = error;
      return false;
    } finally {
      _busy = false;
      _notify();
    }
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _timer = null;
    super.dispose();
  }
}
