package art.arcane.react.mod.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
    @Accessor("litTime")
    int react$litTime();

    @Accessor("litTime")
    void react$setLitTime(int value);

    @Accessor("cookingProgress")
    int react$cookingTimer();

    @Accessor("cookingProgress")
    void react$setCookingTimer(int value);

    @Accessor("cookingTotalTime")
    int react$cookingTotalTime();

    @Accessor("items")
    NonNullList<ItemStack> react$items();
}
