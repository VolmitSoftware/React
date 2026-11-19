package art.arcane.react.mod.mixin;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Only a piston that moves asks its resolver for the blocks to push, so each move counts once with its size. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonResolverHooks {
    @Inject(method = "getToPush", at = @At("RETURN"), require = 0)
    private void react$moving(CallbackInfoReturnable<List<BlockPos>> callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        if (counters.isActive(Counter.PISTON_MOVE)) {
            counters.count(Counter.PISTON_MOVE, this, callback.getReturnValue().size() + 2);
        }
    }
}
