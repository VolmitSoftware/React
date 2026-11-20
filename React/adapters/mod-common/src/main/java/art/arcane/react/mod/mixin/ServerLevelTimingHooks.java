package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModLevelTiming;
import art.arcane.react.mod.ModWorldTimings;
import art.arcane.react.platform.timing.WorldTickTimes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Times each level's tick and save on the server thread. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTimingHooks implements ModLevelTiming {
    @Unique
    private long react$tickStarted;
    @Unique
    private long react$tickNanos;
    @Unique
    private long react$tickMaxNanos;
    @Unique
    private int react$ticks;
    @Unique
    private long react$saveStarted;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"), require = 0)
    private void react$tickStarting(BooleanSupplier haveTime, CallbackInfo callback) {
        react$tickStarted = System.nanoTime();
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"), require = 0)
    private void react$tickFinished(BooleanSupplier haveTime, CallbackInfo callback) {
        long elapsed = System.nanoTime() - react$tickStarted;
        react$tickNanos += elapsed;
        react$ticks++;
        if (elapsed > react$tickMaxNanos) {
            react$tickMaxNanos = elapsed;
        }
    }

    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void react$saveStarting(ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo callback) {
        react$saveStarted = System.nanoTime();
    }

    @Inject(method = "save", at = @At("TAIL"), require = 0)
    private void react$saveFinished(ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo callback) {
        if (!noSave) {
            ModWorldTimings.levelSaved((ServerLevel) (Object) this, System.nanoTime() - react$saveStarted);
        }
    }

    @Override
    public WorldTickTimes.World react$drainTicks(UUID id, String key) {
        WorldTickTimes.World measured = WorldTickTimes.World.measured(id, key, react$ticks, react$tickNanos, react$tickMaxNanos);
        react$ticks = 0;
        react$tickNanos = 0L;
        react$tickMaxNanos = 0L;
        return measured;
    }
}
