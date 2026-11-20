package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Buttons pressed by players, explosions and arrows, and released, where CraftBukkit fires its block redstone event. */
@Mixin(ButtonBlock.class)
public abstract class RedstoneButtonHooks {
    @WrapWithCondition(method = {"useWithoutItem", "onExplosionHit"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/ButtonBlock;press(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private boolean react$pressing(ButtonBlock button, BlockState state, Level level, BlockPos pos, Player player) {
        return !ModRedstoneHost.held(level, pos, state, true);
    }

    @Inject(method = "checkPressed", at = {@At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")},
            cancellable = true, require = 0)
    private void react$switching(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(ButtonBlock.POWERED))) {
            callback.cancel();
        }
    }
}
