library;

import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart';

import 'svg_fallback_chart.dart';

class TimeseriesChart extends StatefulComponent {
  const TimeseriesChart({required this.series, this.height = 160, super.key});

  final List<(String, List<double>)> series;
  final int height;

  @override
  State<TimeseriesChart> createState() => _TimeseriesChartState();
}

class _TimeseriesChartState extends State<TimeseriesChart> {
  final Set<int> _hiddenSeries = <int>{};
  int? _activeSample;

  @override
  Component build(BuildContext context) {
    final bool hasSamples = component.series.any(
      ((String, List<double>) item) => item.$2.isNotEmpty,
    );
    return dom.div(classes: 'reactor-chart${hasSamples ? '' : ' is-empty'}', <
      Component
    >[
      if (hasSamples)
        SvgFallbackChart(
          series: component.series,
          height: component.height,
          hiddenSeries: _hiddenSeries,
          activeSample: _activeSample,
          onSampleHover: (int? sample) =>
              setState(() => _activeSample = sample),
        )
      else
        dom.div(
          classes: 'reactor-chart-empty',
          styles: dom.Styles(
            raw: <String, String>{'height': '${component.height}px'},
          ),
          const <Component>[
            dom.strong(<Component>[Component.text('Awaiting samples')]),
            dom.span(<Component>[
              Component.text(
                'The chart will populate when the server publishes data.',
              ),
            ]),
          ],
        ),
      if (hasSamples && component.series.isNotEmpty)
        dom.div(classes: 'reactor-chart-legend', <Component>[
          for (int index = 0; index < component.series.length; index++)
            dom.button(
              classes:
                  'reactor-chart-legend-item${_hiddenSeries.contains(index) ? ' is-muted' : ''}',
              attributes: <String, String>{
                'type': 'button',
                'aria-pressed': (!_hiddenSeries.contains(index)).toString(),
                'title': _hiddenSeries.contains(index)
                    ? 'Show ${component.series[index].$1}'
                    : 'Hide ${component.series[index].$1}',
              },
              events: <String, EventCallback>{
                'click': (_) => _toggleSeries(index),
              },
              <Component>[
                dom.span(
                  classes:
                      'reactor-chart-legend-line '
                      'reactor-chart-series-${index % 6}',
                  const <Component>[],
                ),
                Component.text(component.series[index].$1),
              ],
            ),
        ]),
    ]);
  }

  void _toggleSeries(int index) {
    setState(() {
      if (!_hiddenSeries.remove(index)) {
        _hiddenSeries.add(index);
      }
      _activeSample = null;
    });
  }
}
