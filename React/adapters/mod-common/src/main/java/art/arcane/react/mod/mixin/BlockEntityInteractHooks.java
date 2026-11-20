package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModEntityTicker;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.TripWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit's EntityInteractEvent for entities inside pressure plates, tripwire, buttons and big dripleaf, as an entity tick trigger. */
@Mixin({BasePressurePlateBlock.class, TripWireBlock.class, ButtonBlock.class, BigDripleafBlock.class})
public abstract class BlockEntityInteractHooks {
    @Inject(method = "entityInside", at = @At("HEAD"), require = 0)
    private void react$inside(CallbackInfo callback, @Local(argsOnly = true) Entity entity) {
        ModEntityTicker.interacted(entity);
    }
}
