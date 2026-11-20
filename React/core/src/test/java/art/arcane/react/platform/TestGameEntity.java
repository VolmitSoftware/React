package art.arcane.react.platform;

import java.util.UUID;

public final class TestGameEntity implements GameEntity {
  private final UUID id;
  private final String type;
  private final GameLocation location;
  private boolean valid = true;
  private int ticksLived;
  private boolean customName;

  public TestGameEntity(String type, GameLocation location) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.location = location;
  }

  public TestGameEntity invalidate() {
    valid = false;
    return this;
  }

  public TestGameEntity ticksLived(int ticks) {
    ticksLived = ticks;
    return this;
  }

  public TestGameEntity named() {
    customName = true;
    return this;
  }

  @Override
  public UUID id() {
    return id;
  }

  @Override
  public String type() {
    return type;
  }

  @Override
  public GameLocation location() {
    return location;
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  @Override
  public int ticksLived() {
    return ticksLived;
  }

  @Override
  public boolean hasCustomName() {
    return customName;
  }

  @Override
  public boolean isPlayer() {
    return false;
  }

  @Override
  public Object handle() {
    return this;
  }
}
