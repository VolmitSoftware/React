package art.arcane.react.mod.mixin;

import art.arcane.react.mod.ModActivationState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityActivationState implements ModActivationState {
    @Unique
    private long react$activatedTick = Integer.MIN_VALUE;

    @Override
    public long react$activatedTick() {
        return react$activatedTick;
    }

    @Override
    public void react$activatedTick(long tick) {
        react$activatedTick = tick;
    }
}
