import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/server_revisions.dart';
import '../service/react_exceptions.dart';
import 'revision_feed.dart';

/// Shared behaviour of the per-server data controllers: bound to one server
/// for life, refetch once (debounced) when the server bumps the controller's
/// revision kind, and notify listeners on every change. Screens listen with
/// [addListener] (a `ListenableBuilder`), read [error] for the last load
/// and the futures returned by writes for their outcome.
abstract class LiveController extends ChangeNotifier {
  /// The server every read and write of this controller goes to. Never
  /// changes; a different server has a different controller.
  final String serverId;
  final RevisionKind kind;
  final Duration refetchDelay;

  final RevisionFeed _revisions;
  StreamSubscription<RevisionKind>? _revisionSub;
  Timer? _refetchTimer;
  Future<void>? _inFlight;
  bool _refetchAgain = false;
  bool _disposed = false;
  bool _loading = false;
  bool _loadedOnce = false;
  Object? _error;
  int? _loadedAt;

  LiveController({
    required this.kind,
    String? serverId,
    RevisionFeed? revisions,
    this.refetchDelay = const Duration(milliseconds: 250),
  }) : serverId = serverId ?? '',
       _revisions = revisions ?? RevisionFeed.none() {
    _revisionSub = _revisions.changes.listen((RevisionKind changed) {
      if (changed == kind) _onRevision();
    });
  }

  bool get loading => _loading;
  Object? get error => _error;
  bool get disposed => _disposed;

  /// Revision of [kind] the visible data was loaded at; null on old plugins.
  int? get loadedRevision => _loadedAt;

  /// Fetches from the server. Subclasses merge the result with local edits.
  Future<void> fetch();

  /// Loads once if nothing was loaded yet; safe to call from `build`.
  void ensureLoaded() {
    if (_disposed || _loadedOnce || _inFlight != null || _error != null) {
      return;
    }
    scheduleMicrotask(() {
      if (!_loadedOnce && _inFlight == null) unawaited(load());
    });
  }

  /// The first listener (a `ListenableBuilder` in a screen) triggers the
  /// first load, so screens never need State to start a controller.
  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    ensureLoaded();
  }

  /// Loads now. Concurrent calls share one request.
  Future<void> load() {
    final Future<void>? running = _inFlight;
    if (running != null) return running;
    final Future<void> pending = _runLoad();
    _inFlight = pending;
    return pending;
  }

  Future<void> _runLoad() async {
    if (_disposed) return;
    _loading = true;
    _loadedAt = _revisions[kind];
    notify();
    try {
      await fetch();
      _error = null;
      _loadedOnce = true;
    } on Object catch (e) {
      _error = e;
    } finally {
      _loading = false;
      _inFlight = null;
      notify();
      if (_refetchAgain && !_disposed) {
        _refetchAgain = false;
        _scheduleRefetch();
      }
    }
  }

  void _onRevision() {
    if (_disposed || !_loadedOnce) return;
    if (_revisions[kind] == _loadedAt) return;
    if (_inFlight != null) {
      _refetchAgain = true;
      return;
    }
    _scheduleRefetch();
  }

  void _scheduleRefetch() {
    _refetchTimer?.cancel();
    _refetchTimer = Timer(refetchDelay, () {
      _refetchTimer = null;
      if (_disposed || _revisions[kind] == _loadedAt) return;
      unawaited(load());
    });
  }

  void notify() {
    if (_disposed) return;
    notifyListeners();
  }

  void setError(Object? error) => _error = error;

  /// Guards a write: a disposed controller (its server was removed) never
  /// sends anything.
  void ensureOpen() {
    if (_disposed) throw const ReactUnavailable('Server removed');
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _refetchTimer?.cancel();
    _refetchTimer = null;
    _revisionSub?.cancel();
    _revisionSub = null;
    super.dispose();
  }
}
