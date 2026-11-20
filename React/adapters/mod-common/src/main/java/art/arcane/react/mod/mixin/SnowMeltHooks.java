package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModGrowthHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A snow layer about to melt in block light, where Bukkit fires its block fade event. */
@Mixin(SnowLayerBlock.class)
public abstract class SnowMeltHooks {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$melting(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 && !ModGrowthHost.allowFade(level, pos)) {
            callback.cancel();
        }
    }
}
