package art.arcane.react.mod.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingAccess {
    @Accessor("brewTime")
    int react$brewTime();

    @Accessor("brewTime")
    void react$setBrewTime(int value);

    @Accessor("fuel")
    int react$fuel();

    @Accessor("items")
    NonNullList<ItemStack> react$items();
}
