package art.arcane.react.mod.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ItemEntity.class)
public interface ItemEntityAccess {
    @Accessor("target")
    UUID react$target();

    @Accessor("age")
    void react$setAge(int age);
}
