package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModFurnaceHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Furnace ticks for furnace/brew batching: a tick can be skipped, and fuel burns and smelts count as activity. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceTickHooks {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$ticking(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity,
                                      CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel && ModFurnaceHost.beforeFurnaceTick(serverLevel, pos, entity)) {
            callback.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private static void react$ticked(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity,
                                     CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel) {
            ModFurnaceHost.afterFurnaceTick(serverLevel, pos, entity);
        }
    }
}
