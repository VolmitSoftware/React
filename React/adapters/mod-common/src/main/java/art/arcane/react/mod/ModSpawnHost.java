package art.arcane.react.mod;

import art.arcane.react.api.protect.internal.ModProtectionEngine;
import art.arcane.react.mod.mixin.BaseSpawnerAccess;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HostListeners;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Spawns on mods, fed by {@code ServerLevelHooks} where an entity is about to join a level, and by the player drop
 * hook. Spawn reasons come from the spawn context the spawner and breeding hooks open, then from the vanilla reason
 * recorded on the entity, and use Bukkit's names.
 */
public final class ModSpawnHost implements SpawnHost {
    private static final HostListeners<Spawn> CREATURES = new HostListeners<>();
    private static final HostListeners<EntitySpawn> ENTITIES = new HostListeners<>();
    private static final HostListeners<Breed> BREEDS = new HostListeners<>();
    private static final HostListeners<ItemDrop> DROPS = new HostListeners<>();
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private final MinecraftServer server;

    public ModSpawnHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Subscription onCreatureSpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<Spawn> listener) {
        return CREATURES.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onEntitySpawn(ListenerPriority priority, boolean ignoreCancelled, Consumer<EntitySpawn> listener) {
        return ENTITIES.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onBreed(ListenerPriority priority, boolean ignoreCancelled, Consumer<Breed> listener) {
        return BREEDS.subscribe(priority, ignoreCancelled, listener);
    }

    @Override
    public Subscription onPlayerDropItem(ListenerPriority priority, boolean ignoreCancelled, Consumer<ItemDrop> listener) {
        return DROPS.subscribe(priority, ignoreCancelled, listener);
    }

    static void clear() {
        CREATURES.clear();
        ENTITIES.clear();
        BREEDS.clear();
        DROPS.clear();
    }

    /** Opens a spawn context, such as a spawner tick, that names the reason of every entity added inside it. */
    public static void enter(String vanillaReason) {
        CONTEXT.set(vanillaReason);
    }

    public static void exit() {
        CONTEXT.remove();
    }

    /** False when a listener cancelled the entity joining the level. */
    public static boolean allowAdd(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
            if (CREATURES.isEmpty() && BREEDS.isEmpty()) {
                return true;
            }
            String reason = bukkitReason(vanillaReason(entity));
            if ("BREEDING".equals(reason) && !BREEDS.isEmpty() && !BREEDS.dispatch(new ModBreed(entity))) {
                return false;
            }
            return CREATURES.isEmpty() || CREATURES.dispatch(new ModSpawn(entity, reason));
        }
        return ENTITIES.isEmpty() || ENTITIES.dispatch(new ModEntitySpawn(entity));
    }

    /** False when a listener cancelled the drop and the item went back into the player's inventory. */
    public static boolean allowDrop(ServerPlayer player, ItemStack stack) {
        if (DROPS.isEmpty() || stack == null || stack.isEmpty()) {
            return true;
        }
        if (DROPS.dispatch(new ModItemDrop(player))) {
            return true;
        }
        return !player.getInventory().add(stack);
    }

    private static String vanillaReason(Entity entity) {
        String context = CONTEXT.get();
        if (context != null) {
            return context;
        }
        return ModSpawnReason.of(entity);
    }

    static String bukkitReason(String vanillaReason) {
        if (vanillaReason == null) {
            return "DEFAULT";
        }
        switch (vanillaReason) {
            case "NATURAL":
                return "NATURAL";
            case "CHUNK_GENERATION":
            case "STRUCTURE":
                return "CHUNK_GEN";
            case "SPAWNER":
                return "SPAWNER";
            case "TRIAL_SPAWNER":
                return "TRIAL_SPAWNER";
            case "BREEDING":
                return "BREEDING";
            case "JOCKEY":
                return "JOCKEY";
            case "EVENT":
                return "RAID";
            case "REINFORCEMENT":
                return "REINFORCEMENTS";
            case "PATROL":
                return "PATROL";
            case "BUCKET":
                return "BUCKET";
            case "SPAWN_EGG":
            case "SPAWN_ITEM_USE":
                return "SPAWNER_EGG";
            case "DISPENSER":
                return "DISPENSE_EGG";
            case "COMMAND":
                return "COMMAND";
            default:
                return "DEFAULT";
        }
    }

    @Override
    public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ, boolean countItems) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null || !level.getChunkSource().hasChunk(chunkX, chunkZ)
                || !level.areEntitiesLoaded(VanillaApi.pack(chunkX, chunkZ))) {
            return -1;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB box = new AABB(minX, VanillaApi.minY(level), minZ, minX + 16, VanillaApi.maxY(level) + 1, minZ + 16);
        List<Entity> entities = level.getEntities((Entity) null, box, (Entity entity) -> entity.getBlockX() >> 4 == chunkX
                && entity.getBlockZ() >> 4 == chunkZ && (countItems || !(entity instanceof ItemEntity)));
        return entities.size();
    }

    @Override
    public boolean spawnProtected(String type, GameLocation location, String reason) {
        ModProtectionEngine engine = ModProtectionEngine.active();
        if (engine == null || location == null || type == null) {
            return false;
        }
        ServerLevel level = ModWorldHost.level(server, location.worldId());
        String typeId = type.indexOf(':') >= 0 ? type : "minecraft:" + type.toLowerCase(Locale.ROOT);
        return engine.spawnProtected(typeId, level == null ? "" : ModReactWorld.nameOf(level), reason);
    }

    @Override
    public void backoffSpawners(GameLocation location, int delayTicks) {
        ServerLevel level = location == null ? null : ModWorldHost.level(server, location.worldId());
        if (level == null) {
            return;
        }
        int blockX = location.blockX();
        int blockY = location.blockY();
        int blockZ = location.blockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 1; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    int worldX = blockX + x;
                    int worldZ = blockZ + z;
                    if (!level.getChunkSource().hasChunk(worldX >> 4, worldZ >> 4)) {
                        continue;
                    }
                    pos.set(worldX, blockY + y, worldZ);
                    if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                        BaseSpawnerAccess access = (BaseSpawnerAccess) spawner.getSpawner();
                        access.react$setSpawnDelay(Math.max(access.react$spawnDelay(), delayTicks));
                        spawner.setChanged();
                    }
                }
            }
        }
    }

    @Override
    public void forEachDarkAir(GameLocation center, int horizontalRadius, int verticalRadius, int maxLight, CellVisitor visitor) {
        ServerLevel level = center == null ? null : ModWorldHost.level(server, center.worldId());
        if (level == null) {
            return;
        }
        int centerX = center.blockX();
        int centerY = center.blockY();
        int centerZ = center.blockZ();
        int minY = VanillaApi.minY(level);
        int maxY = VanillaApi.maxY(level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
            int y = centerY + dy;
            if (y < minY || y > maxY) {
                continue;
            }
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir() && level.getMaxLocalRawBrightness(pos) <= maxLight) {
                        visitor.visit(x, y, z);
                    }
                }
            }
        }
    }

    private static GameLocation at(Entity entity) {
        return entity.level() instanceof ServerLevel level
                ? new GameLocation(ModReactWorld.idOf(level), entity.getX(), entity.getY(), entity.getZ())
                : null;
    }

    private abstract static class ModEvent {
        private boolean cancelled;

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            cancelled = true;
        }
    }

    private static final class ModSpawn extends ModEvent implements Spawn {
        private final Entity entity;
        private final String reason;

        private ModSpawn(Entity entity, String reason) {
            this.entity = entity;
            this.reason = reason;
        }

        @Override
        public GameEntity entity() {
            return new ModGameEntity(entity);
        }

        @Override
        public GameLocation location() {
            return at(entity);
        }

        @Override
        public String type() {
            return ModGameEntity.typeName(entity.getType());
        }

        @Override
        public String reason() {
            return reason;
        }

        @Override
        public boolean isMonster() {
            return entity instanceof Monster;
        }

        @Override
        public boolean hasCustomName() {
            return entity.hasCustomName() && entity.getCustomName() != null && !entity.getCustomName().getString().isBlank();
        }
    }

    private static final class ModEntitySpawn extends ModEvent implements EntitySpawn {
        private final Entity entity;

        private ModEntitySpawn(Entity entity) {
            this.entity = entity;
        }

        @Override
        public GameLocation location() {
            return at(entity);
        }

        @Override
        public String type() {
            return ModGameEntity.typeName(entity.getType());
        }

        @Override
        public boolean isItem() {
            return entity instanceof ItemEntity;
        }

        @Override
        public boolean precedesAdd() {
            return false;
        }
    }

    private static final class ModBreed extends ModEvent implements Breed {
        private final Entity child;

        private ModBreed(Entity child) {
            this.child = child;
        }

        @Override
        public GameLocation location() {
            return at(child);
        }

        @Override
        public String type() {
            return ModGameEntity.typeName(child.getType());
        }
    }

    private static final class ModItemDrop extends ModEvent implements ItemDrop {
        private final ServerPlayer player;

        private ModItemDrop(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public GameLocation location() {
            return at(player);
        }
    }
}
