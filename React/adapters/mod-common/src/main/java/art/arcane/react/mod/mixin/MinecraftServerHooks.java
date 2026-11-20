package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ReactModRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** An empty server pauses and skips the loaders' tick events; React's own queued work keeps running. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerHooks {
    @Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"), require = 0)
    private void react$tickWhilePaused(BooleanSupplier haveTime, CallbackInfo callback) {
        ReactModRuntime.tickWhilePaused();
    }
}
