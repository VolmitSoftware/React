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

class InternalsScreen extends StatelessWidget {
  const InternalsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);

    final SamplerSample? asyncTickTime =
        scope?.snapshot?.sampler('react-async-tick-time');
    final SamplerSample? syncTickTime =
        scope?.snapshot?.sampler('react-sync-tick-time');
    final SamplerSample? jobsQueue =
        scope?.snapshot?.sampler('react-jobs-queue');
    final SamplerSample? jobQueueTime =
        scope?.snapshot?.sampler('react-job-queue-time');
    final SamplerSample? jobBudget =
        scope?.snapshot?.sampler('react-job-budget');
    final SamplerSample? systemLoad =
        scope?.snapshot?.sampler('processor-system-load');
    final SamplerSample? processLoad =
        scope?.snapshot?.sampler('processor-process-load');
    final SamplerSample? outsideLoad =
        scope?.snapshot?.sampler('processor-outside-load');

    final List<(String, List<double>)> tickSeries =
        <(String, List<double>)>[
      ('Async', asyncTickTime?.history ?? const <double>[]),
      ('Sync', syncTickTime?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Internals',
      subtitle: 'Engine internals and job queues',
      children: <Widget>[
        sectionCard(
          label: 'React Tick Time',
          child: TimeseriesChart(series: tickSeries, height: 160),
        ),
        sectionCard(
          label: 'Jobs',
          child: statGrid(<Widget>[
            StatTile(label: 'Queue', sample: jobsQueue),
            StatTile(label: 'Queue Time', sample: jobQueueTime),
            StatTile(label: 'Budget', sample: jobBudget),
          ]),
        ),
        sectionCard(
          label: 'CPU Load',
          child: dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'align-items': 'center',
              'gap': '1rem',
            }),
            <Widget>[
              Gauge(
                label: 'Process Load',
                value: processLoad?.value ?? 0.0,
                display: processLoad?.display,
                max: 100.0,
                thresholds: (60.0, 85.0),
              ),
              statGrid(<Widget>[
                StatTile(label: 'System Load', sample: systemLoad),
                StatTile(label: 'Process Load', sample: processLoad),
                StatTile(label: 'Outside Load', sample: outsideLoad),
              ]),
            ],
          ),
        ),
      ],
    );
  }
}
