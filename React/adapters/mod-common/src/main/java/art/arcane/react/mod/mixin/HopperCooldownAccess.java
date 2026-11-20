package art.arcane.react.mod.mixin;

import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HopperBlockEntity.class)
public interface HopperCooldownAccess {
    @Accessor("cooldownTime")
    int reactCooldown();

    @Accessor("cooldownTime")
    void reactSetCooldown(int ticks);
}
