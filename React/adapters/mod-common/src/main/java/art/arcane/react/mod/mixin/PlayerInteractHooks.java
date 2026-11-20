package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModPlayerHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block and item use where Bukkit fires PlayerInteractEvent: right clicks on blocks and in the air, and the start of
 * breaking a block. A cancelled interaction fails and the client gets the real blocks and inventory back.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerInteractHooks {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$useOn(ServerPlayer user, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                             CallbackInfoReturnable<InteractionResult> callback) {
        if (ModPlayerHost.cancelsInteraction(user)) {
            ModPlayerHost.resync(user, hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()));
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$use(ServerPlayer user, Level level, ItemStack stack, InteractionHand hand,
                           CallbackInfoReturnable<InteractionResult> callback) {
        if (ModPlayerHost.cancelsInteraction(user)) {
            ModPlayerHost.resync(user);
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true, require = 0)
    private void react$startBreaking(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY,
                                     int sequence, CallbackInfo callback) {
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && ModPlayerHost.cancelsInteraction(player)) {
            ModPlayerHost.resync(player, pos);
            callback.cancel();
        }
    }
}
