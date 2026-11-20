package art.arcane.react.mod;

import art.arcane.react.api.protect.internal.ProtectionEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/** Vanilla entities for {@code ReactModProtection}; claims live in the entity's scoreboard tags. */
final class ModProtectionEntities implements ProtectionEntities {
    @Override
    public boolean isEntity(Object entity) {
        return entity instanceof Entity;
    }

    @Override
    public String typeId(Object entity) {
        EntityType<?> type = ((Entity) entity).getType();
        return VanillaApi.namespace(type) + ":" + VanillaApi.path(type);
    }

    @Override
    public String worldName(Object entity) {
        return ((Entity) entity).level() instanceof ServerLevel level ? ModReactWorld.nameOf(level) : "";
    }

    @Override
    public Set<String> tags(Object entity) {
        return VanillaApi.tags((Entity) entity);
    }

    @Override
    public boolean addTag(Object entity, String tag) {
        return ((Entity) entity).addTag(tag);
    }

    @Override
    public boolean removeTag(Object entity, String tag) {
        return ((Entity) entity).removeTag(tag);
    }
}
