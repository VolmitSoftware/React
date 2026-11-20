package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.OwnerCostDto;
import art.arcane.react.content.feature.OwnerCostKindRenderer;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code GET /api/v1/owners/cost}: what each plugin or mod costs, split by kind of work. Reading it keeps on-demand
 * measurement running, as an open cost map does.
 */
public class OwnerCostResource {
    private static final int MAX_LIMIT = 10_000;
    private final Supplier<EventMeasurement> events;
    private final Supplier<String> ownerNoun;
    private final Predicate<String> samplerExists;

    public OwnerCostResource(Supplier<EventMeasurement> events, Supplier<String> ownerNoun, Predicate<String> samplerExists) {
        this.events = events;
        this.ownerNoun = ownerNoun;
        this.samplerExists = samplerExists;
    }

    public void get(Context ctx) {
        String limitParam = ctx.queryParam("limit");
        int limit = 0;
        if (limitParam != null) {
            try {
                limit = Math.max(0, Math.min(MAX_LIMIT, Integer.parseInt(limitParam)));
            } catch (NumberFormatException failure) {
                throw new BadRequestResponse();
            }
        }
        EventMeasurement measurement = events.get();
        if (measurement != null) {
            measurement.markSamplerActivity();
        }
        ctx.json(new Envelope<>(snapshot(measurement, ownerNoun.get(), limit, samplerExists)));
    }

    /** {@code limit} 0 lists every owner; {@code samplerExists} leaves out sampler ids of owners without a history sampler. */
    public static OwnerCostDto snapshot(EventMeasurement measurement, String ownerNoun, int limit, Predicate<String> samplerExists) {
        OwnerCostDto dto = new OwnerCostDto();
        dto.ownerNoun = ownerNoun == null || ownerNoun.isBlank() ? "plugin" : ownerNoun;
        OwnerCostModel model = measurement == null ? null : measurement.ownerCostModel();
        if (model == null) {
            dto.kinds = new OwnerCostDto.KindDto[0];
            dto.owners = new OwnerCostDto.OwnerDto[0];
            return dto;
        }

        double windowSeconds = model.windowSeconds();
        dto.measuring = model.measuring();
        dto.windowSeconds = windowSeconds;
        dto.publishedAtMs = model.publishedAtMs();
        Map<String, Map<String, Double>> ownerKindMs = model.snapshotOwnerKindMs();
        Map<String, Integer> calls = model.snapshotCalls();
        Map<String, Double> recent = model.recentSnapshot();
        Map<String, String> names = new LinkedHashMap<>(measurement.ownerCatalog());
        model.displayNames().forEach(names::putIfAbsent);
        recent.keySet().forEach((String owner) -> names.putIfAbsent(owner, owner));

        Map<String, Double> kindTotals = new HashMap<>();
        double total = 0D;
        List<OwnerCostDto.OwnerDto> owners = new ArrayList<>();
        for (Map.Entry<String, String> owner : names.entrySet()) {
            OwnerCostDto.OwnerDto row = new OwnerCostDto.OwnerDto();
            row.id = owner.getKey();
            String samplerId = OwnerCostModel.samplerId(dto.ownerNoun, owner.getKey());
            row.samplerId = samplerExists.test(samplerId) ? samplerId : null;
            row.name = owner.getValue() == null || owner.getValue().isBlank() ? owner.getKey() : owner.getValue();
            row.kinds = new LinkedHashMap<>();
            Map<String, Double> kinds = ownerKindMs.getOrDefault(owner.getKey(), Map.of());
            if (windowSeconds > 0D) {
                for (Map.Entry<String, Double> kind : kinds.entrySet()) {
                    double perSecond = Math.max(0D, kind.getValue()) / windowSeconds;
                    if (perSecond > 0D) {
                        row.kinds.put(kind.getKey(), perSecond);
                        row.msPerSecond += perSecond;
                        kindTotals.merge(kind.getKey(), perSecond, Double::sum);
                    }
                }
            }
            row.recentMsPerSecond = recent.getOrDefault(owner.getKey(), 0D);
            row.calls = calls.getOrDefault(owner.getKey(), 0);
            total += row.msPerSecond;
            owners.add(row);
        }
        for (OwnerCostDto.OwnerDto row : owners) {
            row.share = total > 0D ? row.msPerSecond / total : 0D;
        }
        owners.sort(Comparator.comparingDouble((OwnerCostDto.OwnerDto row) -> row.msPerSecond).reversed()
                .thenComparing(Comparator.comparingDouble((OwnerCostDto.OwnerDto row) -> row.recentMsPerSecond).reversed())
                .thenComparing((OwnerCostDto.OwnerDto row) -> row.name, String.CASE_INSENSITIVE_ORDER));

        List<OwnerCostDto.KindDto> kinds = new ArrayList<>();
        List<String> order = new ArrayList<>(OwnerCostKindRenderer.KIND_ORDER);
        kindTotals.keySet().stream().filter((String kind) -> !order.contains(kind)).sorted().forEach(order::add);
        for (String kind : order) {
            Double value = kindTotals.get(kind);
            if (value == null) {
                continue;
            }
            OwnerCostDto.KindDto row = new OwnerCostDto.KindDto();
            row.kind = kind;
            row.label = OwnerCostKindRenderer.kindLabel(kind);
            row.msPerSecond = value;
            row.share = total > 0D ? value / total : 0D;
            kinds.add(row);
        }

        dto.totalMsPerSecond = total;
        dto.kinds = kinds.toArray(new OwnerCostDto.KindDto[0]);
        List<OwnerCostDto.OwnerDto> listed = limit > 0 && owners.size() > limit ? owners.subList(0, limit) : owners;
        dto.owners = listed.toArray(new OwnerCostDto.OwnerDto[0]);
        return dto;
    }
}
