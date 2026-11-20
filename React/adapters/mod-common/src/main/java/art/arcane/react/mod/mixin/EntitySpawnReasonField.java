package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSpawnReason;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntitySpawnReasonField implements ModSpawnReason {
    @Unique
    private String react$spawnReason;

    @Override
    public String react$spawnReason() {
        return react$spawnReason;
    }

    @Override
    public void react$setSpawnReason(String reason) {
        react$spawnReason = reason;
    }
}
