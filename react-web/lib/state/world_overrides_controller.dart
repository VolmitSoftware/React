import 'dart:async';

import '../model/server_revisions.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import 'live_controller.dart';

/// A world budget typed by the user but not sent yet, with the server values
/// it was based on.
class StagedBudget {
  final double? budgetMs;
  final double? panicMs;
  final double? releaseMs;
  final WorldSettings base;

  const StagedBudget({
    required this.base,
    this.budgetMs,
    this.panicMs,
    this.releaseMs,
  });

  StagedBudget merge({double? budgetMs, double? panicMs, double? releaseMs}) =>
      StagedBudget(
        base: base,
        budgetMs: budgetMs ?? this.budgetMs,
        panicMs: panicMs ?? this.panicMs,
        releaseMs: releaseMs ?? this.releaseMs,
      );
}

/// Per-world tick budgets of one server. Typed values are staged with
/// [stageBudget] and sent by [commitBudget], never per keystroke.
class WorldOverridesController extends LiveController {
  final IControlClient _client;

  List<WorldSettings> _server = const <WorldSettings>[];
  List<WorldSettings>? _view;
  final Map<String, StagedBudget> _staged = <String, StagedBudget>{};
  final Set<String> _changedOnServer = <String>{};
  final Set<String> _writesInFlight = <String>{};

  WorldOverridesController(
    IControlClient client, {
    super.serverId,
    super.revisions,
    super.refetchDelay,
  }) : _client = client,
       super(kind: RevisionKind.worlds);

  /// Worlds as the user sees them: server values with staged edits on top.
  List<WorldSettings> get worlds => _view ??= List<WorldSettings>.unmodifiable(
    _server.map((WorldSettings world) {
      final StagedBudget? staged = _staged[world.name];
      return staged == null
          ? world
          : world.copyWith(
              budgetMs: staged.budgetMs,
              panicMs: staged.panicMs,
              releaseMs: staged.releaseMs,
            );
    }),
  );

  bool get hasPendingEdits => _staged.isNotEmpty;

  bool hasStaged(String name) => _staged.containsKey(name);

  /// True when the server changed a world the user is still editing.
  bool changedOnServer(String name) => _changedOnServer.contains(name);

  WorldSettings? _serverWorld(String name) {
    for (final WorldSettings world in _server) {
      if (world.name == name) return world;
    }
    return null;
  }

  void _setServer(List<WorldSettings> next) {
    _server = List<WorldSettings>.unmodifiable(next);
    _view = null;
  }

  void _replace(String name, WorldSettings next) {
    _setServer(<WorldSettings>[
      for (final WorldSettings world in _server)
        world.name == name ? next : world,
    ]);
  }

  @override
  Future<void> fetch() async {
    final List<WorldSettings> fresh = await _client.worlds();
    if (disposed) return;
    _setServer(<WorldSettings>[
      for (final WorldSettings world in fresh)
        _writesInFlight.contains(world.name)
            ? (_serverWorld(world.name) ?? world)
            : world,
    ]);
    for (final MapEntry<String, StagedBudget> entry in _staged.entries) {
      final WorldSettings? now = _serverWorld(entry.key);
      final WorldSettings base = entry.value.base;
      if (now == null ||
          now.budgetMs != base.budgetMs ||
          now.panicMs != base.panicMs ||
          now.releaseMs != base.releaseMs) {
        _changedOnServer.add(entry.key);
      }
    }
  }

  void stageBudget(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) {
    final WorldSettings? world = _serverWorld(name);
    if (world == null || disposed) return;
    final StagedBudget current = _staged[name] ?? StagedBudget(base: world);
    _staged[name] = current.merge(
      budgetMs: budgetMs,
      panicMs: panicMs,
      releaseMs: releaseMs,
    );
    _view = null;
    notify();
  }

  Future<Object?> commitBudget(String name) async {
    final StagedBudget? staged = _staged.remove(name);
    if (staged == null) return null;
    _changedOnServer.remove(name);
    _view = null;
    return setBudget(
      name,
      budgetMs: staged.budgetMs,
      panicMs: staged.panicMs,
      releaseMs: staged.releaseMs,
    );
  }

  void discardBudget(String name) {
    if (_staged.remove(name) == null) return;
    _changedOnServer.remove(name);
    _view = null;
    notify();
  }

  /// Sends at once with an optimistic update; rolls back on failure.
  Future<Object?> setBudget(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  }) async {
    final WorldSettings? previous = _serverWorld(name);
    if (previous == null || disposed) return null;
    _replace(
      name,
      previous.copyWith(
        budgetMs: budgetMs,
        panicMs: panicMs,
        releaseMs: releaseMs,
      ),
    );
    notify();
    _writesInFlight.add(name);
    try {
      ensureOpen();
      final WorldSettings result = await _client.setWorld(
        name,
        budgetMs: budgetMs,
        panicMs: panicMs,
        releaseMs: releaseMs,
      );
      _replace(name, result);
      return null;
    } on Object catch (e) {
      _replace(name, previous);
      return e;
    } finally {
      _writesInFlight.remove(name);
      notify();
    }
  }
}
