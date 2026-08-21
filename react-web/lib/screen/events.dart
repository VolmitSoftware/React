library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/server_snapshot_state.dart';
import '../widget/stat_tile.dart';

class EventsScreen extends StatelessWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.eventsTitle),
      subtitle: reactorText(ReactorText.eventsSubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? eventHandles = snapshot.sampler(
      'event-handles-per-tick',
    );
    final SamplerSample? eventListeners = snapshot.sampler('events-listeners');
    final SamplerSample? eventTime = snapshot.sampler('event-time');

    final List<(String, List<double>)> eventTimeSeries =
        <(String, List<double>)>[
          (
            reactorText(ReactorText.commonEventTime),
            eventTime?.history ?? const <double>[],
          ),
          (
            reactorText(ReactorText.eventsSeriesHandlesPerTick),
            eventHandles?.history ?? const <double>[],
          ),
        ];

    return ReactorPage(
      title: reactorText(ReactorText.eventsTitle),
      subtitle: reactorText(ReactorText.eventsSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.commonEventTime),
          children: <Widget>[
            TimeseriesChart(series: eventTimeSeries, height: 160),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.eventsHandlesPerTick),
                sample: eventHandles,
              ),
              StatTile(
                label: reactorText(ReactorText.eventsListeners),
                sample: eventListeners,
              ),
              StatTile(
                label: reactorText(ReactorText.commonEventTime),
                sample: eventTime,
              ),
            ]),
          ],
        ),
      ],
    );
  }
}
