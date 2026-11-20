package art.arcane.react.mod;

import art.arcane.react.core.pluginapi.PluginApiHost;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.ModOwners;
import art.arcane.volmlib.integration.IntegrationMetricSample;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * API packs on a mod server. Packs target a loaded mod by id and read integration metrics; placeholders and event
 * counters are Bukkit sources and stay unavailable here.
 */
final class ModPluginApiHost implements PluginApiHost {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final Supplier<Optional<ModIntegrationMetrics>> integrations;

    ModPluginApiHost(Supplier<Optional<ModIntegrationMetrics>> integrations) {
        this.integrations = integrations;
    }

    @Override
    public String folderName() {
        return "mod-apis";
    }

    @Override
    public String label() {
        return "Mod API";
    }

    @Override
    public String targetMissingDetail() {
        return "target-mod-not-loaded";
    }

    @Override
    public List<String> bundledExamples() {
        return List.of();
    }

    @Override
    public InputStream bundledExample(String name) {
        return null;
    }

    @Override
    public String icon(String raw) {
        String id = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return ITEM_ID.matcher(id).matches() ? id : null;
    }

    @Override
    public Target target(String id) {
        for (ModOwners.LoadedMod mod : CostRegistry.loadedMods()) {
            if (mod.id().equalsIgnoreCase(id)) {
                return new Target(mod.version(), ModPluginApiHost.class.getClassLoader());
            }
        }
        return null;
    }

    @Override
    public double integration(String integrationId, String key) {
        Optional<ModIntegrationMetrics> host = integrations.get();
        if (host.isEmpty() || integrationId == null) {
            return Double.NaN;
        }
        IntegrationMetricSample sample = host.get().sample(integrationId.strip().toLowerCase(Locale.ROOT), key);
        return sample == null || !sample.available() ? Double.NaN : sample.valueOr(Double.NaN);
    }

    @Override
    public double placeholder(String placeholder, boolean foliaSafe) {
        return Double.NaN;
    }

    @Override
    public Runnable eventCounter(Target target, String eventClass, Runnable increment) {
        return null;
    }
}
