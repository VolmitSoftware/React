package art.arcane.react.api.monitor;

/**
 * A player's monitor controls: sneaking focuses a group, scrolling the hotbar while sneaking moves the focus, and a
 * double sneak within 250 ms locks it. Falling fast or starting to sneak in flight briefly ignores sneaking.
 */
public final class MonitorInput {
  private final Object motionLock = new Object();
  private volatile int scrollPosition;
  private volatile boolean sneaking;
  private volatile boolean locked;
  private volatile boolean speedValidForMonitor = true;
  private volatile int speedTickCooldown;
  private double verticalVelocity;
  private long lastShift;

  public boolean isMonitorSneaking() {
    return sneaking && speedValidForMonitor && speedTickCooldown <= 0;
  }

  public boolean isSneaking() {
    return sneaking;
  }

  public boolean isLocked() {
    return locked;
  }

  public int getScrollPosition(int maxRemainder) {
    return Math.floorMod(scrollPosition, maxRemainder);
  }

  public void moved(double verticalDelta) {
    synchronized (motionLock) {
      verticalVelocity += verticalDelta;
    }
  }

  public void sneak(boolean nowSneaking, boolean flying, long nowMillis) {
    sneaking = nowSneaking;
    if (!nowSneaking) {
      return;
    }
    if (flying) {
      speedTickCooldown = 9;
    }
    long previous = lastShift;
    lastShift = nowMillis;
    if (lastShift - previous < 250 && isMonitorSneaking()) {
      locked = !locked;
      lastShift = 0;
    }
  }

  /**
   * Moves the focus by the hotbar change while sneaking. False when the change did not count.
   */
  public boolean scrolled(int previousSlot, int newSlot, boolean sneakingNow) {
    if (!sneakingNow) {
      return false;
    }
    scrollPosition += (newSlot + 19) - (previousSlot + 19);
    return true;
  }

  /**
   * Called on the player's tick: decays the fall speed and the flight cooldown.
   */
  public void tick() {
    synchronized (motionLock) {
      verticalVelocity *= 0.75D;
      if (verticalVelocity < 0.01D && verticalVelocity > -0.01D) {
        verticalVelocity = 0D;
      }
      speedValidForMonitor = verticalVelocity > -1D;
    }
    if (speedTickCooldown > 0) {
      speedTickCooldown--;
    }
  }
}
