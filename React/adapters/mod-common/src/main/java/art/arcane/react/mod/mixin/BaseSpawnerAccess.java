package art.arcane.react.mod.mixin;

import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BaseSpawner.class)
public interface BaseSpawnerAccess {
    @Accessor("spawnDelay")
    int react$spawnDelay();

    @Accessor("spawnDelay")
    void react$setSpawnDelay(int delay);
}
