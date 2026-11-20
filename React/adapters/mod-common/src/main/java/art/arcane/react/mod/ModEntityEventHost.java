package art.arcane.react.mod;

import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Entity damage and projectile launches on mods, fed by the living entity and server level mixins. */
public final class ModEntityEventHost implements EntityEventHost {
    private static final List<Consumer<Damage>> DAMAGE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Launch>> LAUNCH = new CopyOnWriteArrayList<>();
    private final ModWorldHost worldHost;
    private final ModEntityKiller killer;

    ModEntityEventHost(ModWorldHost worldHost, ModEntityKiller killer) {
        this.worldHost = worldHost;
        this.killer = killer;
    }

    static void clear() {
        DAMAGE.clear();
        LAUNCH.clear();
    }

    @Override
    public Subscription onDamage(Consumer<Damage> listener) {
        DAMAGE.add(listener);
        return () -> DAMAGE.remove(listener);
    }

    @Override
    public Subscription onLaunch(Consumer<Launch> listener) {
        LAUNCH.add(listener);
        return () -> LAUNCH.remove(listener);
    }

    public static void damaged(LivingEntity entity, DamageSource source) {
        if (DAMAGE.isEmpty() || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        ModDamage damage = new ModDamage(new ModGameEntity(entity), source.is(DamageTypes.ON_FIRE));
        for (Consumer<Damage> listener : DAMAGE) {
            listener.accept(damage);
        }
    }

    /** False when a listener cancelled the launch of {@code entity}. */
    public static boolean allowLaunch(Entity entity) {
        if (LAUNCH.isEmpty() || !(entity instanceof Projectile projectile) || !(entity.level() instanceof ServerLevel)) {
            return true;
        }
        Entity owner = projectile.getOwner();
        ModLaunch launch = new ModLaunch(new ModGameEntity(entity),
                owner instanceof ServerPlayer player ? Optional.of(new ModReactPlayer(player)) : Optional.empty());
        for (Consumer<Launch> listener : LAUNCH) {
            if (launch.cancelled) {
                return false;
            }
            listener.accept(launch);
        }
        return !launch.cancelled;
    }

    @Override
    public boolean isMonster(GameEntity entity) {
        return handle(entity) instanceof Monster;
    }

    @Override
    public boolean isBubbled(GameEntity entity) {
        Entity handle = handle(entity);
        if (handle == null) {
            return false;
        }
        BlockPos pos = handle.blockPosition();
        BlockState block = handle.level().getBlockState(pos);
        BlockState below = handle.level().getBlockState(pos.below());
        if (!below.liquid() && !block.liquid()) {
            return false;
        }
        return isBlock(block, below, Blocks.BUBBLE_COLUMN) || isBlock(block, below, Blocks.SOUL_SAND);
    }

    private static boolean isBlock(BlockState block, BlockState below, Block type) {
        return block.getBlock() == type || below.getBlock() == type;
    }

    @Override
    public boolean nearPlayer(GameEntity entity, double radius) {
        return entity != null && worldHost.anyPlayerNear(entity.location(), radius);
    }

    @Override
    public void removeLater(GameEntity entity, int seconds) {
        Entity handle = handle(entity);
        if (handle != null) {
            killer.kill(handle, seconds);
        }
    }

    private static Entity handle(GameEntity entity) {
        return entity != null && entity.handle() instanceof Entity handle ? handle : null;
    }

    private static final class ModDamage implements Damage {
        private final GameEntity entity;
        private final boolean fireTick;

        private ModDamage(GameEntity entity, boolean fireTick) {
            this.entity = entity;
            this.fireTick = fireTick;
        }

        @Override
        public GameEntity entity() {
            return entity;
        }

        @Override
        public boolean isFireTick() {
            return fireTick;
        }
    }

    private static final class ModLaunch implements Launch {
        private final GameEntity projectile;
        private final Optional<ReactPlayer> shooter;
        private boolean cancelled;

        private ModLaunch(GameEntity projectile, Optional<ReactPlayer> shooter) {
            this.projectile = projectile;
            this.shooter = shooter;
        }

        @Override
        public GameEntity projectile() {
            return projectile;
        }

        @Override
        public Optional<ReactPlayer> shooter() {
            return shooter;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
