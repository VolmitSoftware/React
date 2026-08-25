import 'package:jaspr/jaspr.dart';

import '../service/react_client.dart';

class PlayerScope extends InheritedComponent {
  final IPlayerClient? client;

  const PlayerScope({required this.client, required super.child, super.key});

  static PlayerScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<PlayerScope>();

  @override
  bool updateShouldNotify(PlayerScope old) => client != old.client;
}
