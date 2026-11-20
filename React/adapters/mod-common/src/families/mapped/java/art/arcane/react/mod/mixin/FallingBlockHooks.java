package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModBlockHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Falling blocks at the two points where Bukkit fires its entity change block event, plus the tick the lazy gravity
 * hook may skip. A cancelled start leaves the block in place and never adds the entity; a cancelled landing removes
 * the entity without placing or dropping the block.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockHooks {
    @Shadow
    public boolean dropItem;

    @Shadow
    public abstract BlockState getBlockState();

    @Inject(method = "fall",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private static void react$starting(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> callback,
                                       FallingBlockEntity entity) {
        if (!ModBlockHost.allowFalling(entity, level, pos)) {
            callback.setReturnValue(entity);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$ticking(CallbackInfo callback) {
        if (ModBlockHost.skipFallingTick((FallingBlockEntity) (Object) this)) {
            callback.cancel();
        }
    }

    @Redirect(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            require = 0)
    private boolean react$landing(Level level, BlockPos pos, BlockState state, int flags) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!ModBlockHost.allowFalling(self, level, pos)) {
            dropItem = false;
            self.discard();
            self.callOnBrokenAfterFall(getBlockState().getBlock(), pos);
            return false;
        }
        return level.setBlock(pos, state, flags);
    }
}
