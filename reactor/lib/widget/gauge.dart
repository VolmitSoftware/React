library;

import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

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
    GaugeStatus.success => 'Nominal',
    GaugeStatus.warning => 'Elevated',
    GaugeStatus.error => 'Critical',
  };

  static String _buildArcD({
    required double fraction,
    required double cx,
    required double cy,
    required double r,
  }) {
    final double startAngle = -math.pi * 0.75;
    final double sweepAngle = math.pi * 1.5 * fraction.clamp(0.0, 1.0);
    final double endAngle = startAngle + sweepAngle;
    final double sx = cx + r * math.cos(startAngle);
    final double sy = cy + r * math.sin(startAngle);
    final double ex = cx + r * math.cos(endAngle);
    final double ey = cy + r * math.sin(endAngle);
    final int largeArc = sweepAngle > math.pi ? 1 : 0;
    return 'M ${sx.toStringAsFixed(2)} ${sy.toStringAsFixed(2)}'
        ' A $r $r 0 $largeArc 1'
        ' ${ex.toStringAsFixed(2)} ${ey.toStringAsFixed(2)}';
  }

  static String _buildTrackD({
    required double cx,
    required double cy,
    required double r,
  }) {
    final double startAngle = -math.pi * 0.75;
    final double endAngle = startAngle + math.pi * 1.5;
    final double sx = cx + r * math.cos(startAngle);
    final double sy = cy + r * math.sin(startAngle);
    final double ex = cx + r * math.cos(endAngle);
    final double ey = cy + r * math.sin(endAngle);
    return 'M ${sx.toStringAsFixed(2)} ${sy.toStringAsFixed(2)}'
        ' A $r $r 0 1 1'
        ' ${ex.toStringAsFixed(2)} ${ey.toStringAsFixed(2)}';
  }

  @override
  Widget build(BuildContext context) {
    final GaugeStatus status = invertStatus
        ? statusFor(max - value, thresholds)
        : statusFor(value, thresholds);
    final String arcColor = _cssColorForStatus(status);
    final String glow = 'color-mix(in srgb, $arcColor 55%, transparent)';
    final double fraction = max > 0.0 ? (value / max).clamp(0.0, 1.0) : 0.0;
    const double cx = 50.0;
    const double cy = 50.0;
    const double r = 38.0;

    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'align-items': 'center',
          'gap': '0.55rem',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'position': 'relative',
              'width': '132px',
              'height': '132px',
            },
          ),
          <Widget>[
            Component.element(
              tag: 'svg',
              attributes: <String, String>{
                'viewBox': '0 0 100 100',
                'width': '132',
                'height': '132',
                'aria-label': label,
              },
              children: <Component>[
                Component.element(
                  tag: 'path',
                  attributes: <String, String>{
                    'd': _buildTrackD(cx: cx, cy: cy, r: r),
                    'fill': 'none',
                    'stroke':
                        'color-mix(in srgb, var(--border) 85%, transparent)',
                    'stroke-width': '7',
                    'stroke-linecap': 'round',
                  },
                ),
                if (fraction > 0.0)
                  Component.element(
                    tag: 'path',
                    attributes: <String, String>{
                      'd': _buildArcD(fraction: fraction, cx: cx, cy: cy, r: r),
                      'fill': 'none',
                      'stroke': arcColor,
                      'stroke-width': '7',
                      'stroke-linecap': 'round',
                      'style': 'filter: drop-shadow(0 0 5px $glow)',
                    },
                  ),
              ],
            ),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'position': 'absolute',
                  'inset': '0',
                  'display': 'flex',
                  'flex-direction': 'column',
                  'align-items': 'center',
                  'justify-content': 'center',
                  'gap': '0.15rem',
                },
              ),
              <Widget>[
                dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '1.4rem',
                      'font-weight': '700',
                      'color': 'var(--foreground)',
                      'line-height': '1',
                      'font-variant-numeric': 'tabular-nums',
                    },
                  ),
                  <Widget>[Component.text(display ?? value.toStringAsFixed(1))],
                ),
                if (status != GaugeStatus.success)
                  dom.div(
                    styles: dom.Styles(
                      raw: <String, String>{
                        'font-size': '0.625rem',
                        'font-weight': '600',
                        'letter-spacing': '0',
                        'text-transform': 'uppercase',
                        'color': arcColor,
                      },
                    ),
                    <Widget>[Component.text(_statusWord(status))],
                  ),
              ],
            ),
          ],
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'font-size': '0.75rem',
              'font-weight': '500',
              'color': 'var(--muted-foreground)',
              'letter-spacing': '0',
            },
          ),
          <Widget>[Component.text(label)],
        ),
      ],
    );
  }
}
