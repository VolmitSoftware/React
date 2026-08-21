package art.arcane.react.nms;

import org.bukkit.World;

public interface NmsBridge {
    String version();

    boolean installFurnaceTickHook(FurnaceTickHook hook);

    boolean installBrewingTickHook(BrewingTickHook hook);

    boolean installFallingBlockTickHook(FallingBlockTickHook hook);

    boolean installExplosionHook(ExplosionHook hook);

    boolean installExplosionPacketSuppressor(ExplosionPacketSuppressor suppressor);

    boolean installHopperTickHook(HopperTickHook hook);

    void uninstallFurnaceTickHook();

    void uninstallBrewingTickHook();

    void uninstallFallingBlockTickHook();

    void uninstallExplosionHook();

    void uninstallExplosionPacketSuppressor();

    void uninstallHopperTickHook();

    boolean supportsFurnaceTickHook();

    boolean supportsBrewingTickHook();

    boolean supportsFallingBlockTickHook();

    boolean supportsExplosionHook();

    boolean supportsExplosionPacketSuppressor();

    boolean supportsHopperTickHook();

    int broadcastMergedExplosion(World world, double x, double y, double z, float radius, double rangeBlocks);
}
