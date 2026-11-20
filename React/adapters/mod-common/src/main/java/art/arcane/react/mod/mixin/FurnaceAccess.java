package art.arcane.react.mod.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
    @Accessor("litTimeRemaining")
    int react$litTime();

    @Accessor("litTimeRemaining")
    void react$setLitTime(int value);

    @Accessor("cookingTimer")
    int react$cookingTimer();

    @Accessor("cookingTimer")
    void react$setCookingTimer(int value);

    @Accessor("cookingTotalTime")
    int react$cookingTotalTime();

    @Accessor("items")
    NonNullList<ItemStack> react$items();
}
