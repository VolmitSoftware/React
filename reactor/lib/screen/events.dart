library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class EventsScreen extends StatelessWidget {
  const EventsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? eventHandles =
        scope?.snapshot?.sampler('event-handles-per-tick');
    final SamplerSample? eventListeners =
        scope?.snapshot?.sampler('events-listeners');
    final SamplerSample? eventTime =
        scope?.snapshot?.sampler('event-time');

    final List<(String, List<double>)> eventTimeSeries =
        <(String, List<double>)>[
      ('Event Time', eventTime?.history ?? const <double>[]),
      ('Handles/tick', eventHandles?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Events',
      subtitle: 'Event dispatch and listeners',
      children: <Widget>[
        sectionCard(
          label: 'Event Time',
          child: TimeseriesChart(series: eventTimeSeries, height: 160),
        ),
        statGrid(<Widget>[
          StatTile(label: 'Handles/Tick', sample: eventHandles),
          StatTile(label: 'Listeners', sample: eventListeners),
          StatTile(label: 'Event Time', sample: eventTime),
        ]),
      ],
    );
  }
}
