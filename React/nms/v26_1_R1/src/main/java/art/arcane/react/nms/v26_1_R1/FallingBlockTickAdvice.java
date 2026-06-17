package art.arcane.react.nms.v26_1_R1;

import art.arcane.react.nms.FallingBlockTickHook;
import art.arcane.react.nms.TickDecision;
import net.bytebuddy.asm.Advice;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.bukkit.craftbukkit.entity.CraftFallingBlock;
import org.bukkit.entity.FallingBlock;

public final class FallingBlockTickAdvice {
    public static volatile FallingBlockTickHook HOOK;

    private FallingBlockTickAdvice() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean enter(@Advice.This FallingBlockEntity self) {
        FallingBlockTickHook hook = HOOK;
        if (hook == null) {
            return false;
        }
        try {
            FallingBlock bukkit = (FallingBlock) self.getBukkitEntity();
            if (bukkit == null) {
                return false;
            }
            TickDecision decision = hook.decide(bukkit);
            return decision == TickDecision.SKIP;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
