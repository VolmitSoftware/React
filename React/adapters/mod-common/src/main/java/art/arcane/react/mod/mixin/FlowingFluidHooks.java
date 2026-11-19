package art.arcane.react.mod.mixin;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidHooks {
    @Inject(method = "spreadTo", at = @At("HEAD"), require = 0)
    private void react$spreading(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target,
                                 CallbackInfo callback) {
        SamplerEventCounters counters = SamplerEventCounters.get();
        counters.count(Counter.FLUID_FLOW, pos);
        counters.count(Counter.FLUID_EVENT, pos);
    }
}
