package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ConfigResource {

    private static final Set<String> VALID_PRESETS = Set.of("off", "light", "balanced", "high");

    private final Supplier<ConfigSectionDto[]> treeSupplier;
    private final ConfigApplier applier;
    private final PresetApplier presetApplier;
    private final WebMutationReporter reporter;
    private final Object mutationLock = new Object();

    public ConfigResource(
            Supplier<ConfigSectionDto[]> treeSupplier,
            ConfigApplier applier,
            PresetApplier presetApplier,
            WebMutationReporter reporter) {
        this.treeSupplier = treeSupplier;
        this.applier = applier;
        this.presetApplier = presetApplier;
        this.reporter = reporter;
    }

    public record ConfigData(ConfigSectionDto[] sections) {}

    public void get(Context ctx) {
        ctx.json(new Envelope<>(new ConfigData(treeSupplier.get())));
    }

    @SuppressWarnings("unchecked")
    public void put(Context ctx) {
        WebAuth.requireScope(ctx, "admin");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || body.isEmpty()) {
            throw new BadRequestResponse("No config values supplied");
        }
        synchronized (mutationLock) {
            applyAll(body);
            reporter.report(ctx, new WebMutation(
                "config.update",
                "configuration",
                "updated " + body.size() + " value(s): " + String.join(", ", body.keySet()),
                "APPLIED"
            ));
            ctx.json(new Envelope<>(new ConfigData(treeSupplier.get())));
        }
    }

    public void preset(Context ctx) {
        WebAuth.requireScope(ctx, "admin");
        String name = ctx.pathParam("name");
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!VALID_PRESETS.contains(normalized)) {
            throw new BadRequestResponse("Unknown preset: " + name);
        }
        synchronized (mutationLock) {
            int updated = presetApplier.apply(normalized);
            if (updated < 0) {
                throw new InternalServerErrorResponse("Failed to apply preset: " + normalized);
            }
            reporter.report(ctx, new WebMutation(
                "config.preset",
                "configuration",
                "applied preset " + normalized + " (" + updated + " value(s))",
                "APPLIED"
            ));
            ctx.json(new Envelope<>(new ConfigData(treeSupplier.get())));
        }
    }

    private void applyAll(Map<String, Object> body) {
        Map<String, Object> originalValues = currentValues();
        for (String path : body.keySet()) {
            if (!originalValues.containsKey(path)) {
                throw new BadRequestResponse("Unknown config path: " + path);
            }
        }

        List<String> applied = new ArrayList<>(body.size());
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!applier.apply(entry.getKey(), entry.getValue())) {
                rollback(originalValues, applied);
                throw new InternalServerErrorResponse("Failed to apply config path: " + entry.getKey());
            }
            applied.add(entry.getKey());
        }
    }

    private Map<String, Object> currentValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ConfigSectionDto section : treeSupplier.get()) {
            if (section == null || section.nodes == null) {
                continue;
            }
            for (ConfigNodeDto node : section.nodes) {
                if (node != null && node.key != null) {
                    values.put(node.key, node.value);
                }
            }
        }
        return values;
    }

    private void rollback(Map<String, Object> originalValues, List<String> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            String path = applied.get(index);
            if (!applier.apply(path, originalValues.get(path))) {
                throw new InternalServerErrorResponse("Failed to restore config path after rejection: " + path);
            }
        }
    }
}
