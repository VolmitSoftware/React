package art.arcane.react.mod;

import art.arcane.react.mod.mixin.ChunkMapViewAccess;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Player activity on mods, fed by the player mixins at the points where Bukkit fires its player events, plus the
 * per-player chunk send distance that the chunk map mixin caps.
 */
public final class ModPlayerHost implements PlayerHost {
    private static final double MOVE_THRESHOLD_SQUARED = 1D / 256D;
    private static final float TURN_THRESHOLD_DEGREES = 10F;
    private static final long GENERATED_READ_TIMEOUT_MS = 100L;
    private static final List<Listener<Move>> MOVES = new CopyOnWriteArrayList<>();
    private static final List<Listener<Teleport>> TELEPORTS = new CopyOnWriteArrayList<>();
    private static final List<Listener<Portal>> PORTALS = new CopyOnWriteArrayList<>();
    private static final List<Listener<ReactPlayer>> JOINS = new CopyOnWriteArrayList<>();
    private static final List<Listener<ReactPlayer>> QUITS = new CopyOnWriteArrayList<>();
    private static final List<Listener<PlayerHost.Cancellable>> INTERACTIONS = new CopyOnWriteArrayList<>();
    private static final List<Listener<PlayerHost.Cancellable>> ATTACKS = new CopyOnWriteArrayList<>();
    private static final List<Listener<PlayerHost.Cancellable>> CONSUMES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, ServerPlayer> ONLINE = new ConcurrentHashMap<>();
    private static final Map<UUID, LastMove> LAST_MOVES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SEND_DISTANCE_CAPS = new ConcurrentHashMap<>();
    private static volatile boolean readingNaturalDistance;
    private final MinecraftServer server;

