library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../localization/reactor_localizations.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/stat_tile.dart';

class OverviewScreen extends StatelessWidget {
  const OverviewScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) {
      return ReactorPage(
        title: reactorText(ReactorText.overviewTitle),
        subtitle: reactorText(ReactorText.overviewSubtitle),
        children: <Widget>[
          ReactorLoadingState(label: reactorText(ReactorText.metricsWaiting)),
        ],
      );
    }

    final SamplerSample? tps = snapshot.sampler('ticks-per-second');
    final SamplerSample? incidentScore = snapshot.sampler('incident-score');
    final SamplerSample? tickTime = snapshot.sampler('tick-time');
    final SamplerSample? players = snapshot.sampler('players');
    final SamplerSample? entities = snapshot.sampler('entities');
    final SamplerSample? chunks = snapshot.sampler('chunks');
    final SamplerSample? memoryUsed = snapshot.sampler('memory-used');
    final SamplerSample? gcTimePercent = snapshot.sampler('gc-time-percent');

    return ReactorPage(
      title: reactorText(ReactorText.overviewTitle),
      subtitle: reactorText(ReactorText.overviewSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.overviewVitals),
          flush: true,
          child: reactorGrid(
            children: <Widget>[
              Gauge(
                label: reactorText(ReactorText.overviewTps),
                value: tps?.value ?? 0.0,
                display: tps?.display ?? '--',
                max: 20.0,
                thresholds: (5.0, 10.0),
                invertStatus: true,
              ),
              Gauge(
                label: reactorText(ReactorText.commonIncidentScore),
                value: incidentScore?.value ?? 0.0,
                display: incidentScore?.display ?? '--',
                max: 100.0,
                thresholds: (40.0, 70.0),
              ),
              Gauge(
                label: reactorText(ReactorText.commonTickTime),
                value: tickTime?.value ?? 0.0,
                display: tickTime?.display ?? '--',
                max: 50.0,
                thresholds: (10.0, 16.7),
              ),
              StatTile(
                label: reactorText(ReactorText.commonPlayers),
                sample: players,
              ),
              StatTile(
                label: reactorText(ReactorText.commonEntities),
                sample: entities,
              ),
              StatTile(
                label: reactorText(ReactorText.commonChunks),
                sample: chunks,
              ),
              StatTile(
                label: reactorText(ReactorText.commonMemoryUsed),
                sample: memoryUsed,
              ),
              StatTile(
                label: reactorText(ReactorText.commonGcTime),
                sample: gcTimePercent,
              ),
            ],
          ),
        ),
        _IncidentStrip(incidentScore: incidentScore),
      ],
    );
  }
}

class _IncidentStrip extends StatelessWidget {
  final SamplerSample? incidentScore;

  const _IncidentStrip({required this.incidentScore});

  @override
  Widget build(BuildContext context) {
    final SamplerSample? score = incidentScore;
    if (score == null) {
      return SectionPanel(
        label: reactorText(ReactorText.overviewIncidentPressure),
        child: ReactorLoadingState(
          label: reactorText(ReactorText.metricsWaiting),
        ),
      );
    }

    final List<double> history = score.history;
    final double currentScore = score.value;
    final GaugeStatus gStatus = Gauge.statusFor(currentScore, (40.0, 70.0));
    final ReactorStatus status = switch (gStatus) {
      GaugeStatus.success => ReactorStatus.healthy,
      GaugeStatus.warning => ReactorStatus.warning,
      GaugeStatus.error => ReactorStatus.critical,
    };
    final String statusLabel = switch (gStatus) {
      GaugeStatus.success => reactorText(ReactorText.statusNormal),
      GaugeStatus.warning => reactorText(ReactorText.statusElevated),
      GaugeStatus.error => reactorText(ReactorText.statusCritical),
    };
    final String barColor = reactorStatusColor(status);

    final Widget body = history.isEmpty
        ? ReactorLoadingState(
            label: reactorText(ReactorText.overviewNoIncidentHistory),
          )
        : dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'gap': '3px',
                'height': '56px',
                'align-items': 'flex-end',
              },
            ),
            <Widget>[
              for (final double v in history)
                _IncidentBar(value: v, max: 100.0, color: barColor),
            ],
          );

    return SectionPanel(
      label: reactorText(ReactorText.overviewIncidentPressure),
      trailing: reactorBadge(statusLabel, status),
      child: body,
    );
  }
}

class _IncidentBar extends StatelessWidget {
  final double value;
  final double max;
  final String color;

  const _IncidentBar({
    required this.value,
    required this.max,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final double fraction = max > 0.0 ? (value / max).clamp(0.0, 1.0) : 0.0;
    final int heightPct = (fraction * 100).round().clamp(5, 100);

    return dom.div(
      styles: dom.Styles(
        raw: <String, String>{
          'flex': '1',
          'min-width': '3px',
          'height': '$heightPct%',
          'background': color,
          'border-radius': '0',
          'transition': 'height 200ms ease',
        },
      ),
      const <Widget>[],
    );
  }
}
