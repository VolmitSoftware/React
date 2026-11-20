package art.arcane.react.mod;

import art.arcane.react.mod.mixin.PathNavigationAccess;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityRanges;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Entity activity on mods. Wake and chunk entity events come from React's mixins: a successful
 * {@code LivingEntity} hurt, a changed {@code Mob#setTarget}, and level tracking start and end. Navigation budgets
 * drive vanilla {@link PathNavigation}'s visited-node multiplier; entity ranges are React's own per-level table.
 */
public final class ModEntityActivityHost implements EntityActivityHost {
    private static final List<Consumer<GameEntity>> DAMAGE = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<GameEntity, GameEntity>> TARGET = new CopyOnWriteArrayList<>();
    private static final List<Consumer<List<GameEntity>>> LOAD = new CopyOnWriteArrayList<>();
    private static final List<Consumer<List<GameEntity>>> UNLOAD = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> cursors = new ConcurrentHashMap<>();

    static void clear() {
        DAMAGE.clear();
        TARGET.clear();
        LOAD.clear();
        UNLOAD.clear();
        ModEntityRanges.clear();
    }

    public static void damaged(Entity entity) {
        if (DAMAGE.isEmpty()) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(entity);
        for (Consumer<GameEntity> listener : DAMAGE) {
            listener.accept(wrapped);
        }
    }

    public static void targeted(Mob mob, LivingEntity target) {
        if (TARGET.isEmpty() || mob.getTarget() == target) {
            return;
        }
        GameEntity wrapped = new ModGameEntity(mob);
        GameEntity wrappedTarget = target == null ? null : new ModGameEntity(target);
        for (BiConsumer<GameEntity, GameEntity> listener : TARGET) {
            listener.accept(wrapped, wrappedTarget);
        }
    }

    public static void trackingStarted(Entity entity) {
        dispatch(LOAD, entity);
    }

    public static void trackingEnded(Entity entity) {
        dispatch(UNLOAD, entity);
    }

    private static void dispatch(List<Consumer<List<GameEntity>>> listeners, Entity entity) {
        if (listeners.isEmpty() || entity == null) {
            return;
        }
        List<GameEntity> wrapped = List.of(new ModGameEntity(entity));
        for (Consumer<List<GameEntity>> listener : listeners) {
            listener.accept(wrapped);
        }
    }

    @Override
    public List<GameEntity> nextEntities(ReactWorld world, int budget) {
        if (!(world.handle() instanceof ServerLevel level) || budget <= 0) {
            return List.of();
        }
        List<Entity> all = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null) {
                all.add(entity);
            }
        }
        if (all.isEmpty()) {
            return List.of();
        }
        UUID worldId = world.id();
        int start = Math.floorMod(cursors.getOrDefault(worldId, 0), all.size());
        int count = Math.min(budget, all.size());
        List<GameEntity> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new ModGameEntity(all.get((start + index) % all.size())));
        }
        cursors.put(worldId, (start + count) % all.size());
        return result;
    }

    @Override
    public List<GameEntity> sampleNearby(ReactPlayer player, String owner, double horizontalRadius, double verticalRadius,
                                         int limit, Predicate<GameEntity> accept) {
        if (limit <= 0 || player == null || !(player.handle() instanceof ServerPlayer handle)
                || !(handle.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<GameEntity> result = new ArrayList<>();
        for (Entity entity : level.getEntities(handle, handle.getBoundingBox().inflate(horizontalRadius, verticalRadius,
                horizontalRadius), (Entity candidate) -> true)) {
            if (result.size() >= limit) {
                break;
            }
            GameEntity wrapped = new ModGameEntity(entity);
            if (accept == null || accept.test(wrapped)) {
                result.add(wrapped);
            }
        }
        return result;
    }

    @Override
    public boolean isNamed(GameEntity entity) {
        Entity handle = (Entity) entity.handle();
        return handle.hasCustomName() && handle.getCustomName() != null && !handle.getCustomName().getString().isBlank();
    }

    @Override
    public boolean isVillager(GameEntity entity) {
        return "VILLAGER".equals(entity.type());
    }

    @Override
    public boolean isBoss(GameEntity entity) {
        String type = entity.type();
        return "ENDER_DRAGON".equals(type) || "WITHER".equals(type) || "WARDEN".equals(type);
    }

    @Override
    public boolean isPersistent(GameEntity entity) {
        return ((Entity) entity.handle()).getType().canSerialize();
    }

    @Override
    public int entityId(GameEntity entity) {
        return ((Entity) entity.handle()).getId();
    }

    @Override
    public boolean supportsAwareness() {
        return true;
    }

    @Override
    public Subscription onDamage(Consumer<GameEntity> listener) {
        return subscribe(DAMAGE, listener);
    }

    @Override
    public Subscription onTarget(BiConsumer<GameEntity, GameEntity> listener) {
        return subscribe(TARGET, listener);
    }

    @Override
    public Subscription onEntitiesLoad(Consumer<List<GameEntity>> listener) {
        return subscribe(LOAD, listener);
    }

    @Override
    public Subscription onEntitiesUnload(Consumer<List<GameEntity>> listener) {
        return subscribe(UNLOAD, listener);
    }

    @Override
    public boolean supportsNavigationBudget() {
        return true;
    }

    @Override
    public boolean setNavigationBudget(GameEntity mob, float multiplier) {
        if (!(mob.handle() instanceof Mob handle)) {
            return false;
        }
        PathNavigation navigation = handle.getNavigation();
        if (navigation == null) {
            return false;
        }
        navigation.setMaxVisitedNodesMultiplier(multiplier);
        return true;
    }

    @Override
    public void resetNavigationBudget(GameEntity mob) {
        if (mob.handle() instanceof Mob handle && handle.getNavigation() != null) {
            handle.getNavigation().resetMaxVisitedNodesMultiplier();
        }
    }

    @Override
    public boolean hasNavigationBudget(GameEntity mob) {
        return mob.handle() instanceof Mob handle && handle.getNavigation() instanceof PathNavigationAccess access
                && access.react$maxVisitedNodesMultiplier() != 1.0F;
    }

    @Override
    public Optional<EntityRanges> entityRanges(ReactWorld world) {
        return world.handle() instanceof ServerLevel level ? Optional.of(ModEntityRanges.of(level)) : Optional.empty();
    }

    private static <T> Subscription subscribe(List<T> listeners, T listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
