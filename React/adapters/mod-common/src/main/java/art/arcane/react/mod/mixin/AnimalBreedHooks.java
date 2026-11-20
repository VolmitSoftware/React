package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityBreedEvent: both parents are refreshed. */
@Mixin(Animal.class)
public abstract class AnimalBreedHooks {
    @Inject(method = "spawnChildFromBreeding", at = @At("HEAD"), require = 0)
    private void react$breeding(ServerLevel level, Animal partner, CallbackInfo callback) {
        ModEntityTicker.poke((Animal) (Object) this);
        ModEntityTicker.poke(partner);
    }
}
