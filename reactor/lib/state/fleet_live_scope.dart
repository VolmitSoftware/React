import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show InheritedComponent;

import 'fleet_live_model.dart';
import 'fleet_rollup.dart';

class FleetLiveScope extends InheritedComponent {
  final List<FleetServerLive> servers;
  final int revision;

  const FleetLiveScope({
    required this.servers,
    required this.revision,
    required super.child,
    super.key,
  });

  static FleetLiveScope? of(BuildContext c) =>
      c.dependOnInheritedComponentOfExactType<FleetLiveScope>();

  @override
  bool updateShouldNotify(FleetLiveScope old) => revision != old.revision;
}

class FleetLiveObserver extends StatefulWidget {
  final List<FleetLiveSource> sources;
  final Widget child;

  const FleetLiveObserver({
    required this.sources,
    required this.child,
    super.key,
  });

  @override
  State<FleetLiveObserver> createState() => _FleetLiveObserverState();
}

class _FleetLiveObserverState extends State<FleetLiveObserver> {
  late FleetLiveModel _model;
  int _revision = 0;

  @override
  void initState() {
    super.initState();
    _model = FleetLiveModel(
      component.sources,
      onChange: () => setState(() => _revision++),
    );
  }

  @override
  void didUpdateComponent(FleetLiveObserver oldComponent) {
    super.didUpdateComponent(oldComponent);
    if (oldComponent.sources != component.sources) {
      _model.update(component.sources);
    }
  }

  @override
  void dispose() {
    _model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FleetLiveScope(
      servers: _model.servers,
      revision: _revision,
      child: component.child,
    );
  }
}
