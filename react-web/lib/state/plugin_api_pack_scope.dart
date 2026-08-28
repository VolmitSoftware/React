import 'package:jaspr/jaspr.dart';

import '../service/react_client.dart';

class PluginApiPackScope extends InheritedComponent {
  final IPluginApiPackClient? client;

  const PluginApiPackScope({
    required this.client,
    required super.child,
    super.key,
  });

  static PluginApiPackScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<PluginApiPackScope>();

  @override
  bool updateShouldNotify(PluginApiPackScope old) => client != old.client;
}
