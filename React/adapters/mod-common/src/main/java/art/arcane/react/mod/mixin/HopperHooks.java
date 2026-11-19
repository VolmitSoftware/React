package art.arcane.react.mod.mixin;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(HopperBlockEntity.class)
public abstract class HopperHooks {
    @Inject(method = "tryMoveItems", at = @At("RETURN"), require = 0)
    private static void react$moved(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, BooleanSupplier action,
                                    CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            SamplerEventCounters.get().count(Counter.HOPPER_MOVE, pos);
        }
    }
}
