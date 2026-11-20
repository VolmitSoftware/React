package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player breaking a block, where Bukkit fires its block break event; a cancelled break resends the block. */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerBreakHooks {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$breaking(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (!ModBlockHost.allowBreak(level, pos, player)) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
            callback.setReturnValue(false);
        }
    }
}
