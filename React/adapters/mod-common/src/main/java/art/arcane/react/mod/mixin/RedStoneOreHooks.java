package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RedStoneOreBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityInteractEvent for an entity stepping on redstone ore, as an entity tick trigger. */
@Mixin(RedStoneOreBlock.class)
public abstract class RedStoneOreHooks {
    @Inject(method = "stepOn", at = @At("HEAD"), require = 0)
    private void react$stepped(CallbackInfo callback, @Local(argsOnly = true) Entity entity) {
        ModEntityTicker.interacted(entity);
    }
}
