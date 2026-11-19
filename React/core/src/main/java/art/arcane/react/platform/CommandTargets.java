package art.arcane.react.platform;

import java.util.Objects;

/**
 * Command argument types. Director help prints a parameter's simple class name, so these keep the names players
 * already see: Player, World and ReactRenderer.
 */
public final class CommandTargets {
  private CommandTargets() {
  }

  public record Player(ReactPlayer player) {
    public Player {
      Objects.requireNonNull(player, "player");
    }

    public String name() {
      return player.name();
    }
  }

  /**
   * A loaded world and its namespaced key, such as {@code minecraft:overworld}.
   */
  public record World(ReactWorld world, String key) {
    public World {
      Objects.requireNonNull(world, "world");
      Objects.requireNonNull(key, "key");
    }

    public String name() {
      return world.name();
    }

    public String keyPath() {
      int separator = key.indexOf(':');
      return separator < 0 ? key : key.substring(separator + 1);
    }
  }

  public record ReactRenderer(String id) {
    public ReactRenderer {
      Objects.requireNonNull(id, "id");
    }

    public String getId() {
      return id;
    }
  }
}
