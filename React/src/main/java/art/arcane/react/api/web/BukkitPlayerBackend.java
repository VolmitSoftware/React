package art.arcane.react.api.web;

import art.arcane.react.React;
import art.arcane.react.api.web.dto.OnlinePlayerDto;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BukkitPlayerBackend implements PlayerBackend {

    private final SafeTeleportDestinationResolver destinationResolver;
    private final Set<UUID> activeTeleports;

    public BukkitPlayerBackend() {
        destinationResolver = new SafeTeleportDestinationResolver();
        activeTeleports = ConcurrentHashMap.newKeySet();
    }

    @Override
    public List<OnlinePlayerDto> list() {
        NearbyPlayerIndexController controller = React.controller(NearbyPlayerIndexController.class);
        if (controller == null) {
            return List.of();
        }
        List<OnlinePlayerDto> players = new ArrayList<>();
        for (NearbyPlayerIndexController.PlayerViewSnapshot snapshot : controller.playerSnapshots()) {
            if (snapshot.name() == null || snapshot.name().isBlank()) {
                continue;
            }
            players.add(new OnlinePlayerDto(snapshot.playerId().toString(), snapshot.name()));
        }
        players.sort(Comparator.comparing(OnlinePlayerDto::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(players);
    }

    @Override
    public TeleportResult queueTeleport(
        UUID playerId,
        String worldKey,
        int blockX,
        int blockZ
    ) {
        NearbyPlayerIndexController playerIndex = React.controller(NearbyPlayerIndexController.class);
        NearbyPlayerIndexController.PlayerViewSnapshot playerSnapshot = playerIndex == null
            ? null
            : playerIndex.playerSnapshot(playerId).orElse(null);
        if (playerSnapshot == null || playerSnapshot.name() == null || playerSnapshot.name().isBlank()) {
            return new TeleportResult(TeleportStatus.PLAYER_OFFLINE, "");
        }
        HeatmapWorldRef world = resolveWorld(worldKey);
        if (world == null) {
            return new TeleportResult(TeleportStatus.WORLD_UNAVAILABLE, playerSnapshot.name());
        }
        if (!contains(world, blockX + 0.5D, blockZ + 0.5D)) {
            return new TeleportResult(TeleportStatus.OUTSIDE_BORDER, playerSnapshot.name());
        }
        if (!activeTeleports.add(playerId)) {
            return new TeleportResult(TeleportStatus.REJECTED, playerSnapshot.name());
        }
        J.s(() -> dispatch(playerId, world, blockX, blockZ));
        return new TeleportResult(TeleportStatus.QUEUED, playerSnapshot.name());
    }

    private HeatmapWorldRef resolveWorld(String worldKey) {
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null) {
            return null;
        }
        for (HeatmapWorldRef world : observer.heatmapWorlds()) {
            if (world.worldKey().equals(worldKey)) {
                return world;
            }
        }
        return null;
    }

    private void dispatch(UUID playerId, HeatmapWorldRef worldRef, int blockX, int blockZ) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            activeTeleports.remove(playerId);
            React.verbose("Cancelled queued web teleport because player " + playerId + " is offline.");
            return;
        }
        Runnable retired = () -> fail(
            playerId,
            "Queued web teleport retired before player scheduling for " + playerId,
            null
        );
        J.runEntity(
            player,
            () -> {
                try {
                    dispatchFromPlayer(player, worldRef, blockX, blockZ);
                } catch (RuntimeException | Error failure) {
                    fail(playerId, "Failed to dispatch queued web teleport for " + playerId, failure);
                    throw failure;
                }
            },
            0,
            retired
        );
    }

    private void dispatchFromPlayer(
        Player player,
        HeatmapWorldRef worldRef,
        int blockX,
        int blockZ
    ) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline()) {
            activeTeleports.remove(playerId);
            return;
        }
        World world = Bukkit.getWorld(worldRef.worldId());
        if (world == null) {
            fail(
                playerId,
                "Queued web teleport world is no longer loaded: " + worldRef.worldKey(),
                null
            );
            return;
        }
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        if (!J.runChunk(
            world,
            chunkX,
            chunkZ,
            () -> resolveDestination(playerId, player, world, blockX, blockZ)
        )) {
            fail(
                playerId,
                "Failed to schedule web teleport destination lookup in " + worldRef.worldKey()
                    + " at " + blockX + "," + blockZ,
                null
            );
        }
    }

    private void resolveDestination(
        UUID playerId,
        Player player,
        World world,
        int blockX,
        int blockZ
    ) {
        HeatmapWorldRef liveWorld = snapshotWorld(world.getUID());
        if (liveWorld == null || !contains(liveWorld, blockX + 0.5D, blockZ + 0.5D)) {
            fail(
                playerId,
                "Cancelled queued web teleport outside the current world border at "
                    + blockX + "," + blockZ,
                null
            );
            return;
        }
        Location destination;
        try {
            destination = destinationResolver.resolve(world, blockX, blockZ);
        } catch (RuntimeException | Error failure) {
            fail(
                playerId,
                "Failed to resolve a safe web teleport destination at " + blockX + "," + blockZ,
                failure
            );
            throw failure;
        }
        if (destination == null) {
            fail(
                playerId,
                "No safe web teleport destination was found near " + blockX + "," + blockZ,
                null
            );
            return;
        }
        if (!contains(liveWorld, destination.getX(), destination.getZ())) {
            fail(
                playerId,
                "Safe web teleport destination fell outside the current world border at "
                    + destination.getBlockX() + "," + destination.getBlockZ(),
                null
            );
            return;
        }
        Runnable retired = () -> fail(
            playerId,
            "Queued web teleport retired before final player scheduling for " + playerId,
            null
        );
        if (!J.runEntity(
            player,
            () -> completeTeleport(playerId, player, destination, blockX, blockZ),
            0,
            retired
        )) {
            activeTeleports.remove(playerId);
        }
    }

    private void completeTeleport(
        UUID playerId,
        Player player,
        Location destination,
        int blockX,
        int blockZ
    ) {
        if (!player.isOnline()) {
            activeTeleports.remove(playerId);
            return;
        }
        CompletableFuture<Boolean> teleport;
        try {
            teleport = player.teleportAsync(
                destination,
                PlayerTeleportEvent.TeleportCause.PLUGIN
            );
        } catch (RuntimeException | Error failure) {
            fail(playerId, "Web teleport dispatch failed for " + playerId, failure);
            throw failure;
        }
        if (teleport == null) {
            fail(
                playerId,
                "Web teleportAsync returned no completion future for " + playerId,
                null
            );
            return;
        }
        teleport.whenComplete((Boolean teleported, Throwable failure) -> {
            activeTeleports.remove(playerId);
            if (failure != null) {
                React.reportError(new IllegalStateException(
                    "Web teleport failed for " + playerId + " at "
                        + blockX + "," + blockZ,
                    failure
                ));
            } else if (!Boolean.TRUE.equals(teleported)) {
                React.reportError(new IllegalStateException(
                    "Web teleport was rejected for " + playerId + " at "
                        + blockX + "," + blockZ
                ));
            }
        });
    }

    private void fail(UUID playerId, String message, Throwable cause) {
        activeTeleports.remove(playerId);
        React.reportError(cause == null
            ? new IllegalStateException(message)
            : new IllegalStateException(message, cause));
    }

    private HeatmapWorldRef snapshotWorld(UUID worldId) {
        ObserverController observer = React.controller(ObserverController.class);
        return observer == null ? null : observer.heatmapWorld(worldId).orElse(null);
    }

    static boolean contains(HeatmapWorldRef world, double blockX, double blockZ) {
        double size = world.borderSizeBlocks();
        if (!Double.isFinite(size) || size <= 0D) {
            return true;
        }
        double half = size / 2D;
        return blockX >= world.borderCenterBlockX() - half
            && blockX < world.borderCenterBlockX() + half
            && blockZ >= world.borderCenterBlockZ() - half
            && blockZ < world.borderCenterBlockZ() + half;
    }
}
