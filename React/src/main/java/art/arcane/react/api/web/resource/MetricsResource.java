package art.arcane.react.api.web.resource;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.HistoryCursorCodec;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.MetricHistoryPageDto;
import art.arcane.react.api.web.dto.MetricHistorySeriesDto;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistoryQueryResult;
import art.arcane.react.core.history.HistoryQuerySeries;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
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

    private final SampleController samplers;
    private final HistoryController history;
    private final MetricsSerializer serializer;

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
        MetricSnapshot snapshot = history == null ? MetricSnapshot.empty() : history.latest();
        if (!snapshot.values().isEmpty()) {
            SamplerDto[] data = new SamplerDto[snapshot.values().size()];
            for (int index = 0; index < snapshot.values().size(); index++) {
                data[index] = serializer.toDto(snapshot.values().get(index));
            }
            return new SnapshotResponse(snapshot.sequence(), snapshot.capturedAtMs(), data);
        }

        Registry<Sampler> registry = samplers == null ? null : samplers.getSamplers();
        if (registry == null) {
            return new SnapshotResponse(0L, System.currentTimeMillis(), new SamplerDto[0]);
        }
        Collection<Sampler> all = registry.all();
        List<SamplerDto> data = new ArrayList<>(all.size());
        for (Sampler sampler : all) {
            data.add(serializer.toDto(sampler));
        }
        return new SnapshotResponse(0L, System.currentTimeMillis(), data.toArray(new SamplerDto[0]));
    }

    public void snapshot(Context context) {
        context.json(new Envelope<>(snapshotData()));
    }

    public void historyCatalog(Context context) {
        if (history == null) {
            context.json(new Envelope<>(new MetricHistoryDescriptorDto[0]));
            return;
        }
        List<MetricDescriptor> descriptors = history.descriptors();
        MetricHistoryDescriptorDto[] data = new MetricHistoryDescriptorDto[descriptors.size()];
        for (int index = 0; index < descriptors.size(); index++) {
            MetricDescriptor descriptor = descriptors.get(index);
            data[index] = new MetricHistoryDescriptorDto(
                descriptor.id(),
                descriptor.name(),
                descriptor.suffix(),
                descriptor.firstTimestampMs(),
                descriptor.lastTimestampMs(),
                descriptor.active()
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
                    point.count()
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

    public record SnapshotResponse(long sequence, long capturedAtMs, SamplerDto[] samplers) {
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
