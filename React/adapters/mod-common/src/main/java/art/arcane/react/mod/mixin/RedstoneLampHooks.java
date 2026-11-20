package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redstone lamps switching, where CraftBukkit fires its block redstone event; a held change skips the switch, as on Bukkit. */
@Mixin(RedstoneLampBlock.class)
public abstract class RedstoneLampHooks {
    private static final String LEVEL_SET = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";
    private static final String SERVER_SET = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";

    @Inject(method = "tick", at = @At(value = "INVOKE", target = SERVER_SET), cancellable = true, require = 0)
    private void react$switchingOff(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(RedstoneLampBlock.LIT))) {
            callback.cancel();
        }
    }

    @Inject(method = "neighborChanged", at = @At(value = "INVOKE", target = LEVEL_SET), cancellable = true, require = 0)
    private void react$switchingOn(CallbackInfo callback, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) Level level,
                                   @Local(argsOnly = true, ordinal = 0) BlockPos pos) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(RedstoneLampBlock.LIT))) {
            callback.cancel();
        }
    }
}
