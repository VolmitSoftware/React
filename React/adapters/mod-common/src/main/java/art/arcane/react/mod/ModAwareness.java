package art.arcane.react.mod;

/**
 * Bukkit's {@code Mob#setAware} on mods: an unaware mob skips its goals, brain, pathfinding and movement controls but
 * keeps physics, and still floats in water and lava. Mixed into {@code Mob}; the flag is not saved, as on Paper.
 */
public interface ModAwareness {
    boolean react$isAware();

    void react$setAware(boolean aware);
}
