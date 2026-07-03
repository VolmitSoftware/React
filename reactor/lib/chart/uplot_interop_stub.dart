library;

class UPlotHandle {
  UPlotHandle._();

  static UPlotHandle? tryCreate(
    Object opts,
    Object data,
    Object target,
  ) =>
      null;

  static UPlotHandle? mountOnHost({
    required String hostId,
    required List<(String, List<double>)> series,
    required int height,
  }) =>
      null;

  void setSeriesData(List<(String, List<double>)> series) {}

  void destroy() {}
}
