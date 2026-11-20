package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModHopperHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hopper block entities joining or leaving a loaded chunk, by any cause: placement, pistons, explosions. */
@Mixin(LevelChunk.class)
public abstract class HopperIndexChunkHooks {
    @Inject(method = "setBlockEntity", at = @At("TAIL"), require = 0)
    private void react$blockEntitySet(BlockEntity blockEntity, CallbackInfo callback) {
        ModHopperHost.blockEntitySet((LevelChunk) (Object) this, blockEntity);
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), require = 0)
    private void react$blockEntityRemoving(BlockPos pos, CallbackInfo callback) {
        ModHopperHost.blockEntityRemoving((LevelChunk) (Object) this, pos);
    }
}
