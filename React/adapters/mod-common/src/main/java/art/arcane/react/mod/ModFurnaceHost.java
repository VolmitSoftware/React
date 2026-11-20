package art.arcane.react.mod;

import art.arcane.react.mod.mixin.BrewingAccess;
import art.arcane.react.mod.mixin.FurnaceAccess;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Furnace and brewing stand ticks on mods, fed by the tick mixins with the same skip and catch-up rules React's
 * Bukkit agent applies.
 */
public final class ModFurnaceHost implements FurnaceHost {
    private static final List<Consumer<Station>> ACTIVITY = new CopyOnWriteArrayList<>();
    private static final Map<ServerLevel, UUID> WORLD_IDS = new ConcurrentHashMap<>();
    private static volatile TickHook furnaceHook;
    private static volatile TickHook brewingHook;
    private static int litBefore;
    private static int cookBefore;

    private final MinecraftServer server;

    public ModFurnaceHost(MinecraftServer server) {
        this.server = server;
    }

    static void clear() {
        ACTIVITY.clear();
        WORLD_IDS.clear();
        furnaceHook = null;
        brewingHook = null;
    }

    @Override
    public Subscription hookTicks(TickHook furnace, TickHook brewing, Consumer<Boolean> installed) {
        furnaceHook = furnace;
        brewingHook = brewing;
        installed.accept(true);
        return () -> {
            if (furnaceHook == furnace) {
                furnaceHook = null;
            }
            if (brewingHook == brewing) {
                brewingHook = null;
            }
        };
    }

    @Override
    public Subscription onActivity(Consumer<Station> listener) {
        ACTIVITY.add(listener);
        return () -> ACTIVITY.remove(listener);
    }

    /** True when the furnace skips this tick. */
    public static boolean beforeFurnaceTick(ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity entity) {
        FurnaceAccess access = (FurnaceAccess) entity;
        if (!ACTIVITY.isEmpty()) {
            litBefore = access.react$litTime();
            cookBefore = access.react$cookingTimer();
        }
        TickHook hook = furnaceHook;
        if (hook == null) {
            return false;
        }
        int decision;
        try {
            decision = hook.decide(worldId(level), pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException failure) {
            return false;
        }
        if (decision == SKIP) {
            return true;
        }
        if (decision > 0) {
            int cookTotal = access.react$cookingTotalTime();
            if (cookTotal > 0) {
                access.react$setCookingTimer(Math.min(Math.max(0, cookTotal - 1), access.react$cookingTimer() + decision));
            }
            int litRemaining = access.react$litTime();
            if (litRemaining > 0) {
                access.react$setLitTime(Math.max(1, litRemaining - decision));
            }
            if (!ACTIVITY.isEmpty()) {
                litBefore = access.react$litTime();
                cookBefore = access.react$cookingTimer();
            }
        }
        return false;
    }

    /** Reports fuel starting to burn and an item finishing, where Bukkit fires its furnace burn and smelt events. */
    public static void afterFurnaceTick(ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity entity) {
        if (ACTIVITY.isEmpty()) {
            return;
        }
        FurnaceAccess access = (FurnaceAccess) entity;
        int lit = access.react$litTime();
        boolean burned = lit > 0 && lit >= litBefore;
        boolean smelted = cookBefore > 0 && access.react$cookingTimer() == 0 && lit > 0;
        if (burned || smelted) {
            fire(new Station(worldId(level), pos.getX(), pos.getY(), pos.getZ(), Kind.FURNACE));
        }
    }

    /** True when the brewing stand skips this tick. */
    public static boolean beforeBrewingTick(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity) {
        TickHook hook = brewingHook;
        if (hook == null) {
            return false;
        }
        int decision;
        try {
            decision = hook.decide(worldId(level), pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException failure) {
            return false;
        }
        if (decision == SKIP) {
            return true;
        }
        if (decision > 0) {
            BrewingAccess access = (BrewingAccess) entity;
            int brewTime = access.react$brewTime();
            if (brewTime > 0) {
                access.react$setBrewTime(Math.max(1, brewTime - decision));
            }
        }
        return false;
    }

    /** Reports a furnace or brewing stand a player opened or a hopper moved items through. */
    public static void touch(Object container) {
        if (ACTIVITY.isEmpty() || !(container instanceof BlockEntity block)) {
            return;
        }
        Kind kind = kind(block);
        if (kind != null && block.getLevel() instanceof ServerLevel level) {
            BlockPos pos = block.getBlockPos();
            fire(new Station(worldId(level), pos.getX(), pos.getY(), pos.getZ(), kind));
        }
    }

    private static void fire(Station station) {
        for (Consumer<Station> listener : ACTIVITY) {
            listener.accept(station);
        }
    }

    private static Kind kind(BlockEntity block) {
        if (block instanceof AbstractFurnaceBlockEntity) {
            return Kind.FURNACE;
        }
        if (block instanceof BrewingStandBlockEntity) {
            return Kind.BREWING_STAND;
        }
        return null;
    }

    private static UUID worldId(ServerLevel level) {
        UUID id = WORLD_IDS.get(level);
        if (id == null) {
            id = ModReactWorld.idOf(level);
            WORLD_IDS.put(level, id);
        }
        return id;
    }

    @Override
    public List<Station> scan(ChunkRef ref) {
        ServerLevel level = ModWorldHost.level(server, ref.worldId());
        LevelChunk chunk = level == null ? null : level.getChunkSource().getChunkNow(ref.chunkX(), ref.chunkZ());
        if (chunk == null) {
            return List.of();
        }
        List<Station> stations = new ArrayList<>();
        for (BlockEntity block : chunk.getBlockEntities().values()) {
            Kind kind = kind(block);
            if (kind != null) {
                BlockPos pos = block.getBlockPos();
                stations.add(new Station(ref.worldId(), pos.getX(), pos.getY(), pos.getZ(), kind));
            }
        }
        return stations;
    }

    @Override
    public Optional<FurnaceState> furnace(UUID worldId, int x, int y, int z) {
        if (!(blockEntity(worldId, x, y, z) instanceof AbstractFurnaceBlockEntity furnace)) {
            return Optional.empty();
        }
        FurnaceAccess access = (FurnaceAccess) furnace;
        NonNullList<ItemStack> items = access.react$items();
        return Optional.of(new FurnaceState(blockType(furnace), access.react$litTime(), access.react$cookingTimer(),
                access.react$cookingTotalTime(), !items.get(0).isEmpty(), !items.get(1).isEmpty()));
    }

    @Override
    public Optional<BrewingState> brewing(UUID worldId, int x, int y, int z) {
        if (!(blockEntity(worldId, x, y, z) instanceof BrewingStandBlockEntity stand)) {
            return Optional.empty();
        }
        BrewingAccess access = (BrewingAccess) stand;
        NonNullList<ItemStack> items = access.react$items();
        boolean hasBottle = !items.get(0).isEmpty() || !items.get(1).isEmpty() || !items.get(2).isEmpty();
        return Optional.of(new BrewingState(access.react$fuel(), access.react$brewTime(), !items.get(3).isEmpty(), hasBottle));
    }

    private static String blockType(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof BlastFurnaceBlockEntity) {
            return "BLAST_FURNACE";
        }
        return furnace instanceof SmokerBlockEntity ? "SMOKER" : "FURNACE";
    }

    private BlockEntity blockEntity(UUID worldId, int x, int y, int z) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null || level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(x, y, z));
    }
}
