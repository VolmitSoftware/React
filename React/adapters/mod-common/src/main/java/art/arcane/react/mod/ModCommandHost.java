package art.arcane.react.mod;

import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.util.plugin.VolmitSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Command targets on a mod server; subcommands that need Bukkit-only systems keep the unavailable reply. */
final class ModCommandHost implements CommandHost {
    private final MinecraftServer server;
    private final ModMaps maps;
    private final ModLanguageCommand language;

    ModCommandHost(MinecraftServer server, ModMaps maps, ModLanguageCommand language) {
        this.server = server;
        this.maps = maps;
        this.language = language;
    }

    @Override
    public boolean language(ReactCommandSender sender, String[] arguments) {
        return language.execute(sender, arguments);
    }

    @Override
    public Optional<List<String>> completeLanguage(ReactCommandSender sender, String[] arguments) {
        return Optional.of(language.complete(sender, arguments));
    }

    @Override
    public List<String> rendererIds() {
        return maps.rendererIds();
    }

    @Override
    public void map(VolmitSender sender, String rendererId) {
        ReactPlayer player = sender.player();
        if (player != null && player.handle() instanceof ServerPlayer serverPlayer) {
            maps.map(serverPlayer, rendererId);
        }
    }

    @Override
    public void configGui(VolmitSender sender) {
        ReactPlayer player = sender.player();
        if (player != null && player.handle() instanceof ServerPlayer serverPlayer) {
            ModSettingsScreens.openRoot(serverPlayer);
        }
    }

    @Override
    public List<CommandTargets.Player> players() {
        List<CommandTargets.Player> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            players.add(new CommandTargets.Player(new ModReactPlayer(player)));
        }
        return players;
    }

    @Override
    public List<CommandTargets.World> worlds() {
        List<CommandTargets.World> worlds = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            worlds.add(target(level));
        }
        return worlds;
    }

    @Override
    public Optional<CommandTargets.World> worldOf(ReactPlayer player) {
        return player.handle() instanceof ServerPlayer serverPlayer ? Optional.of(target(serverPlayer.level())) : Optional.empty();
    }

    private static CommandTargets.World target(ServerLevel level) {
        return new CommandTargets.World(new ModReactWorld(level), ModReactWorld.nameOf(level));
    }
}
