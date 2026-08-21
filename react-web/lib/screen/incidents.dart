library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
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
    final SamplerSample? incidentScore = scope?.snapshot?.sampler(
      'incident-score',
    );
    final SamplerSample? backlogGrowthRate = scope?.snapshot?.sampler(
      'backlog-growth-rate',
    );
    final SamplerSample? schedulerBacklog = scope?.snapshot?.sampler(
      'scheduler-backlog',
    );

    return ReactorPage(
      title: reactorText(ReactorText.incidentsTitle),
      subtitle: reactorText(ReactorText.incidentsSubtitle),
      children: <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'justify-content': 'center',
              'padding': '1rem 0',
            },
          ),
          <Widget>[
            Gauge(
              label: reactorText(ReactorText.commonIncidentScore),
              value: incidentScore?.value ?? 0.0,
              display: incidentScore?.display,
              max: 100.0,
              thresholds: (40.0, 70.0),
            ),
          ],
        ),
        sectionCard(
          label: reactorText(ReactorText.commonIncidentTimeline),
          child: TimeseriesChart(
            series: <(String, List<double>)>[
              (
                reactorText(ReactorText.commonIncidentScore),
                incidentScore?.history ?? const <double>[],
              ),
            ],
            height: 120,
          ),
        ),
        sectionCard(
          label: reactorText(ReactorText.incidentsBacklog),
          child: TimeseriesChart(
            series: <(String, List<double>)>[
              (
                reactorText(ReactorText.commonGrowthRate),
                backlogGrowthRate?.history ?? const <double>[],
              ),
              (
                reactorText(ReactorText.commonSchedulerBacklog),
                schedulerBacklog?.history ?? const <double>[],
              ),
            ],
            height: 120,
          ),
        ),
        statGrid(<Widget>[
          StatTile(
            label: reactorText(ReactorText.commonIncidentScore),
            sample: incidentScore,
          ),
          StatTile(
            label: reactorText(ReactorText.incidentsBacklogGrowth),
            sample: backlogGrowthRate,
          ),
          StatTile(
            label: reactorText(ReactorText.commonSchedulerBacklog),
            sample: schedulerBacklog,
          ),
        ]),
      ],
    );
  }
}
