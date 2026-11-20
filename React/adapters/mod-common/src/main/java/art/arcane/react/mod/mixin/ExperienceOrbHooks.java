package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModDropHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbHooks {
    @Inject(method = "award", at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$captureExperience(ServerLevel level, Vec3 at, int amount, CallbackInfo callback) {
        if (ModDropHost.captureExperience(level, at, amount)) {
            callback.cancel();
        }
    }
}
