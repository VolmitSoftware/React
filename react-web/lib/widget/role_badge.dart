library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../model/role_info.dart';
import '../localization/reactor_localizations.dart';
import '../ui/reactor_ui.dart';

class RoleBadge extends StatelessWidget {
  final RoleInfo? role;

  const RoleBadge({required this.role, super.key});

  @override
  Widget build(BuildContext context) {
    final RoleInfo? r = role;
    if (r == null) return Component.fragment(const <Widget>[]);
    return switch (r.role) {
      'admin' => reactorBadge(
        reactorText(ReactorText.roleAdmin),
        ReactorStatus.healthy,
      ),
      'operator' => reactorBadge(
        reactorText(ReactorText.roleOperator),
        ReactorStatus.info,
      ),
      _ => reactorBadge(
        reactorText(ReactorText.roleViewer),
        ReactorStatus.warning,
      ),
    };
  }
}
