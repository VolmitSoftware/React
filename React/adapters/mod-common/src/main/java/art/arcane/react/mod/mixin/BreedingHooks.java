package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModSpawnHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class BreedingHooks {
    @Inject(method = "spawnChildFromBreeding", at = @At("HEAD"), require = 0)
    private void react$breeding(ServerLevel level, Animal partner, CallbackInfo callback) {
        ModSpawnHost.enter("BREEDING");
    }

    @Inject(method = "spawnChildFromBreeding", at = @At("RETURN"), require = 0)
    private void react$bred(ServerLevel level, Animal partner, CallbackInfo callback) {
        ModSpawnHost.exit();
    }
}
