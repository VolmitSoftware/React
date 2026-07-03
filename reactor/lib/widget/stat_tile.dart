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

    return Card.flat(
      fillWidth: true,
      padding: '0',
      borderRadius: kReactorRadius,
      child: dom.div(
        classes: 'reactor-metric-card',
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '0.6rem',
            'padding': '0.95rem 1rem',
            'overflow': 'hidden',
            'border-radius': kReactorRadius,
          },
        ),
        <Widget>[
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'justify-content': 'space-between',
                'gap': '0.5rem',
              },
            ),
            <Widget>[
              reactorEyebrow(label),
              if (status != ReactorStatus.neutral)
                reactorStatusDot(status, size: 7.0),
            ],
          ),
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'baseline',
                'gap': '0.25rem',
              },
            ),
            <Widget>[
              dom.span(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'font-size': '1.7rem',
                    'font-weight': '700',
                    'color': kReactorFg,
                    'line-height': '1',
                    'letter-spacing': '0',
                    'font-variant-numeric': 'tabular-nums',
                  },
                ),
                <Widget>[Component.text(displayValue)],
              ),
              if (suffix.isNotEmpty)
                dom.span(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '0.85rem',
                      'color': kReactorMuted,
                    },
                  ),
                  <Widget>[Component.text(suffix)],
                ),
            ],
          ),
          if (history.length >= 2)
            ReactorSparkline(values: history, color: sparkColor, height: 34),
        ],
      ),
    );
  }
}
