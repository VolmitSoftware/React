package art.arcane.react.api.test.load;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SyntheticPlayerLoad {
  private final List<SyntheticPlayerRecord> records;
  private boolean active;
  private long radius;

  public SyntheticPlayerLoad() {
    this.records = new ArrayList<SyntheticPlayerRecord>();
    this.active = false;
    this.radius = 256L;
  }

  public void begin(int count, World world, long radius) {
    if (active || world == null || count <= 0) {
      return;
    }
    this.radius = Math.max(64L, radius);
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    if (index == null) {
      return;
    }
    Location center = world.getSpawnLocation();
    for (int i = 0; i < count; i++) {
      double x = center.getX() + ThreadLocalRandom.current().nextDouble(-this.radius, this.radius);
      double z = center.getZ() + ThreadLocalRandom.current().nextDouble(-this.radius, this.radius);
      double y = center.getY();
      Location location = new Location(world, x, y, z);
      SyntheticPlayerRecord record = new SyntheticPlayerRecord(UUID.randomUUID(), location);
      records.add(record);
      index.injectSynthetic(record.id(), location);
    }
    active = true;
  }

  public void tickMovement() {
    if (!active) {
      return;
    }
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    if (index == null) {
      return;
    }
    for (SyntheticPlayerRecord record : records) {
      Location location = record.location();
      location.setX(location.getX() + ThreadLocalRandom.current().nextDouble(-4.0, 4.0));
      location.setZ(location.getZ() + ThreadLocalRandom.current().nextDouble(-4.0, 4.0));
      index.injectSynthetic(record.id(), location);
    }
  }

  public void end() {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    for (SyntheticPlayerRecord record : records) {
      if (index != null) {
        index.clearSynthetic(record.id());
      }
    }
    records.clear();
    active = false;
  }

  public int active() {
    return active ? records.size() : 0;
  }
}
