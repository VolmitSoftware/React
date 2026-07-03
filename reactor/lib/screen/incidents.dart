library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../chart/timeseries_chart.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class IncidentsScreen extends StatelessWidget {
  const IncidentsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? incidentScore =
        scope?.snapshot?.sampler('incident-score');
    final SamplerSample? backlogGrowthRate =
        scope?.snapshot?.sampler('backlog-growth-rate');
    final SamplerSample? schedulerBacklog =
        scope?.snapshot?.sampler('scheduler-backlog');

    return ReactorPage(
      title: 'Incidents',
      subtitle: 'Incident scoring and history',
      children: <Widget>[
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'flex',
            'justify-content': 'center',
            'padding': '1rem 0',
          }),
          <Widget>[
            Gauge(
              label: 'Incident Score',
              value: incidentScore?.value ?? 0.0,
              display: incidentScore?.display,
              max: 100.0,
              thresholds: (40.0, 70.0),
            ),
          ],
        ),
        sectionCard(
          label: 'Incident Timeline',
          child: TimeseriesChart(
            series: <(String, List<double>)>[
              ('Incident Score', incidentScore?.history ?? const <double>[]),
            ],
            height: 120,
          ),
        ),
        sectionCard(
          label: 'Backlog',
          child: TimeseriesChart(
            series: <(String, List<double>)>[
              (
                'Growth Rate',
                backlogGrowthRate?.history ?? const <double>[],
              ),
              (
                'Scheduler Backlog',
                schedulerBacklog?.history ?? const <double>[],
              ),
            ],
            height: 120,
          ),
        ),
        statGrid(<Widget>[
          StatTile(label: 'Incident Score', sample: incidentScore),
          StatTile(label: 'Backlog Growth', sample: backlogGrowthRate),
          StatTile(label: 'Scheduler Backlog', sample: schedulerBacklog),
        ]),
      ],
    );
  }
}
