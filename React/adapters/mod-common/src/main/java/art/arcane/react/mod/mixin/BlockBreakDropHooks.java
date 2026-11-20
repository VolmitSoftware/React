package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModDropHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBreakDropHooks {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), require = 0)
    private void react$beginBreakDrops(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        ModDropHost.beginBreak(player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"), require = 0)
    private void react$endBreakDrops(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        ModDropHost.endBreak(player);
    }
}
