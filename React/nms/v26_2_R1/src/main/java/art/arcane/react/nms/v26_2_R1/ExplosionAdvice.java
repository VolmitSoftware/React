package art.arcane.react.nms.v26_2_R1;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.level.ServerExplosion;

public final class ExplosionAdvice {
    private ExplosionAdvice() {}

    @Advice.OnMethodEnter
    public static void enter(@Advice.This ServerExplosion self) {
        ExplosionRuntime.enter(self);
    }
}
