package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Horse taming; the class moved packages after 1.21.1, so both names are targeted. */
@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.animal.equine.AbstractHorse", "net.minecraft.world.entity.animal.horse.AbstractHorse"})
public abstract class AbstractHorseHooks {
    @Inject(method = "tameWithName(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("TAIL"), require = 0)
    private void react$tamed(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            ModMobStackHost.tamed((Entity) (Object) this, player);
            ModEntityTicker.poke((Entity) (Object) this);
        }
    }
}
