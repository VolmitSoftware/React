library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/server_snapshot_state.dart';
import '../widget/stat_tile.dart';

class IncidentsScreen extends StatelessWidget {
  const IncidentsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.incidentsTitle),
      subtitle: reactorText(ReactorText.incidentsSubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? incidentScore = snapshot.sampler('incident-score');
    final SamplerSample? backlogGrowthRate = snapshot.sampler(
      'backlog-growth-rate',
    );
    final SamplerSample? schedulerBacklog = snapshot.sampler(
      'scheduler-backlog',
    );

    return ReactorPage(
      title: reactorText(ReactorText.incidentsTitle),
      subtitle: reactorText(ReactorText.incidentsSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.commonIncidentTimeline),
          children: <Widget>[
            if (incidentScore == null)
              StatTile(
                label: reactorText(ReactorText.commonIncidentScore),
                sample: null,
              )
            else
              Gauge(
                label: reactorText(ReactorText.commonIncidentScore),
                value: incidentScore.value,
                display: incidentScore.display,
                max: 100.0,
                thresholds: (40.0, 70.0),
              ),
            TimeseriesChart(
              series: <(String, List<double>)>[
                (
                  reactorText(ReactorText.commonIncidentScore),
                  incidentScore?.history ?? const <double>[],
                ),
              ],
              height: 120,
            ),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.incidentsBacklog),
          children: <Widget>[
            TimeseriesChart(
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
            statGrid(<Widget>[
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
        ),
      ],
    );
  }
}
