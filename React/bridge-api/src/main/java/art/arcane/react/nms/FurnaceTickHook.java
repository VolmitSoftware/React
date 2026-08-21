package art.arcane.react.nms;

import org.bukkit.World;

@FunctionalInterface
public interface FurnaceTickHook {
    FurnaceTickResult decide(World world, int x, int y, int z);
}
