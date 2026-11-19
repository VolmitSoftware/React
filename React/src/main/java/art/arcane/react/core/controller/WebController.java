package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.web.BukkitWebHost;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.platform.WebHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigFileSupport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

/** React Web on Bukkit: the platform-free {@link ReactWebServer} reading React's controllers through {@link BukkitWebHost}. */
@Getter
@Setter
public class WebController extends ReactWebServer implements IController {
    private transient volatile SampleController sampleController;
    private transient volatile HistoryController historyController;
    private transient volatile FeatureController featureController;
    private transient volatile TweakController tweakController;
    private transient volatile IntegrationController integrationController;
    private transient volatile PluginApiPackController pluginApiPackController;
    private transient volatile Supplier<SamplerIncidentScore.IncidentScoreSnapshot> incidentSnapshotSupplier;
    private final transient BukkitWebHost bukkitHost = new BukkitWebHost(this);

    public WebController() {
        super(null);
    }

    @Override
    protected WebHost host() {
        return bukkitHost;
    }

    @Override
    public String getId() {
        return "web";
    }

    @Override
    public String getName() {
        return "Web";
    }

    @Override
    public void start() {
        if (React.instance != null) {
            if (sampleController == null) {
                sampleController = React.controller(SampleController.class);
            }
            if (historyController == null) {
                historyController = React.controller(HistoryController.class);
            }
            if (featureController == null) {
                featureController = React.controller(FeatureController.class);
            }
            if (tweakController == null) {
                tweakController = React.controller(TweakController.class);
            }
            if (integrationController == null) {
                integrationController = React.controller(IntegrationController.class);
            }
            if (pluginApiPackController == null) {
                pluginApiPackController = React.controller(PluginApiPackController.class);
            }
        }
        super.start();
    }

    public static WebConfiguration prepareHotloadSnapshot(File sourceFile, String rawContent) throws IOException {
        JsonObject root = ConfigFileSupport.parseSnapshot(sourceFile, rawContent, JsonObject.class, "web-config");
        if (!root.has("listenerEnabled") || !root.has("listenAddress")) {
            throw new IOException("web.toml requires listenerEnabled and listenAddress.");
        }
        try {
            return validatedConfiguration(new Gson().fromJson(root, WebConfiguration.class));
        } catch (RuntimeException failure) {
            throw new IOException("Invalid web.toml configuration: " + failure.getMessage(), failure);
        }
    }

    public static boolean applyConfigValueOnServerThread(String path, Object value) {
        return Boolean.TRUE.equals(J.sResult(() -> ReactConfigGUI.applyAndSave(null, path, value)));
    }

    public static int applyPresetOnServerThread(String name) {
        Integer updated = J.sResult(() -> ReactConfigGUI.applyPresetHeadless(name));
        return updated == null ? -1 : updated;
    }
}
