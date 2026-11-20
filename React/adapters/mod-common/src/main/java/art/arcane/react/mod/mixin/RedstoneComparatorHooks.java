package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Comparators switching, where CraftBukkit fires its block redstone event; a held change skips the switch, as on Bukkit. */
@Mixin(ComparatorBlock.class)
public abstract class RedstoneComparatorHooks {
    private static final String LEVEL_SET = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";

    @Inject(method = "refreshOutputState", at = @At(value = "INVOKE", target = LEVEL_SET), cancellable = true, require = 0)
    private void react$switching(Level level, BlockPos pos, BlockState state, CallbackInfo callback) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(ComparatorBlock.POWERED))) {
            callback.cancel();
        }
    }
}
