package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModWorldTimings;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Times full saves, the autosave and {@code /save-all}, from start to finish. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaveHooks {
    @Inject(method = "saveEverything", at = @At("HEAD"), require = 0)
    private void react$saving(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> callback) {
        ModWorldTimings.saveStarted(force);
    }

    @Inject(method = "saveEverything", at = @At("RETURN"), require = 0)
    private void react$saved(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> callback) {
        ModWorldTimings.saveFinished();
    }
}
