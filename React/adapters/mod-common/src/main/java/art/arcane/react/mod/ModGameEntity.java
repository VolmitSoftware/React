package art.arcane.react.mod;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ModGameEntity(Entity entity) implements GameEntity {
    public ModGameEntity {
        Objects.requireNonNull(entity, "entity");
    }

    /** The type in React's config naming: the Bukkit constant for vanilla types, the namespaced id otherwise. */
    public static String typeName(EntityType<?> type) {
        Identifier key = EntityType.getKey(type);
        return "minecraft".equals(key.getNamespace()) ? key.getPath().toUpperCase(Locale.ROOT) : key.toString();
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
}
