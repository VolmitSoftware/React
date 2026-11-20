package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sculk sensors switching on and off, where CraftBukkit fires its block redstone event: activation uses the power the
 * listeners settle on and is skipped at zero, and a deactivation held above zero keeps the sensor powered.
 */
@Mixin(SculkSensorBlock.class)
public abstract class RedstoneSculkHooks {
    @WrapMethod(method = "activate", require = 0)
    private void react$activating(Entity source, Level level, BlockPos pos, BlockState state, int power, int frequency,
                                  Operation<Void> original) {
        int settled = ModRedstoneHost.signal(level, pos, state, state.getValue(SculkSensorBlock.POWER), power);
        if (settled != 0) {
            original.call(source, level, pos, state, settled, frequency);
        }
    }

    @Inject(method = "deactivate", at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$deactivating(Level level, BlockPos pos, BlockState state, CallbackInfo callback) {
        int settled = ModRedstoneHost.signal(level, pos, state, state.getValue(SculkSensorBlock.POWER), 0);
        if (settled > 0) {
            level.setBlock(pos, state.setValue(SculkSensorBlock.POWER, settled), 3);
            callback.cancel();
        }
    }
}
