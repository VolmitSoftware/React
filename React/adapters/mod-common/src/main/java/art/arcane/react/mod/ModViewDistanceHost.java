package art.arcane.react.mod;

import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.ViewDistanceHost;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level distances on mods. Each level's chunk map takes its own distance, and the players in that level are
 * told the new radius, as Paper does for World#setViewDistance.
 */
final class ModViewDistanceHost implements ViewDistanceHost {
    private final MinecraftServer server;
    private final Map<UUID, Integer> viewDistances = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> simulationDistances = new ConcurrentHashMap<>();

    ModViewDistanceHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean supportsWorldDistance() {
        return true;
    }

    @Override
    public int serverViewDistance() {
        return server.getPlayerList().getViewDistance();
    }

    @Override
    public int serverSimulationDistance() {
        return server.getPlayerList().getSimulationDistance();
    }

    @Override
    public int viewDistance(ReactWorld world) {
        return viewDistances.getOrDefault(world.id(), serverViewDistance());
    }

    @Override
    public int simulationDistance(ReactWorld world) {
        return simulationDistances.getOrDefault(world.id(), serverSimulationDistance());
    }

    @Override
    public void setViewDistance(ReactWorld world, int distance) {
        ServerLevel level = (ServerLevel) world.handle();
        level.getChunkSource().setViewDistance(distance);
        remember(viewDistances, world.id(), distance, serverViewDistance());
        for (ServerPlayer player : List.copyOf(level.players())) {
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(distance));
        }
    }

    @Override
    public void setSimulationDistance(ReactWorld world, int distance) {
        ServerLevel level = (ServerLevel) world.handle();
        level.getChunkSource().setSimulationDistance(distance);
        remember(simulationDistances, world.id(), distance, serverSimulationDistance());
        for (ServerPlayer player : List.copyOf(level.players())) {
            player.connection.send(new ClientboundSetSimulationDistancePacket(distance));
        }
    }

    private static void remember(Map<UUID, Integer> distances, UUID worldId, int distance, int serverDistance) {
        if (distance == serverDistance) {
            distances.remove(worldId);
        } else {
            distances.put(worldId, distance);
        }
    }
}