    ModPlayerHost(MinecraftServer server) {
        this.server = server;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ONLINE.put(player.getUUID(), player);
        }
    }

    static void clear() {
        MOVES.clear();
        TELEPORTS.clear();
        PORTALS.clear();
        JOINS.clear();
        QUITS.clear();
        INTERACTIONS.clear();
        ATTACKS.clear();
        CONSUMES.clear();
        ONLINE.clear();
        LAST_MOVES.clear();
        SEND_DISTANCE_CAPS.clear();
    }

    @Override
    public List<ReactPlayer> players() {
        List<ReactPlayer> players = new ArrayList<>(ONLINE.size());
        for (ServerPlayer player : ONLINE.values()) {
            players.add(new ModReactPlayer(player));
        }
        return players;
    }

    @Override
    public boolean isOnline(ReactPlayer player) {
        return player != null && player.handle() instanceof ServerPlayer handle && !handle.hasDisconnected()
                && ONLINE.get(handle.getUUID()) == handle;
    }

    @Override
    public Subscription onMove(Listener<Move> listener) {
        return subscribe(MOVES, listener);
    }

    @Override
    public Subscription onTeleport(Listener<Teleport> listener) {
        return subscribe(TELEPORTS, listener);
    }

    @Override
    public Subscription onPortal(Listener<Portal> listener) {
        return subscribe(PORTALS, listener);
    }

    @Override
    public Subscription onJoin(Listener<ReactPlayer> listener) {
        return subscribe(JOINS, listener);
    }

    @Override
    public Subscription onQuit(Listener<ReactPlayer> listener) {
        return subscribe(QUITS, listener);
    }

    @Override
    public Subscription onInteract(Listener<PlayerHost.Cancellable> listener) {
        return subscribe(INTERACTIONS, listener);
    }

    @Override
    public Subscription onAttack(Listener<PlayerHost.Cancellable> listener) {
        return subscribe(ATTACKS, listener);
    }

    @Override
    public Subscription onConsume(Listener<PlayerHost.Cancellable> listener) {
        return subscribe(CONSUMES, listener);
    }

    @Override
    public boolean supportsSendViewDistance() {
        return server.overworld().getChunkSource().chunkMap instanceof ChunkMapViewAccess;
    }

    @Override
    public int sendViewDistance(ReactPlayer player) {
        ServerPlayer handle = online(player);
        int natural = naturalDistance(handle);
        Integer cap = SEND_DISTANCE_CAPS.get(handle.getUUID());
        return cap == null ? natural : Math.min(natural, cap);
    }

    @Override
    public void setSendViewDistance(ReactPlayer player, int distance) {
        ServerPlayer handle = online(player);
        if (!server.isSameThread()) {
            throw new IllegalStateException("Send view distance changes run on the server thread");
        }
        int natural = naturalDistance(handle);
        if (distance >= natural) {
            SEND_DISTANCE_CAPS.remove(handle.getUUID());
        } else {
            SEND_DISTANCE_CAPS.put(handle.getUUID(), Math.max(2, distance));
        }
        ((ChunkMapViewAccess) VanillaApi.level(handle).getChunkSource().chunkMap).react$updateChunkTracking(handle);
    }

    @Override
    public boolean isChunkGenerated(GameLocation location) {
        ServerLevel level = location == null ? null : ModWorldHost.level(server, location.worldId());
        if (level == null) {
            return true;
        }
        int chunkX = location.chunkX();
        int chunkZ = location.chunkZ();
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            return true;
        }
        try {
            return level.getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ))
                    .get(GENERATED_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS).isPresent();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return true;
        } catch (ExecutionException | TimeoutException unknown) {
            return true;
        }
    }

    /** The chunk map's own distance for the player, capped by React's send distance. */
    public static int capViewDistance(ServerPlayer player, int distance) {
        if (readingNaturalDistance || SEND_DISTANCE_CAPS.isEmpty()) {
            return distance;
        }
        Integer cap = SEND_DISTANCE_CAPS.get(player.getUUID());
        return cap == null ? distance : Math.min(distance, cap);
    }

    private static int naturalDistance(ServerPlayer player) {
        ChunkMap chunkMap = VanillaApi.level(player).getChunkSource().chunkMap;
        readingNaturalDistance = true;
        try {
            return ((ChunkMapViewAccess) chunkMap).react$playerViewDistance(player);
        } finally {
            readingNaturalDistance = false;
        }
    }

    private static ServerPlayer online(ReactPlayer player) {
        if (player == null || !(player.handle() instanceof ServerPlayer handle) || handle.hasDisconnected()) {
            throw new IllegalStateException("Player is not online");
        }
        return handle;
    }

    public static void joined(ServerPlayer player) {
        ONLINE.put(player.getUUID(), player);
        LAST_MOVES.put(player.getUUID(), LastMove.of(player));
        ReactPlayer react = new ModReactPlayer(player);
        for (Listener<ReactPlayer> listener : JOINS) {
            listener.on(react);
        }
    }

    public static void left(ServerPlayer player) {
        ReactPlayer react = new ModReactPlayer(player);
        for (Listener<ReactPlayer> listener : QUITS) {
            listener.on(react);
        }
        ONLINE.remove(player.getUUID(), player);
        LAST_MOVES.remove(player.getUUID());
        SEND_DISTANCE_CAPS.remove(player.getUUID());
    }

    /** True when a listener cancelled the move; the player is then sent back to their position before the packet. */
    public static boolean cancelsMove(ServerGamePacketListenerImpl connection, ServerPlayer player, ServerboundMovePlayerPacket packet) {
        if (MOVES.isEmpty() || player == null || player.hasDisconnected() || player.isPassenger()
                || !VanillaApi.level(player).getServer().isSameThread()) {
            return false;
        }
        LastMove last = LAST_MOVES.computeIfAbsent(player.getUUID(), (UUID ignored) -> LastMove.of(player));
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYRot(player.getYRot());
        float pitch = packet.getXRot(player.getXRot());
        double dx = x - last.x;
        double dy = y - last.y;
        double dz = z - last.z;
        boolean moved = !last.worldId.equals(ModReactWorld.idOf(VanillaApi.level(player)))
                || (dx * dx) + (dy * dy) + (dz * dz) > MOVE_THRESHOLD_SQUARED;
        boolean turned = Math.abs(yaw - last.yaw) + Math.abs(pitch - last.pitch) > TURN_THRESHOLD_DEGREES;
        if (!moved && !turned) {
            return false;
        }
        UUID worldId = ModReactWorld.idOf(VanillaApi.level(player));
        ModMove move = new ModMove(new ModReactPlayer(player), new GameLocation(worldId, player.getX(), player.getY(), player.getZ()),
                new GameLocation(worldId, x, y, z));
        for (Listener<Move> listener : MOVES) {
            listener.on(move);
        }
        if (move.cancelled) {
            connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return true;
        }
        LAST_MOVES.put(player.getUUID(), new LastMove(worldId, x, y, z, yaw, pitch));
        return false;
    }

    /** True when a listener cancelled a command or mod teleport of the player. */
    public static boolean cancelsTeleport(ServerPlayer player, ServerLevel level, Set<?> relatives, double x, double y, double z) {
        if (TELEPORTS.isEmpty() || player.hasDisconnected()) {
            return false;
        }
        GameLocation target = new GameLocation(ModReactWorld.idOf(level), absolute(relatives, "X", x, player.getX()),
                absolute(relatives, "Y", y, player.getY()), absolute(relatives, "Z", z, player.getZ()));
        ModTeleport teleport = new ModTeleport(new ModReactPlayer(player), target);
        for (Listener<Teleport> listener : TELEPORTS) {
            listener.on(teleport);
        }
        return teleport.cancelled;
    }

    private static double absolute(Set<?> relatives, String axis, double value, double current) {
        for (Object relative : relatives) {
            if (relative instanceof Enum<?> flag && flag.name().equals(axis)) {
                return current + value;
            }
        }
        return value;
    }

    /** True when a listener cancelled the portal trip of {@code entity} to {@code transition}. */
    public static boolean cancelsPortal(Entity entity, Object transition) {
        if (PORTALS.isEmpty() || transition == null) {
            return false;
        }
        ModPortal portal = new ModPortal(entity, transition);
        for (Listener<Portal> listener : PORTALS) {
            listener.on(portal);
        }
        return portal.cancelled;
    }

    /** True when a listener cancelled the block or item use. */
    public static boolean cancelsInteraction(ServerPlayer player) {
        return cancels(INTERACTIONS, player);
    }

    /** True when a listener cancelled damage dealt by a player, directly or with a projectile they shot. */
    public static boolean cancelsAttack(DamageSource source) {
        if (ATTACKS.isEmpty() || source == null) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        ServerPlayer player = direct instanceof ServerPlayer attacker ? attacker
                : direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer shooter ? shooter : null;
        return player != null && cancels(ATTACKS, player);
    }

    /** True when a listener cancelled the player finishing eating or drinking. */
    public static boolean cancelsConsume(ServerPlayer player) {
        return cancels(CONSUMES, player);
    }

    /** Resends the blocks around a cancelled interaction and the player's inventory, as Bukkit does after a cancel. */
    public static void resync(ServerPlayer player, BlockPos... positions) {
        ServerLevel level = VanillaApi.level(player);
        for (BlockPos pos : positions) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        }
        player.containerMenu.sendAllDataToRemote();
    }

    private static boolean cancels(List<Listener<PlayerHost.Cancellable>> listeners, ServerPlayer player) {
        if (listeners.isEmpty() || player.hasDisconnected()) {
            return false;
        }
        ModAction action = new ModAction(new ModReactPlayer(player));
        for (Listener<PlayerHost.Cancellable> listener : listeners) {
            listener.on(action);
        }
        return action.cancelled;
    }

    private static <T> Subscription subscribe(List<Listener<T>> listeners, Listener<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private record LastMove(UUID worldId, double x, double y, double z, float yaw, float pitch) {
        private static LastMove of(ServerPlayer player) {
            return new LastMove(ModReactWorld.idOf(VanillaApi.level(player)), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
    }

    private static class ModAction implements PlayerHost.Cancellable {
        private final ReactPlayer player;
        boolean cancelled;

        private ModAction(ReactPlayer player) {
            this.player = player;
        }

        @Override
        public ReactPlayer player() {
            return player;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class ModMove extends ModAction implements Move {
        private final GameLocation from;
        private final GameLocation to;

        private ModMove(ReactPlayer player, GameLocation from, GameLocation to) {
            super(player);
            this.from = from;
            this.to = to;
        }

        @Override
        public GameLocation from() {
            return from;
        }

        @Override
        public GameLocation to() {
            return to;
        }
    }

    private static final class ModTeleport extends ModAction implements Teleport {
        private final GameLocation to;

        private ModTeleport(ReactPlayer player, GameLocation to) {
            super(player);
            this.to = to;
        }

        @Override
        public GameLocation to() {
            return to;
        }
    }

    private static final class ModPortal implements Portal {
        private final Entity entity;
        private final Object transition;
        private boolean cancelled;

        private ModPortal(Entity entity, Object transition) {
            this.entity = entity;
            this.transition = transition;
        }

        @Override
        public UUID entityId() {
            return entity.getUUID();
        }

        @Override
        public boolean isPlayer() {
            return entity instanceof ServerPlayer;
        }

        @Override
        public Object handle() {
            return entity;
        }

        @Override
        public GameLocation destination() {
            return VanillaApi.portalDestination(transition);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean canTravel() {
            if (entity instanceof ServerPlayer player) {
                return !player.hasDisconnected() && player.isAlive();
            }
            return !entity.isRemoved() && entity.isAlive();
        }

        @Override
        public CompletableFuture<Boolean> travel() {
            return CompletableFuture.completedFuture(VanillaApi.travel(entity, transition));
        }
    }
}
