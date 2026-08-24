package art.arcane.react.nms.v26_2_R1;

import net.bytebuddy.asm.Advice;

public final class ExplosionPacketScopeAdvice {
    private ExplosionPacketScopeAdvice() {}

    @Advice.OnMethodEnter
    public static void enter() {
        ExplosionPacketSubstitution.enterExplosion();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit() {
        ExplosionPacketSubstitution.exitExplosion();
    }
}
