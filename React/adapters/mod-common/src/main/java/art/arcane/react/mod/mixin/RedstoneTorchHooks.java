package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModRedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redstone torches switching, where CraftBukkit fires its block redstone event; a held change skips the switch, as on Bukkit. */
@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchHooks {
    private static final String SERVER_SET = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z";
    private static final String SERVER_SET_AND_UPDATE = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";

    @Inject(method = "tick", at = {@At(value = "INVOKE", target = SERVER_SET_AND_UPDATE), @At(value = "INVOKE", target = SERVER_SET)},
            cancellable = true, require = 0)
    private void react$switching(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (ModRedstoneHost.held(level, pos, state, !state.getValue(RedstoneTorchBlock.LIT))) {
            callback.cancel();
        }
    }
}
