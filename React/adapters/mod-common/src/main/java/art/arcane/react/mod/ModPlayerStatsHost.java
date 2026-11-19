package art.arcane.react.mod;

import art.arcane.react.platform.PlayerStatsHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ModPlayerStatsHost implements PlayerStatsHost {
    private final MinecraftServer server;

    ModPlayerStatsHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public int onlinePlayerCount() {
        return server.getPlayerCount();
    }

    @Override
    public Map<UUID, Integer> playerPings() {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Map<UUID, Integer> pings = new LinkedHashMap<>();
        for (ServerPlayer player : players) {
            if (player.connection != null) {
                pings.put(player.getUUID(), player.connection.latency());
            }
        }
        return pings;
    }
}
