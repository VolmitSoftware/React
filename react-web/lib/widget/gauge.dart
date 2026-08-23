library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';

enum GaugeStatus { success, warning, error }

class Gauge extends StatelessWidget {
  final String label;
  final double value;
  final String? display;
  final double max;
  final (double, double) thresholds;
  final bool invertStatus;

  const Gauge({
    required this.label,
    required this.value,
    this.display,
    required this.max,
    required this.thresholds,
    this.invertStatus = false,
    super.key,
  });

  static GaugeStatus statusFor(double value, (double, double) thresholds) {
    if (value >= thresholds.$2) return GaugeStatus.error;
    if (value >= thresholds.$1) return GaugeStatus.warning;
    return GaugeStatus.success;
  }

  static String _cssColorForStatus(GaugeStatus status) => switch (status) {
    GaugeStatus.success => 'var(--success)',
    GaugeStatus.warning => 'var(--warning)',
    GaugeStatus.error => 'var(--destructive)',
  };

  static String _statusWord(GaugeStatus status) => switch (status) {
    GaugeStatus.success => reactorText(ReactorText.statusNominal),
    GaugeStatus.warning => reactorText(ReactorText.statusElevated),
    GaugeStatus.error => reactorText(ReactorText.statusCritical),
  };

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final GaugeStatus status = invertStatus
        ? statusFor(max - value, thresholds)
        : statusFor(value, thresholds);
    final String arcColor = _cssColorForStatus(status);
    final double fraction = max > 0.0 ? (value / max).clamp(0.0, 1.0) : 0.0;

    return dom.div(classes: 'reactor-gauge is-${status.name}', <Widget>[
      dom.div(classes: 'reactor-gauge-label', <Widget>[Component.text(label)]),
      dom.div(
        classes: 'reactor-gauge-value',
        styles: dom.Styles(raw: <String, String>{'color': arcColor}),
        <Widget>[Component.text(display ?? value.toStringAsFixed(1))],
      ),
      dom.div(
        classes: 'reactor-gauge-track',
        attributes: <String, String>{
          'role': 'meter',
          'aria-label': label,
          'aria-valuemin': '0',
          'aria-valuemax': max.toString(),
          'aria-valuenow': value.toString(),
        },
        <Widget>[
          dom.div(
            classes: 'reactor-gauge-fill',
            styles: dom.Styles(
              raw: <String, String>{
                'width': '${(fraction * 100).toStringAsFixed(2)}%',
                'background': arcColor,
              },
            ),
            const <Widget>[],
          ),
        ],
      ),
      if (status != GaugeStatus.success)
        dom.span(
          classes: 'reactor-gauge-status',
          styles: dom.Styles(raw: <String, String>{'color': arcColor}),
          <Widget>[Component.text(_statusWord(status))],
        )
      else
        const dom.span(<Widget>[]),
    ]);
  }
}
