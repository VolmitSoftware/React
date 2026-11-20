package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.FarmlandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityInteractEvent for an entity landing on farmland, as an entity tick trigger. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandHooks {
    @Inject(method = "fallOn", at = @At("HEAD"), require = 0)
    private void react$landed(CallbackInfo callback, @Local(argsOnly = true) Entity entity) {
        ModEntityTicker.interacted(entity);
    }
}
