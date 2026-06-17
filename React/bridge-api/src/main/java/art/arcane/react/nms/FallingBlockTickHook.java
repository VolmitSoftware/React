package art.arcane.react.nms;

import org.bukkit.entity.FallingBlock;

@FunctionalInterface
public interface FallingBlockTickHook {
    TickDecision decide(FallingBlock entity);
}
