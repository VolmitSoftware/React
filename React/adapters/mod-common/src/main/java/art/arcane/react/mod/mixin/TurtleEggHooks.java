package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityInteractEvent for an entity stepping or landing on turtle eggs, as an entity tick trigger. */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggHooks {
    @Inject(method = {"stepOn", "fallOn"}, at = @At("HEAD"), require = 0)
    private void react$trampled(CallbackInfo callback, @Local(argsOnly = true) Entity entity) {
        ModEntityTicker.interacted(entity);
    }
}
