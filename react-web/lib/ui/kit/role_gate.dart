/// What the paired token may do on this server. The server checks every
/// call; the gate only keeps controls a role cannot use out of the way.
/// While the role is still loading (or outside a server) the gate allows,
/// matching `readOnlyFor` and `adminGatedDisabled`.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../../model/role_info.dart';
import '../../state/role_scope.dart';

/// What a control needs.
enum RoleNeed {
  /// Runs actions, toggles features (`op:execute`).
  operator,

  /// Changes config, packs, players (`admin`).
  admin,

  /// Sends console commands (`console:execute`).
  console,
}

/// Whether [role] may use a control that needs [need]; true while unknown.
bool roleAllows(RoleInfo? role, RoleNeed need) {
  if (role == null) return true;
  return switch (need) {
    RoleNeed.operator => role.canExecute,
    RoleNeed.admin => role.isAdmin,
    RoleNeed.console => role.canExecuteConsole,
  };
}

/// [child] when the server's role allows [need], else [denied] (or nothing).
class RoleGate extends StatelessWidget {
  const RoleGate({
    required this.need,
    required this.child,
    this.denied,
    super.key,
  });

  final RoleNeed need;
  final Widget child;
  final Widget? denied;

  /// Whether the role in [context] allows [need].
  static bool allows(BuildContext context, RoleNeed need) =>
      roleAllows(RoleScope.of(context)?.role, need);

  @override
  Widget build(BuildContext context) => allows(context, need)
      ? child
      : (denied ?? const Component.fragment(<Widget>[]));
}
