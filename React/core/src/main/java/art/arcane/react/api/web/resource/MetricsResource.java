package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.HistoryCursorCodec;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.MetricHistoryPageDto;
import art.arcane.react.api.web.dto.MetricHistorySeriesDto;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.HistoryQuerySeries;
import art.arcane.react.platform.WebMetricHistory;
import art.arcane.react.platform.WebMetricsSource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.ServiceUnavailableResponse;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MetricsResource {
    private static final long DEFAULT_RANGE_MS = 24L * 60L * 60L * 1_000L;
    private static final int MAX_RANGE_IDS = 64;

    private final WebMetricsSource source;
    private volatile CachedSnapshot cachedSnapshot;
    private volatile CachedLiveFrame cachedLiveFrame;

    public MetricsResource(WebMetricsSource source) {
        this.source = source == null ? WebMetricsSource.empty() : source;
    }

    public SnapshotResponse snapshotData() {
        WebRevisions.Snapshot revisions = WebRevisions.current();
        WebMetricsSource.Frame frame = source.currentFrame();
        CachedSnapshot cached = cachedSnapshot;
        if (cached != null && cached.source() == frame && cached.response().revisions() == revisions) {
            return cached.response();
        }
        synchronized (this) {
            cached = cachedSnapshot;
            if (cached != null && cached.source() == frame) {
                if (cached.response().revisions() == revisions) {
                    return cached.response();
                }
                SnapshotResponse response = cached.response().withRevisions(revisions);
                cachedSnapshot = new CachedSnapshot(frame, response);
                return response;
            }
            SamplerDto[] data = new SamplerDto[frame.values().size()];
            for (int index = 0; index < data.length; index++) {
                data[index] = toDto(frame.values().get(index));
            }
            SnapshotResponse response = new SnapshotResponse(frame.sequence(), frame.capturedAtMs(), data, revisions);
            cachedSnapshot = new CachedSnapshot(frame, response);
            return response;
        }
    }

    /**
     * The live frame pushed over {@code /ws/metrics}: available samplers only, no metadata or
     * diagnostics. Returns the same instance until the captured frame or the revisions change.
     */
    public LiveFrame liveFrame() {
        WebRevisions.Snapshot revisions = WebRevisions.current();
        WebMetricsSource.Frame source = this.source.currentFrame();
        CachedLiveFrame cached = cachedLiveFrame;
        if (cached != null && cached.source() == source && cached.frame().revisions() == revisions) {
            return cached.frame();
        }
        synchronized (this) {
            cached = cachedLiveFrame;
            if (cached != null && cached.source() == source && cached.frame().revisions() == revisions) {
                return cached.frame();
            }
            LiveSampleDto[] samples = cached != null && cached.source() == source
                ? cached.frame().samplers()
                : liveSamples(source);
            LiveFrame frame = new LiveFrame(source.sequence(), source.capturedAtMs(), samples, revisions);
            cachedLiveFrame = new CachedLiveFrame(source, frame);
            return frame;
        }
    }

    public static SamplerDto toDto(WebMetricsSource.Value value) {
        SamplerDto dto = new SamplerDto();
        dto.id = value.id();
        dto.name = value.name();
        dto.value = value.value();
        dto.suffix = value.suffix();
        dto.display = value.display();
        dto.available = value.available();
        dto.sampledAtMs = value.sampledAtMs();
        dto.intervalMs = value.intervalMs();
        dto.windowMs = value.windowMs();
        dto.quality = value.quality();
        dto.unit = value.unit();
        dto.unavailableReason = value.unavailableReason();
        dto.diagnostics = value.diagnostics();
        return dto;
    }

    public void snapshot(Context context) {
        if ("1".equals(context.queryParam("compact"))) {
            context.json(new Envelope<>(liveFrame()));
            return;
        }
        context.json(new Envelope<>(snapshotData()));
    }

    private static LiveSampleDto[] liveSamples(WebMetricsSource.Frame frame) {
        int available = 0;
        for (WebMetricsSource.Value value : frame.values()) {
            if (value.available()) {
                available++;
            }
        }
        LiveSampleDto[] samples = new LiveSampleDto[available];
        int index = 0;
        for (WebMetricsSource.Value value : frame.values()) {
            if (value.available()) {
                samples[index++] = new LiveSampleDto(value.id(), value.name(), value.suffix(), value.value(), value.display());
            }
        }
        return samples;
    }

    private WebMetricHistory historyOrNull() {
        return source.history().orElse(null);
    }

    public void historyCatalog(Context context) {
        WebMetricHistory history = historyOrNull();
        if (history == null) {
            context.json(new Envelope<>(new MetricHistoryDescriptorDto[0]));
            return;
        }
        context.json(new Envelope<>(history.descriptors().toArray(new MetricHistoryDescriptorDto[0])));
    }

    public void history(Context context) throws IOException {
        WebMetricHistory history = historyOrNull();
        if (history == null) {
            throw new ServiceUnavailableResponse("Metric history is not available");
        }
        QueryPage query = context.queryParam("cursor") == null
            ? initialQuery(history, context)
            : cursorQuery(history, context.queryParam("cursor"));
        boolean known = false;
        for (String id : query.ids()) {
            if (history.knowsMetric(id)) {
                known = true;
                break;
            }
        }
        if (!known) {
            throw new NotFoundResponse("No requested metric is known");
        }

        long pageSpan;
        try {
            pageSpan = Math.multiplyExact(query.resolutionMs(), query.pagePoints());
        } catch (ArithmeticException failure) {
            pageSpan = Long.MAX_VALUE;
        }
        long pageTo = Math.min(query.requestedToMs(), saturatedAdd(query.pageFromMs(), pageSpan));
        HistoryQueryResult result = history.query(
            query.ids(),
            query.pageFromMs(),
            pageTo,
            query.resolutionMs(),
            query.throughSequence(),
            query.throughMs()
        );
        String nextCursor = pageTo >= query.requestedToMs()
            ? null
            : HistoryCursorCodec.encode(new HistoryCursorCodec.HistoryCursor(
                query.ids(),
                query.requestedFromMs(),
                query.requestedToMs(),
                pageTo,
                query.resolutionMs(),
                query.throughSequence(),
                query.throughMs(),
                query.pagePoints()
            ));
        context.json(new Envelope<>(new MetricHistoryPageDto(
            query.requestedFromMs(),
            query.requestedToMs(),
            query.pageFromMs(),
            pageTo,
            query.resolutionMs(),
            query.throughSequence(),
            query.throughMs(),
            nextCursor,
            seriesDtos(result.series())
        )));
    }

    public void range(Context context) {
        WebMetricHistory history = historyOrNull();
        if (history == null) {
            throw new ServiceUnavailableResponse("Metric history is not available");
        }
        ChartRange range = ChartRange.byId(context.queryParam("range"));
        if (range == null) {
            throw new BadRequestResponse("range must be one of 1h, 6h or 24h");
        }
        List<String> ids = parseRangeIds(context.queryParam("ids"));
        context.json(new Envelope<>(history.range(range, ids, System.currentTimeMillis())));
    }

    private static List<String> parseRangeIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String candidate : raw.split(",")) {
            String id = candidate.strip();
            if (id.isEmpty()) {
                continue;
            }
            if (id.length() > 256) {
                throw new BadRequestResponse("Metric ids must be at most 256 characters");
            }
            ids.add(id);
            if (ids.size() > MAX_RANGE_IDS) {
                throw new BadRequestResponse("ids must contain at most " + MAX_RANGE_IDS + " metrics");
            }
        }
        return List.copyOf(ids);
    }

    private QueryPage initialQuery(WebMetricHistory history, Context context) {
        List<String> ids = parseIds(history, context.queryParam("ids"));
        int maxPoints = parseInt(context.queryParam("maxPoints"), 1_200, "maxPoints");
        if (maxPoints < 1 || maxPoints > history.maxQueryPoints()) {
            throw new BadRequestResponse("maxPoints must be between 1 and " + history.maxQueryPoints());
        }
        int pagePoints = parseInt(
            context.queryParam("pageSize"),
            history.queryPagePoints(),
            "pageSize"
        );
        if (pagePoints < 1 || pagePoints > history.queryPagePoints()) {
            throw new BadRequestResponse("pageSize must be between 1 and " + history.queryPagePoints());
        }
        WebMetricHistory.Capture snapshot = history.latestCapture();
        long throughMs = snapshot.capturedAtMs() > 0L ? snapshot.capturedAtMs() : System.currentTimeMillis();
        long requestedToMs = parseLong(context.queryParam("to"), throughMs, "to");
        requestedToMs = Math.min(requestedToMs, saturatedAdd(throughMs, 1L));
        long requestedFromMs = parseLong(
            context.queryParam("from"),
            Math.max(0L, requestedToMs - DEFAULT_RANGE_MS),
            "from"
        );
        validateRange(requestedFromMs, requestedToMs);
        long resolutionMs = history.selectResolution(requestedFromMs, requestedToMs, maxPoints);
        return new QueryPage(
            ids,
            requestedFromMs,
            requestedToMs,
            requestedFromMs,
            resolutionMs,
            snapshot.sequence(),
            throughMs,
            pagePoints
        );
    }

    private QueryPage cursorQuery(WebMetricHistory history, String cursor) {
        try {
            HistoryCursorCodec.HistoryCursor decoded = HistoryCursorCodec.decode(cursor);
            validateIds(history, decoded.ids());
            validateRange(decoded.requestedFromMs(), decoded.requestedToMs());
            if (decoded.nextFromMs() < decoded.requestedFromMs()
                || decoded.nextFromMs() >= decoded.requestedToMs()
                || decoded.resolutionMs() < 1_000L
                || decoded.pagePoints() < 1
                || decoded.pagePoints() > history.queryPagePoints()) {
                throw new BadRequestResponse("History cursor contains invalid query bounds");
            }
            return new QueryPage(
                decoded.ids(),
                decoded.requestedFromMs(),
                decoded.requestedToMs(),
                decoded.nextFromMs(),
                decoded.resolutionMs(),
                decoded.throughSequence(),
                decoded.throughMs(),
                decoded.pagePoints()
            );
        } catch (BadRequestResponse failure) {
            throw failure;
        } catch (IOException failure) {
            throw new BadRequestResponse("Invalid history cursor: " + failure.getMessage());
        }
    }

    private List<String> parseIds(WebMetricHistory history, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestResponse("ids is required");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String candidate : raw.split(",")) {
            String id = candidate.strip();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        List<String> result = List.copyOf(ids);
        validateIds(history, result);
        return result;
    }

    private static void validateIds(WebMetricHistory history, List<String> ids) {
        if (ids.isEmpty() || ids.size() > history.maxQuerySeries()) {
            throw new BadRequestResponse(
                "ids must contain between 1 and " + history.maxQuerySeries() + " unique metrics"
            );
        }
        for (String id : ids) {
            if (id.isBlank() || id.length() > 256 || id.indexOf('\0') >= 0) {
                throw new BadRequestResponse("Metric ids must be non-empty and at most 256 characters");
            }
        }
    }

    private static MetricHistorySeriesDto[] seriesDtos(List<HistoryQuerySeries> series) {
        MetricHistorySeriesDto[] data = new MetricHistorySeriesDto[series.size()];
        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            HistoryQuerySeries value = series.get(seriesIndex);
            Number[][] points = new Number[value.points().size()][];
            for (int pointIndex = 0; pointIndex < value.points().size(); pointIndex++) {
                HistoryPoint point = value.points().get(pointIndex);
                points[pointIndex] = new Number[]{
                    point.timestampMs(),
                    point.average(),
                    point.minimum(),
                    point.maximum(),
                    point.last(),
                    point.count(),
                    point.firstSampledAtMs(),
                    point.lastSampledAtMs()
                };
            }
            data[seriesIndex] = new MetricHistorySeriesDto(
                value.id(),
                value.name(),
                value.suffix(),
                points
            );
        }
        return data;
    }

    private static int parseInt(String raw, int fallback, String label) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException failure) {
            throw new BadRequestResponse(label + " must be an integer");
        }
    }

    private static long parseLong(String raw, long fallback, String label) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException failure) {
            throw new BadRequestResponse(label + " must be an epoch-millisecond integer");
        }
    }

    private static void validateRange(long fromMs, long toMs) {
        if (fromMs < 0L || toMs <= fromMs) {
            throw new BadRequestResponse("from must be non-negative and earlier than to");
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException failure) {
            return Long.MAX_VALUE;
        }
    }

    private record CachedSnapshot(WebMetricsSource.Frame source, SnapshotResponse response) {
    }

    private record CachedLiveFrame(WebMetricsSource.Frame source, LiveFrame frame) {
    }

    public record SnapshotResponse(
        long sequence,
        long capturedAtMs,
        SamplerDto[] samplers,
        WebRevisions.Snapshot revisions
    ) {
        SnapshotResponse withRevisions(WebRevisions.Snapshot current) {
            return new SnapshotResponse(sequence, capturedAtMs, samplers, current);
        }
    }

    public record LiveFrame(
        long sequence,
        long capturedAtMs,
        LiveSampleDto[] samplers,
        WebRevisions.Snapshot revisions
    ) {
    }

    public record LiveSampleDto(String id, String name, String suffix, double value, String display) {
    }

    private record QueryPage(
        List<String> ids,
        long requestedFromMs,
        long requestedToMs,
        long pageFromMs,
        long resolutionMs,
        long throughSequence,
        long throughMs,
        int pagePoints
    ) {
    }
}
