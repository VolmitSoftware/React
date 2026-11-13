package art.arcane.react.api.web.resource;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.HistoryCursorCodec;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.MetricHistoryPageDto;
import art.arcane.react.api.web.dto.MetricHistorySeriesDto;
import art.arcane.react.api.web.dto.MetricRangeDto;
import art.arcane.react.api.web.dto.MetricRangeSeriesDto;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.ChartHistory;
import art.arcane.react.core.history.ChartRange;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.HistoryQuerySeries;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.ServiceUnavailableResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MetricsResource {
    private static final long DEFAULT_RANGE_MS = 24L * 60L * 60L * 1_000L;
    private static final int MAX_RANGE_IDS = 64;

    private final SampleController samplers;
    private final HistoryController history;
    private final MetricsSerializer serializer;
    private volatile CachedSnapshot cachedSnapshot;
    private volatile CachedLiveFrame cachedLiveFrame;

    public MetricsResource(
        SampleController samplers,
        HistoryController history,
        MetricsSerializer serializer
    ) {
        this.samplers = samplers;
        this.history = history;
        this.serializer = serializer;
    }

    public SnapshotResponse snapshotData() {
        WebRevisions.Snapshot revisions = WebRevisions.current();
        MetricSnapshot snapshot = history == null ? MetricSnapshot.empty() : history.latestObserved();
        if (!snapshot.values().isEmpty()) {
            return snapshotResponse(snapshot, revisions);
        }
        cachedSnapshot = null;
        return registrySnapshot(revisions);
    }

    private SnapshotResponse registrySnapshot(WebRevisions.Snapshot revisions) {
        Registry<Sampler> registry = samplers == null ? null : samplers.getSamplers();
        if (registry == null) {
            return new SnapshotResponse(0L, System.currentTimeMillis(), new SamplerDto[0], revisions);
        }
        Collection<Sampler> all = registry.all();
        List<SamplerDto> data = new ArrayList<>(all.size());
        for (Sampler sampler : all) {
            data.add(serializer.toDto(sampler));
        }
        return new SnapshotResponse(0L, System.currentTimeMillis(), data.toArray(new SamplerDto[0]), revisions);
    }

    /**
     * The live frame pushed over {@code /ws/metrics}: available samplers only, no metadata or
     * diagnostics. Returns the same instance until the snapshot or the revisions change.
     */
    public LiveFrame liveFrame() {
        WebRevisions.Snapshot revisions = WebRevisions.current();
        MetricSnapshot snapshot = history == null ? MetricSnapshot.empty() : history.latestObserved();
        if (snapshot.values().isEmpty()) {
            cachedLiveFrame = null;
            SnapshotResponse fallback = registrySnapshot(revisions);
            return new LiveFrame(fallback.sequence(), fallback.capturedAtMs(), liveSamples(fallback.samplers()), revisions);
        }
        CachedLiveFrame cached = cachedLiveFrame;
        if (cached != null && cached.source() == snapshot && cached.frame().revisions() == revisions) {
            return cached.frame();
        }
        synchronized (this) {
            cached = cachedLiveFrame;
            if (cached != null && cached.source() == snapshot && cached.frame().revisions() == revisions) {
                return cached.frame();
            }
            LiveSampleDto[] samples = cached != null && cached.source() == snapshot
                ? cached.frame().samplers()
                : liveSamples(snapshot);
            LiveFrame frame = new LiveFrame(snapshot.sequence(), snapshot.capturedAtMs(), samples, revisions);
            cachedLiveFrame = new CachedLiveFrame(snapshot, frame);
            return frame;
        }
    }

    public void snapshot(Context context) {
        if ("1".equals(context.queryParam("compact"))) {
            context.json(new Envelope<>(liveFrame()));
            return;
        }
        context.json(new Envelope<>(snapshotData()));
    }

    private static LiveSampleDto[] liveSamples(MetricSnapshot snapshot) {
        int available = 0;
        for (MetricSnapshotValue value : snapshot.values()) {
            if (value.available()) {
                available++;
            }
        }
        LiveSampleDto[] samples = new LiveSampleDto[available];
        int index = 0;
        for (MetricSnapshotValue value : snapshot.values()) {
            if (value.available()) {
                samples[index++] = new LiveSampleDto(value.id(), value.name(), value.suffix(), value.value(), value.display());
            }
        }
        return samples;
    }

    private static LiveSampleDto[] liveSamples(SamplerDto[] samplers) {
        List<LiveSampleDto> samples = new ArrayList<>(samplers.length);
        for (SamplerDto sampler : samplers) {
            if (sampler.available) {
                samples.add(new LiveSampleDto(sampler.id, sampler.name, sampler.suffix, sampler.value, sampler.display));
            }
        }
        return samples.toArray(new LiveSampleDto[0]);
    }

    public void historyCatalog(Context context) {
        if (history == null) {
            context.json(new Envelope<>(new MetricHistoryDescriptorDto[0]));
            return;
        }
        List<MetricDescriptor> descriptors = history.descriptors();
        MetricSnapshot latest = history.latest();
        MetricHistoryDescriptorDto[] data = new MetricHistoryDescriptorDto[descriptors.size()];
        for (int index = 0; index < descriptors.size(); index++) {
            MetricDescriptor descriptor = descriptors.get(index);
            MetricSnapshotValue live = latest.value(descriptor.id());
            data[index] = new MetricHistoryDescriptorDto(
                descriptor.id(),
                descriptor.name(),
                descriptor.suffix(),
                descriptor.firstTimestampMs(),
                descriptor.lastTimestampMs(),
                descriptor.active(),
                descriptor.metadata().intervalMs(),
                descriptor.metadata().windowMs(),
                descriptor.metadata().quality(),
                descriptor.metadata().unit(),
                live != null && live.available(),
                live == null || live.available() ? null : live.unavailableReason()
            );
        }
        context.json(new Envelope<>(data));
    }

    public void history(Context context) throws IOException {
        if (history == null) {
            throw new ServiceUnavailableResponse("Metric history is not available");
        }
        QueryPage query = context.queryParam("cursor") == null
            ? initialQuery(context)
            : cursorQuery(context.queryParam("cursor"));
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
        if (history == null) {
            throw new ServiceUnavailableResponse("Metric history is not available");
        }
        ChartRange range = ChartRange.byId(context.queryParam("range"));
        if (range == null) {
            throw new BadRequestResponse("range must be one of 1h, 6h or 24h");
        }
        List<String> ids = parseRangeIds(context.queryParam("ids"));
        ChartHistory.Window window = history.chartRange(range, ids, System.currentTimeMillis());
        MetricRangeSeriesDto[] series = new MetricRangeSeriesDto[window.series().size()];
        for (int index = 0; index < series.length; index++) {
            ChartHistory.Series value = window.series().get(index);
            series[index] = new MetricRangeSeriesDto(value.id(), value.values());
        }
        context.json(new Envelope<>(new MetricRangeDto(
            range.id(),
            range.stepMs(),
            window.fromMs(),
            window.toMs(),
            series
        )));
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

    private SnapshotResponse snapshotResponse(MetricSnapshot snapshot, WebRevisions.Snapshot revisions) {
        CachedSnapshot cached = cachedSnapshot;
        if (cached != null && cached.source() == snapshot && cached.response().revisions() == revisions) {
            return cached.response();
        }
        synchronized (this) {
            cached = cachedSnapshot;
            if (cached != null && cached.source() == snapshot) {
                if (cached.response().revisions() == revisions) {
                    return cached.response();
                }
                SnapshotResponse response = cached.response().withRevisions(revisions);
                cachedSnapshot = new CachedSnapshot(snapshot, response);
                return response;
            }
            SamplerDto[] data = new SamplerDto[snapshot.values().size()];
            for (int index = 0; index < snapshot.values().size(); index++) {
                data[index] = serializer.toDto(snapshot.values().get(index));
            }
            SnapshotResponse response = new SnapshotResponse(snapshot.sequence(), snapshot.capturedAtMs(), data, revisions);
            cachedSnapshot = new CachedSnapshot(snapshot, response);
            return response;
        }
    }

    private QueryPage initialQuery(Context context) {
        List<String> ids = parseIds(context.queryParam("ids"));
        int maxPoints = parseInt(context.queryParam("maxPoints"), 1_200, "maxPoints");
        if (maxPoints < 1 || maxPoints > history.effectiveMaxQueryPoints()) {
            throw new BadRequestResponse("maxPoints must be between 1 and " + history.effectiveMaxQueryPoints());
        }
        int pagePoints = parseInt(
            context.queryParam("pageSize"),
            history.effectiveQueryPagePoints(),
            "pageSize"
        );
        if (pagePoints < 1 || pagePoints > history.effectiveQueryPagePoints()) {
            throw new BadRequestResponse("pageSize must be between 1 and " + history.effectiveQueryPagePoints());
        }
        MetricSnapshot snapshot = history.latest();
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

    private QueryPage cursorQuery(String cursor) {
        try {
            HistoryCursorCodec.HistoryCursor decoded = HistoryCursorCodec.decode(cursor);
            validateIds(decoded.ids());
            validateRange(decoded.requestedFromMs(), decoded.requestedToMs());
            if (decoded.nextFromMs() < decoded.requestedFromMs()
                || decoded.nextFromMs() >= decoded.requestedToMs()
                || decoded.resolutionMs() < 1_000L
                || decoded.pagePoints() < 1
                || decoded.pagePoints() > history.effectiveQueryPagePoints()) {
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

    private List<String> parseIds(String raw) {
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
        validateIds(result);
        return result;
    }

    private void validateIds(List<String> ids) {
        if (ids.isEmpty() || ids.size() > history.effectiveMaxQuerySeries()) {
            throw new BadRequestResponse(
                "ids must contain between 1 and " + history.effectiveMaxQuerySeries() + " unique metrics"
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

    private record CachedSnapshot(MetricSnapshot source, SnapshotResponse response) {
    }

    private record CachedLiveFrame(MetricSnapshot source, LiveFrame frame) {
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
