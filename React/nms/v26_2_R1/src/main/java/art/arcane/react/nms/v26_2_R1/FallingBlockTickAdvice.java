package art.arcane.react.nms.v26_2_R1;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.entity.item.FallingBlockEntity;

public final class FallingBlockTickAdvice {
    private FallingBlockTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.This FallingBlockEntity self) {
        return FallingBlockTickRuntime.enter(self);
    }
}
