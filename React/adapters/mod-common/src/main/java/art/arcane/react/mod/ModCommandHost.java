package art.arcane.react.mod;

import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.platform.ReactPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Command targets on a mod server; subcommands that need Bukkit-only systems keep the unavailable reply. */
final class ModCommandHost implements CommandHost {
    private final MinecraftServer server;

    ModCommandHost(MinecraftServer server) {
        this.server = server;
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
