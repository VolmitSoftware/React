package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.gui.ReactSettingsMenu;
import art.arcane.react.core.gui.SettingsInputSessions;
import art.arcane.react.core.gui.SettingsSource;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.util.project.config.TomlFiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
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
    private final ModLanguageCommand languageCommand;
    private final SettingsInputSessions inputs;
    private final ReactSettingsMenu menu;

    ModSettings(MinecraftServer server, File dataFolder, ModLanguageHost language, ModMaps maps, Supplier<SampleController> samples,
                ModTicks ticks) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.language = language;
        this.maps = maps;
        this.samples = samples;
        this.languageCommand = new ModLanguageCommand(ticks::later);
        this.menu = new ReactSettingsMenu(this, new ModSettingsScreenHost(server, ticks));
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
    }

    static Optional<ModSettings> current() {
        return Optional.ofNullable(current);
    }

    void open(ServerPlayer player) {
        menu.open(player.getUUID());
    }

    /** True when the chat message answered a settings prompt and must not reach chat. Called on the network thread. */
    public static boolean onChat(ServerPlayer player, String message) {
        ModSettings settings = current;
        return settings != null && player != null && settings.inputs.onChat(player.getUUID(), message);
    }

    public static void forget(ServerPlayer player) {
        ModSettings settings = current;
        if (settings != null && player != null) {
            settings.inputs.forget(player.getUUID());
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
        return language.reload() && ReactLanguage.reload();
    }

    @Override
    public List<CoreSection> coreSections() {
        return List.of(new CoreSection("map", "Map", "minecraft:slime_ball", maps.settings()));
    }

    @Override
    public boolean reloadCore(String id) {
        return "map".equals(id) && maps.reloadSettings();
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
