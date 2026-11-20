package art.arcane.react.platform;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Furnaces, blast furnaces, smokers and brewing stands: their per-tick work, the moments they show activity, and
 * their current cooking state.
 */
public interface FurnaceHost {
  /**
   * A tick hook answer that skips the block entity's tick entirely.
   */
  int SKIP = -1;

  enum Kind {
    FURNACE,
    BREWING_STAND;

    /**
     * The station kind of a block type, or null for any other block.
     */
    public static Kind of(String blockType) {
      if (blockType == null) {
        return null;
      }
      String name = blockType.startsWith("minecraft:") ? blockType.substring(10).toUpperCase(Locale.ROOT) : blockType;
      return switch (name) {
        case "FURNACE", "BLAST_FURNACE", "SMOKER" -> FURNACE;
        case "BREWING_STAND" -> BREWING_STAND;
        default -> null;
      };
    }
  }

  record Station(UUID worldId, int x, int y, int z, Kind kind) {
  }

  /**
   * A furnace's state in Bukkit's units: burn time left, cook progress and the cook total.
   */
  record FurnaceState(String blockType, int burnTime, int cookTime, int cookTimeTotal, boolean hasInput, boolean hasFuelItem) {
  }

  record BrewingState(int fuelLevel, int brewingTime, boolean hasIngredient, boolean hasBottle) {
  }

  /**
   * Decides one block entity tick: {@link #SKIP}, or the number of skipped ticks to catch up before the normal tick
   * runs (0 runs it unchanged). Called on the thread that ticks the block.
   */
  @FunctionalInterface
  interface TickHook {
    int decide(UUID worldId, int x, int y, int z);
  }

  /**
   * Hooks every furnace and brewing stand tick. {@code installed} hears true once either hook is live; it is not
   * called when the platform cannot hook ticks.
   */
  Subscription hookTicks(TickHook furnace, TickHook brewing, Consumer<Boolean> installed);

  /**
   * Stations that show activity: fuel burning, an item smelting, a player opening one, or a hopper moving items in
   * or out.
   */
  Subscription onActivity(Consumer<Station> listener);

  /**
   * Every station in a loaded chunk; empty when the chunk is not loaded. Call on the thread that owns the chunk.
   */
  List<Station> scan(ChunkRef chunk);

  /**
   * Call on the thread that owns the block.
   */
  Optional<FurnaceState> furnace(UUID worldId, int x, int y, int z);

  /**
   * Call on the thread that owns the block.
   */
  Optional<BrewingState> brewing(UUID worldId, int x, int y, int z);
}
