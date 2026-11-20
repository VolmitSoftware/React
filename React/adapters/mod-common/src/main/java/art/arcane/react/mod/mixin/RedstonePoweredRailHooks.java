package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Powered and activator rails switching, where CraftBukkit fires its block redstone event; a held change skips the update, as on Bukkit. */
@Mixin(PoweredRailBlock.class)
public abstract class RedstonePoweredRailHooks {
    @Inject(method = "updateState", at = {@At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"), @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")},
            cancellable = true, require = 0)
    private void react$switching(BlockState state, Level level, BlockPos pos, Block block, CallbackInfo callback) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(PoweredRailBlock.POWERED))) {
            callback.cancel();
        }
    }
}
