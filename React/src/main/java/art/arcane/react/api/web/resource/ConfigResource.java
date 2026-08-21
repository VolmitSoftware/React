package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.Envelope;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ConfigResource {

    private static final Set<String> VALID_PRESETS = Set.of("off", "light", "balanced", "high");

    private final Supplier<ConfigSectionDto[]> treeSupplier;
    private final ConfigApplier applier;
    private final PresetApplier presetApplier;

    public ConfigResource(Supplier<ConfigSectionDto[]> treeSupplier, ConfigApplier applier, PresetApplier presetApplier) {
        this.treeSupplier = treeSupplier;
        this.applier = applier;
        this.presetApplier = presetApplier;
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
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            applier.apply(entry.getKey(), entry.getValue());
        }
        ctx.json(new Envelope<>(new ConfigData(treeSupplier.get())));
    }

    public void preset(Context ctx) {
        WebAuth.requireScope(ctx, "admin");
        String name = ctx.pathParam("name");
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!VALID_PRESETS.contains(normalized)) {
            throw new BadRequestResponse("Unknown preset: " + name);
        }
        int updated = presetApplier.apply(normalized);
        if (updated < 0) {
            throw new BadRequestResponse("Unknown preset: " + name);
        }
        ctx.json(new Envelope<>(new ConfigData(treeSupplier.get())));
    }
}
