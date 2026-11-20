package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModFurnaceHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brewing stand ticks for furnace/brew batching; a tick can be skipped. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingTickHooks {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void react$ticking(ServerLevel level, BlockPos pos, BlockState state, BrewingStandBlockEntity entity,
                                      CallbackInfo callback) {
        if (ModFurnaceHost.beforeBrewingTick(level, pos, entity)) {
            callback.cancel();
        }
    }
}
