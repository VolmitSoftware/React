library;

import 'dart:async';

import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart';

import 'svg_fallback_chart.dart';
import 'uplot_interop_stub.dart'
    if (dart.library.js_interop) 'uplot_interop.dart';

class TimeseriesChart extends StatefulComponent {
  const TimeseriesChart({
    required this.series,
    this.height = 160,
    super.key,
  });

  final List<(String, List<double>)> series;
  final int height;

  @override
  State<TimeseriesChart> createState() => _TimeseriesChartState();
}

class _TimeseriesChartState extends State<TimeseriesChart> {
  late final String _hostId;
  UPlotHandle? _handle;
  bool _useFallback = !kIsWeb;
  bool _mounted = false;

  @override
  void initState() {
    super.initState();
    _hostId = 'uplot-${identityHashCode(this)}';
    _mounted = true;
    if (kIsWeb) {
      Timer.run(_tryMount);
    }
  }

  void _tryMount() {
    if (!_mounted) return;
    final UPlotHandle? handle = UPlotHandle.mountOnHost(
      hostId: _hostId,
      series: component.series,
      height: component.height,
    );
    if (handle == null) {
      setState(() {
        _useFallback = true;
      });
    } else {
      setState(() {
        _handle = handle;
      });
    }
  }

  @override
  void didUpdateComponent(TimeseriesChart oldComponent) {
    super.didUpdateComponent(oldComponent);
    if (_handle != null) {
      _handle!.setSeriesData(component.series);
    } else if (kIsWeb && !_useFallback) {
      Timer.run(_tryMount);
    }
  }

  @override
  void dispose() {
    _mounted = false;
    _handle?.destroy();
    _handle = null;
    super.dispose();
  }

  @override
  Component build(BuildContext context) {
    final String hostHeight = '${component.height}px';

    if (_useFallback || !kIsWeb) {
      return dom.div(
        styles: dom.Styles(raw: <String, String>{
          'width': '100%',
          'height': hostHeight,
        }),
        <Component>[
          SvgFallbackChart(
            series: component.series,
            height: component.height,
          ),
        ],
      );
    }

    return dom.div(
      id: _hostId,
      styles: dom.Styles(raw: <String, String>{
        'width': '100%',
        'height': hostHeight,
        'position': 'relative',
      }),
      const <Component>[],
    );
  }
}
