package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bukkit's PlayerInteractEntityEvent, before the interaction runs. */
@Mixin(Player.class)
public abstract class PlayerHooks {
    @Inject(method = "interactOn", at = @At("HEAD"), require = 0)
    private void react$interacting(Entity entity, InteractionHand hand, Vec3 location,
                                   CallbackInfoReturnable<InteractionResult> callback) {
        ModMobStackHost.interacted((Player) (Object) this, entity, hand);
        ModEntityTicker.poke(entity);
    }
}
