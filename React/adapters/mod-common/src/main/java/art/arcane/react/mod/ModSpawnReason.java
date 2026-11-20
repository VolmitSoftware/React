package art.arcane.react.mod;

import net.minecraft.world.entity.Entity;

/**
 * The vanilla spawn reason recorded on an entity when it was created or finalized, by the spawn reason mixins.
 */
public interface ModSpawnReason {
    String react$spawnReason();

    void react$setSpawnReason(String reason);

    static void record(Object entity, Enum<?> reason) {
        if (entity instanceof ModSpawnReason holder && reason != null) {
            holder.react$setSpawnReason(reason.name());
        }
    }

    static String of(Entity entity) {
        return entity instanceof ModSpawnReason holder ? holder.react$spawnReason() : null;
    }
}
