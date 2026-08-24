package art.arcane.react.nms;

import org.bukkit.World;

@FunctionalInterface
public interface ExplosionPacketSuppressor {
    boolean shouldSuppress(World world, double x, double y, double z, float radius, int packetCount);
}
