package art.arcane.react.mod.mixin;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireHooks {
    @Inject(method = "updatePowerStrength", at = @At("HEAD"), require = 0)
    private void react$powerChanging(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean shapeUpdate,
                                     CallbackInfo callback) {
        SamplerEventCounters.get().count(Counter.REDSTONE, pos);
    }
}
