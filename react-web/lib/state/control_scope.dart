import 'package:jaspr/jaspr.dart';

import '../service/react_client.dart';

class ControlScope extends InheritedComponent {
  final IControlClient? client;

  const ControlScope({required this.client, required super.child, super.key});

  static ControlScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<ControlScope>();

  @override
  bool updateShouldNotify(ControlScope old) => client != old.client;
}
