package art.arcane.react.platform;

public interface GameRuleHost {
  /**
   * The world's random tick speed game rule; null when it cannot be read. Server thread.
   */
  Integer randomTickSpeed(ReactWorld world);

  /**
   * Sets the world's random tick speed game rule; false when the value was refused. Server thread.
   */
  boolean setRandomTickSpeed(ReactWorld world, int value);
}
