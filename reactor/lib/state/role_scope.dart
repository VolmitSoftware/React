import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show InheritedComponent, PreloadStateMixin;

import '../model/role_info.dart';
import '../service/react_client.dart';

class RoleScope extends InheritedComponent {
  final RoleInfo? role;

  const RoleScope({
    required this.role,
    required super.child,
    super.key,
  });

  static RoleScope? of(BuildContext c) =>
      c.dependOnInheritedComponentOfExactType<RoleScope>();

  @override
  bool updateShouldNotify(RoleScope old) => role?.role != old.role?.role;
}

class RoleObserver extends StatefulWidget {
  final IRoleClient? client;
  final Widget child;

  const RoleObserver({
    required this.client,
    required this.child,
    super.key,
  });

  @override
  State<RoleObserver> createState() => _RoleObserverState();
}

class _RoleObserverState extends State<RoleObserver>
    with PreloadStateMixin<RoleObserver> {
  RoleInfo? _role;
  bool _started = false;

  @override
  Future<void> preloadState() async {
    final IRoleClient? c = component.client;
    if (c == null) return;
    try {
      _role = await c.whoami();
      _started = true;
    } on Object {
      return;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IRoleClient? c = component.client;
    if (c != null && !_started) {
      _started = true;
      _fetch(c);
    }
  }

  Future<void> _fetch(IRoleClient c) async {
    try {
      final RoleInfo info = await c.whoami();
      if (!mounted) return;
      setState(() => _role = info);
    } on Object {
      return;
    }
  }

  @override
  Widget build(BuildContext context) {
    return RoleScope(role: _role, child: component.child);
  }
}
