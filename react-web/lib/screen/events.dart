/// Performance > Events (Advanced): event dispatch cost. Time and handle
/// counts are different units, so each has its own chart.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

class EventsScreen extends StatelessWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final SamplerSample? handles = snapshot.sampler('event-handles-per-tick');
    final SamplerSample? eventTime = snapshot.sampler('event-time');
    final String handlesLabel = reactorText(ReactorText.eventsHandlesPerTick);
    final String timeLabel = reactorText(ReactorText.commonEventTime);

    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            samplerTile(timeLabel, eventTime),
            samplerTile(handlesLabel, handles),
            samplerTile(
              reactorText(ReactorText.eventsListeners),
              snapshot.sampler('events-listeners'),
            ),
          ],
        ),
      ],
    );
  }
}
