import 'package:jaspr/jaspr.dart';

import '../service/react_client.dart';

class HeatmapScope extends InheritedComponent {
  final IHeatmapClient? client;

  const HeatmapScope({required this.client, required super.child, super.key});

  static HeatmapScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<HeatmapScope>();

  @override
  bool updateShouldNotify(HeatmapScope old) => client != old.client;
}
