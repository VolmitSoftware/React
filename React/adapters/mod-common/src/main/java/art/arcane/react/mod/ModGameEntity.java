package art.arcane.react.mod;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ModGameEntity(Entity entity) implements GameEntity {
    public ModGameEntity {
        Objects.requireNonNull(entity, "entity");
    }

    /** The type in React's config naming: the Bukkit constant for vanilla types, the namespaced id otherwise. */
    public static String typeName(EntityType<?> type) {
        String namespace = VanillaApi.namespace(type);
        String path = VanillaApi.path(type);
        return "minecraft".equals(namespace) ? path.toUpperCase(Locale.ROOT) : namespace + ":" + path;
    }

    public static GameLocation location(Entity entity) {
        return entity.level() instanceof ServerLevel level
                ? new GameLocation(ModReactWorld.idOf(level), entity.getX(), entity.getY(), entity.getZ())
                : null;
    }

    @Override
    public UUID id() {
        return entity.getUUID();
    }

    @Override
    public String type() {
        return typeName(entity.getType());
    }

    @Override
    public GameLocation location() {
        return location(entity);
    }

    @Override
    public boolean isValid() {
        return entity.isAlive() && !entity.isRemoved();
    }

    @Override
    public int ticksLived() {
        return entity.tickCount;
    }

    @Override
    public boolean hasCustomName() {
        return entity.hasCustomName();
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
    public boolean isLiving() {
        return entity instanceof LivingEntity;
    }

    @Override
    public boolean isMob() {
        return entity instanceof Mob;
    }

    @Override
    public boolean isMonster() {
        return entity instanceof Monster;
    }

    @Override
    public double speed() {
        return entity.getDeltaMovement().length();
    }

    @Override
    public double health() {
        return entity instanceof LivingEntity living ? living.getHealth() : 0D;
    }

    @Override
    public double maxHealth() {
        return entity instanceof LivingEntity living ? living.getMaxHealth() : Double.NaN;
    }

    @Override
    public boolean isTameable() {
        return VanillaApi.isTameable(entity);
    }

    @Override
    public boolean isTamed() {
        return VanillaApi.isTamed(entity);
    }

    @Override
    public boolean isBreedable() {
        return entity instanceof AgeableMob;
    }

    @Override
    public boolean isAdult() {
        return !(entity instanceof AgeableMob ageable) || !ageable.isBaby();
    }
}
