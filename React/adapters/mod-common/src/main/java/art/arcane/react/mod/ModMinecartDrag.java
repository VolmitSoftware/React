package art.arcane.react.mod;

/**
 * CraftBukkit's flying and derailed minecart velocity modifiers, which React only ever zeroes: a stopped cart loses
 * all of its motion each tick it spends off rails, on the ground or in the air, until it unloads.
 */
public interface ModMinecartDrag {
    boolean react$offRailStopped();

    void react$setOffRailStopped(boolean stopped);
}
