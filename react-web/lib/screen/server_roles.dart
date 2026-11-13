/// What this browser's token may do on each paired server, asked once per
/// server for the screen that shows it (the server table, Settings >
/// Servers).
library;

import 'dart:async' show unawaited;

import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/fleet_manager.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/page.dart';

/// Why a server's role is not known.
enum RoleProblem { pending, revoked, wrongServer, forbidden, unavailable }

class ServerRoles {
  ServerRoles(this._changed);

  /// Called when an answer arrives (the owner's `setState`).
  final void Function() _changed;
  final Map<String, RoleInfo> _roles = <String, RoleInfo>{};
  final Map<String, RoleProblem> _problems = <String, RoleProblem>{};
  final Set<String> _asked = <String>{};
  bool _closed = false;

  RoleInfo? roleOf(String id) => _roles[id];

  /// Null once the role is known.
  RoleProblem? problemOf(String id) =>
      _roles.containsKey(id) ? null : _problems[id] ?? RoleProblem.pending;

  /// Asks every server in [ids] not asked yet.
  void ask(FleetManager fleet, Iterable<String> ids) {
    for (final String id in ids) {
      if (!_asked.add(id)) continue;
      final IRoleClient? client = fleet.sessionFor(id)?.roles;
      if (client == null) {
        _problems[id] = RoleProblem.unavailable;
        continue;
      }
      unawaited(_fetch(id, client));
    }
  }

  /// Stops reporting answers (the owner is gone).
  void close() => _closed = true;

  Future<void> _fetch(String id, IRoleClient client) async {
    RoleInfo? role;
    RoleProblem? problem;
    try {
      role = await client.whoami();
    } on Object catch (error) {
      problem = switch (reactErrorKind(error)) {
        ReactErrorKind.revoked ||
        ReactErrorKind.authentication => RoleProblem.revoked,
        ReactErrorKind.wrongServer => RoleProblem.wrongServer,
        ReactErrorKind.forbidden => RoleProblem.forbidden,
        _ => RoleProblem.unavailable,
      };
    }
    if (_closed) return;
    if (role != null) {
      _roles[id] = role;
      _problems.remove(id);
    } else {
      _problems[id] = problem!;
    }
    _changed();
  }

  /// The role as a short word, or why it is missing.
  String label(String id) {
    final RoleInfo? role = _roles[id];
    if (role != null) return roleLabel(role);
    return switch (problemOf(id)!) {
      RoleProblem.pending => '--',
      RoleProblem.revoked => reactorText(ReactorText.pairEndedState),
      RoleProblem.wrongServer => reactorText(ReactorText.fleetWrongServer),
      RoleProblem.forbidden => reactorText(ReactorText.settingsNoAccess),
      RoleProblem.unavailable => reactorText(ReactorText.commonUnavailable),
    };
  }

  /// [label] as a badge: the role neutral, a problem in its colour.
  StatusBadge badge(String id) => StatusBadge(
    label(id),
    tone: switch (problemOf(id)) {
      null || RoleProblem.pending => BadgeTone.neutral,
      RoleProblem.revoked ||
      RoleProblem.wrongServer ||
      RoleProblem.forbidden => BadgeTone.error,
      RoleProblem.unavailable => BadgeTone.warning,
    },
  );
}
