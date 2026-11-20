package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.gui.MonitorConfigMenu;
import art.arcane.react.core.gui.ReactSettingsMenu;
import art.arcane.react.core.gui.SettingsInputSessions;
import art.arcane.react.core.gui.SettingsSource;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.TomlFiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * React's settings menu on mods: {@code react.toml}, {@code core/map.toml} and the feature, tweak, action and sampler
 * configs, edited through the same menu and chat prompts as on Bukkit.
 */
public final class ModSettings implements SettingsSource {
    private static final int INPUT_TIMEOUT_SECONDS = 45;
    private static volatile ModSettings current;

    private final MinecraftServer server;
    private final File dataFolder;
    private final ModLanguageHost language;
    private final ModMaps maps;
    private final Supplier<SampleController> samples;
    private final List<IController> controllers;
    private final ModLanguageCommand languageCommand;
    private final SettingsInputSessions inputs;
    private final ReactSettingsMenu menu;
    private final MonitorConfigMenu monitor;

    ModSettings(MinecraftServer server, File dataFolder, ModLanguageHost language, ModMaps maps, Supplier<SampleController> samples,
                ModTicks ticks, List<IController> controllers) {
        this.controllers = List.copyOf(controllers);
        this.server = server;
        this.dataFolder = dataFolder;
        this.language = language;
        this.maps = maps;
        this.samples = samples;
        this.languageCommand = new ModLanguageCommand(ticks::later);
        ModSettingsScreenHost screens = new ModSettingsScreenHost(server, ticks);
        this.menu = new ReactSettingsMenu(this, screens);
        this.monitor = new MonitorConfigMenu(screens, (sampler) -> true);
        this.inputs = new SettingsInputSessions(() -> menu, () -> INPUT_TIMEOUT_SECONDS);
    }

    static void bind(ModSettings settings) {
        current = settings;
    }

    static void unbind(ModSettings settings) {
        if (current == settings) {
            current = null;
        }
        settings.inputs.clear();
        settings.monitor.clear();
    }

    static Optional<ModSettings> current() {
        return Optional.ofNullable(current);
    }

    void open(ServerPlayer player) {
        menu.open(player.getUUID());
    }

    ReactSettingsMenu menu() {
        return menu;
    }

    MonitorConfigMenu monitorMenu() {
        return monitor;
    }

    /** True when the chat message answered a settings or monitor prompt and must not reach chat. Called on the network thread. */
    public static boolean onChat(ServerPlayer player, String message) {
        ModSettings settings = current;
        return settings != null && player != null
                && (settings.inputs.onChat(player.getUUID(), message) || settings.monitor.prompts().onChat(player.getUUID(), message));
    }

    public static void forget(ServerPlayer player) {
        ModSettings settings = current;
        if (settings != null && player != null) {
            settings.inputs.forget(player.getUUID());
            settings.monitor.forget(player.getUUID());
        }
    }

    @Override
    public boolean canConfigure(UUID viewerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
        return player != null && ModPermissions.has(player, "react.configurator");
    }

    @Override
    public Object mainSettings() {
        return language.settings();
    }

    @Override
    public File mainFile() {
        return language.settingsFile();
    }

    @Override
    public boolean reloadMain() {
        if (!language.reload() || !ReactLanguage.reload()) {
            return false;
        }
        ModPlayerMonitors.current().ifPresent(ModPlayerMonitors::updateMonitors);
        return true;
    }

    @Override
    public List<CoreSection> coreSections() {
        List<CoreSection> sections = new ArrayList<>();
        sections.add(new CoreSection("map", "Map", "minecraft:slime_ball", maps.settings()));
        for (IController controller : controllers) {
            sections.add(new CoreSection(controller.getId(), controller.getName(), controller.getIcon(), controller));
        }
        return sections;
    }

    /** The core controller with a {@code core/<id>.toml} on mods, other than the map settings. */
    Optional<IController> controller(String id) {
        for (IController controller : controllers) {
            if (controller.getId().equals(id)) {
                return Optional.of(controller);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean reloadCore(String id) {
        if ("map".equals(id)) {
            return maps.reloadSettings();
        }
        Optional<IController> controller = controller(id);
        controller.ifPresent(IController::loadConfiguration);
        return controller.isPresent();
    }

    @Override
    public File dataFile(String category, String fileName) {
        return new File(new File(dataFolder, category), fileName);
    }

    @Override
    public SampleController samples() {
        return samples.get();
    }

    @Override
    public void writeConfig(File file, String content) throws IOException {
        TomlFiles.write(file, content);
    }

    @Override
    public void openServerLanguage(UUID viewerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
        if (player != null) {
            languageCommand.execute(new ModCommandSender(player.createCommandSourceStack()), new String[]{"server"});
        }
    }

    @Override
    public Optional<SettingsInputSessions> inputSessions() {
        return Optional.of(inputs);
    }
}
