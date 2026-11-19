package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.ReactWorld;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public record BukkitReactWorld(World world) implements ReactWorld {
  public BukkitReactWorld {
    Objects.requireNonNull(world, "world");
  }

  @Override
  public UUID id() {
    return world.getUID();
  }

  @Override
  public String name() {
    return world.getName();
  }

  @Override
  public Object handle() {
    return world;
  }
}
