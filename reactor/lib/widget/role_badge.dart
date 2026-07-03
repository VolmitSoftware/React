library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../model/role_info.dart';

class RoleBadge extends StatelessWidget {
  final RoleInfo? role;

  const RoleBadge({required this.role, super.key});

  @override
  Widget build(BuildContext context) {
    final RoleInfo? r = role;
    if (r == null) return Component.fragment(const <Widget>[]);
    return switch (r.role) {
      'admin' => const ArcaneStatusBadge.success('Admin'),
      'operator' => const ArcaneStatusBadge.info('Operator'),
      _ => const ArcaneStatusBadge.warning('Viewer'),
    };
  }
}
