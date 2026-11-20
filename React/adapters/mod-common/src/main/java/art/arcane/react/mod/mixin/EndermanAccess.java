package art.arcane.react.mod.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The enderman's screaming and stared-at state, which Paper exposes as provocation; the class was renamed in 26.3. */
@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.monster.Enderman", "net.minecraft.world.entity.monster.EnderMan"})
public interface EndermanAccess {
    @Accessor("DATA_CREEPY")
    static EntityDataAccessor<Boolean> react$creepyData() {
        throw new AssertionError();
    }

    @Invoker("isCreepy")
    boolean react$isCreepy();

    @Invoker("hasBeenStaredAt")
    boolean react$hasBeenStaredAt();

    @Invoker("setBeingStaredAt")
    void react$setBeingStaredAt();
}
