package art.arcane.react.platform;

/**
 * Hopper item transfers. Listeners run on the thread that moves the item, before the move happens, in the order
 * they subscribed; a cancelled move skips the remaining listeners.
 */
public interface HopperHost {
  interface Move {
    /**
     * The hopper block taking part in the move, the source when both sides are hoppers; null when neither side is
     * a hopper block, as with hopper minecarts.
     */
    GameLocation hopper();

    boolean sourceIsHopper();

    boolean destinationIsHopper();

    boolean isCancelled();

    void cancel();
  }

  @FunctionalInterface
  interface MoveListener {
    void onMove(Move move);
  }

  Subscription onMove(MoveListener listener);
}
