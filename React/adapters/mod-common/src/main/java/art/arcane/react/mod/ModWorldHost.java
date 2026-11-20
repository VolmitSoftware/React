package art.arcane.react.mod;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Levels and players, read live on the server thread; mods have no separate player index, so it is always ready. */
final class ModWorldHost implements WorldHost {
    private final MinecraftServer server;

    ModWorldHost(MinecraftServer server) {
        this.server = server;
    }

    static ServerLevel level(MinecraftServer server, UUID worldId) {
        if (worldId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.idOf(level).equals(worldId)) {
                return level;
            }
        }
        return null;
    }

    @Override
    public List<ReactWorld> worlds() {
        List<ReactWorld> worlds = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            worlds.add(new ModReactWorld(level));
        }
        return worlds;
    }

    @Override
    public Optional<ReactWorld> world(UUID worldId) {
        ServerLevel level = level(server, worldId);
        return level == null ? Optional.empty() : Optional.of(new ModReactWorld(level));
    }

    @Override
    public String worldKey(ReactWorld world) {
        return ModReactWorld.nameOf((ServerLevel) world.handle());
    }

    @Override
    public List<ReactPlayer> onlinePlayers() {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<ReactPlayer> result = new ArrayList<>(players.size());
        for (ServerPlayer player : List.copyOf(players)) {
            result.add(new ModReactPlayer(player));
        }
        return result;
    }

    @Override
    public int onlinePlayerCount() {
        return server.getPlayerCount();
    }

    @Override
    public boolean isOnline(ReactPlayer player) {
        return player != null && player.handle() instanceof ServerPlayer handle && !handle.hasDisconnected()
                && server.getPlayerList().getPlayer(handle.getUUID()) == handle;
    }

    @Override
    public Optional<GameLocation> location(ReactPlayer player) {
        return player != null && player.handle() instanceof ServerPlayer handle
                ? Optional.ofNullable(ModGameEntity.location(handle))
                : Optional.empty();
    }

    @Override
    public boolean anyPlayerNear(GameLocation location, double radius) {
        if (location == null || radius <= 0) {
            return false;
        }
        ServerLevel level = level(server, location.worldId());
        if (level == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (ServerPlayer player : List.copyOf(level.players())) {
            double dx = player.getX() - location.x();
            double dy = player.getY() - location.y();
            double dz = player.getZ() - location.z();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPlayerIndexReady() {
        return true;
    }

    @Override
    public Optional<List<PlayerPosition>> indexedPlayers() {
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        List<PlayerPosition> positions = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            positions.add(position(player));
        }
        return Optional.of(positions);
    }

    @Override
    public Optional<PlayerPosition> indexedPlayer(UUID playerId) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        return player == null ? Optional.empty() : Optional.of(position(player));
    }

    private static PlayerPosition position(ServerPlayer player) {
        UUID worldId = player.level() instanceof ServerLevel level ? ModReactWorld.idOf(level) : null;
        return new PlayerPosition(player.getUUID(), worldId, player.getX(), player.getY(), player.getZ());
    }
}
