package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import art.arcane.react.mod.ModMobStackHost;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalHooks {
    @Inject(method = "tame", at = @At("TAIL"), require = 0)
    private void react$tamed(Player player, CallbackInfo callback) {
        ModMobStackHost.tamed((TamableAnimal) (Object) this, player);
        ModEntityTicker.poke((TamableAnimal) (Object) this);
    }
}
