package art.arcane.react.platform;

import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.MetricRangeDto;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.HistoryQueryResult;

import java.io.IOException;
import java.util.List;

/** Stored metric history behind {@code /api/v1/metrics/catalog}, {@code /history} and {@code /range}. */
public interface WebMetricHistory {
  /** Every stored metric, with whether its latest capture is available. */
  List<MetricHistoryDescriptorDto> descriptors();

  boolean knowsMetric(String id);

  int maxQueryPoints();

  int queryPagePoints();

  int maxQuerySeries();

  long selectResolution(long fromMs, long toMs, int maxPoints);

  /** Sequence and time of the latest capture, including ones that saw no value. */
  Capture latestCapture();

  HistoryQueryResult query(List<String> ids, long fromMs, long toMs, long resolutionMs, long throughSequence, long throughMs)
      throws IOException;

  MetricRangeDto range(ChartRange range, List<String> ids, long nowMs);

  record Capture(long sequence, long capturedAtMs) {
  }
}
