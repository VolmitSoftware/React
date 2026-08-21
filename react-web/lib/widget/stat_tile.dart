library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/sampler_sample.dart';
import '../ui/reactor_ui.dart';

class StatTile extends StatelessWidget {
  final String label;
  final SamplerSample? sample;
  final ReactorStatus status;

  const StatTile({
    required this.label,
    required this.sample,
    this.status = ReactorStatus.neutral,
    super.key,
  });

  static String formatValue(SamplerSample? sample) {
    if (sample == null) return '--';
    return sample.display;
  }

  @override
  Widget build(BuildContext context) {
    final SamplerSample? s = sample;
    final String displayValue = formatValue(s);
    final String suffix = s?.suffix ?? '';
    final List<double> history = s?.history ?? <double>[];
    final String sparkColor = status == ReactorStatus.neutral
        ? 'color-mix(in srgb, var(--muted-foreground) 70%, transparent)'
        : reactorStatusColor(status);

    return dom.section(
      classes: 'reactor-metric-card reactor-stat-cell is-${status.name}',
      <Widget>[
        dom.div(classes: 'reactor-stat-cell-heading', <Widget>[
          reactorEyebrow(label),
          if (status != ReactorStatus.neutral)
            reactorStatusDot(status, size: 7.0, label: status.name),
        ]),
        dom.div(classes: 'reactor-stat-reading', <Widget>[
          dom.span(classes: 'reactor-stat-value', <Widget>[
            Component.text(displayValue),
          ]),
          if (suffix.isNotEmpty)
            dom.span(classes: 'reactor-stat-unit', <Widget>[
              Component.text(suffix),
            ]),
        ]),
        if (history.length >= 2)
          ReactorSparkline(values: history, color: sparkColor, height: 28),
      ],
    );
  }
}
