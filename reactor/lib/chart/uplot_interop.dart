library;

import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:web/web.dart' as web;

@JS('uPlot')
extension type UPlotJS._(JSObject _) implements JSObject {
  @JS('uPlot')
  external factory UPlotJS(JSObject opts, JSArray<JSArray<JSNumber>> data, JSObject target);

  external void setData(JSArray<JSArray<JSNumber>> data);

  external void destroy();
}

JSArray<JSArray<JSNumber>> _buildData(List<(String, List<double>)> series) {
  final int len = series.isEmpty ? 0 : series[0].$2.length;

  final JSArray<JSNumber> timestamps =
      List<double>.generate(len, (int i) => i.toDouble())
          .map((double d) => d.toJS)
          .toList()
          .toJS;

  final List<JSArray<JSNumber>> rows = <JSArray<JSNumber>>[timestamps];
  for (final (String _, List<double> values) in series) {
    rows.add(values.map((double d) => d.toJS).toList().toJS);
  }
  return rows.toJS;
}

JSObject _buildOpts(List<(String, List<double>)> series, int width, int height) {
  final JSObject opts = JSObject();
  opts['width'] = width.toDouble().toJS;
  opts['height'] = height.toJS;

  final List<JSObject> seriesConfigs = <JSObject>[JSObject()];
  for (final (String label, _) in series) {
    final JSObject s = JSObject();
    s['label'] = label.toJS;
    seriesConfigs.add(s);
  }
  opts['series'] = seriesConfigs.toJS;
  return opts;
}

class UPlotHandle {
  UPlotHandle._(this._plot);

  final UPlotJS _plot;

  static UPlotHandle? tryCreate(
    JSObject opts,
    JSArray<JSArray<JSNumber>> data,
    JSObject target,
  ) {
    try {
      return UPlotHandle._(UPlotJS(opts, data, target));
    } catch (_) {
      return null;
    }
  }

  static UPlotHandle? mountOnHost({
    required String hostId,
    required List<(String, List<double>)> series,
    required int height,
  }) {
    final web.Element? el = web.document.getElementById(hostId);
    if (el == null) return null;
    final int measured = el.clientWidth;
    final int width = measured > 0 ? measured : 600;
    final JSObject opts = _buildOpts(series, width, height);
    final JSArray<JSArray<JSNumber>> data = _buildData(series);
    return tryCreate(opts, data, el as JSObject);
  }

  void setSeriesData(List<(String, List<double>)> series) {
    _plot.setData(_buildData(series));
  }

  void destroy() {
    _plot.destroy();
  }
}
