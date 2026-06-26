package art.arcane.react.api.test.load;

import org.bukkit.Location;

import java.util.UUID;

public final class SyntheticPlayerRecord {
  private final UUID id;
  private final Location location;

  public SyntheticPlayerRecord(UUID id, Location location) {
    this.id = id;
    this.location = location;
  }

  public UUID id() {
    return id;
  }

  public Location location() {
    return location;
  }
}
