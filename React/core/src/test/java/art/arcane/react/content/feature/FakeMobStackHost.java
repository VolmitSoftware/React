package art.arcane.react.content.feature;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FakeMobStackHost implements MobStackHost {
  static final UUID WORLD = UUID.randomUUID();

  final Map<GameEntity, Mob> mobs = new HashMap<>();
  final List<GameEntity> spawned = new ArrayList<>();
  final List<GameEntity> discarded = new ArrayList<>();
  final List<GameEntity[]> collected = new ArrayList<>();
  boolean rejectSpawns;
  boolean failCopies;

  static final class Mob {
    final String type;
    final UUID id = UUID.randomUUID();
    double x;
    double health = 20D;
    double maxHealth = 20D;
    boolean dead;
    boolean tamed;
    UUID owner;
    String name;
    String label;
    final Set<String> flags = new HashSet<>();

    Mob(String type, double x) {
      this.type = type;
      this.x = x;
    }
  }

  record Entity(Mob mob) implements GameEntity {
    @Override
    public UUID id() {
      return mob.id;
    }

    @Override
    public String type() {
      return mob.type;
    }

    @Override
    public GameLocation location() {
      return new GameLocation(WORLD, mob.x, 64D, 0D);
    }

    @Override
    public boolean isValid() {
      return !mob.dead;
    }

    @Override
    public int ticksLived() {
      return 100;
    }

    @Override
    public boolean hasCustomName() {
      return mob.name != null;
    }

    @Override
    public boolean isPlayer() {
      return false;
    }

    @Override
    public Object handle() {
      return mob;
    }
  }

  GameEntity add(String type, double x) {
    Mob mob = new Mob(type, x);
    GameEntity entity = new Entity(mob);
    mobs.put(entity, mob);
    return entity;
  }

  Mob mob(GameEntity entity) {
    return ((Entity) entity).mob();
  }

  @Override
  public Subscription listen(Events events) {
    return Subscription.NONE;
  }

  @Override
  public Set<String> defaultStackableTypes() {
    return Set.of("ZOMBIE", "CREEPER", "WOLF");
  }


  @Override
  public boolean isLiving(GameEntity entity) {
    return true;
  }

  @Override
  public boolean isDead(GameEntity entity) {
    return mob(entity).dead;
  }

  @Override
  public boolean isAlive(GameEntity entity) {
    return !mob(entity).dead;
  }

  @Override
  public boolean isSameEntity(GameEntity first, GameEntity second) {
    return mob(first) == mob(second);
  }

  @Override
  public boolean isTamedPet(GameEntity entity) {
    return mob(entity).tamed;
  }

  @Override
  public UUID ownerId(GameEntity entity) {
    return mob(entity).owner;
  }

  @Override
  public Object mergeBucket(GameEntity entity) {
    return entity.type();
  }

  @Override
  public boolean hasSafeMergeState(GameEntity source, GameEntity target) {
    return true;
  }

  @Override
  public boolean hasSafeHealthState(GameEntity source, GameEntity target) {
    return mob(source).health == mob(source).maxHealth && mob(target).health == mob(target).maxHealth;
  }

  @Override
  public double maxHealth(GameEntity entity) {
    return mob(entity).maxHealth;
  }

  @Override
  public double health(GameEntity entity) {
    return mob(entity).health;
  }

  @Override
  public void setHealth(GameEntity entity, double health) {
    mob(entity).health = health;
  }

  @Override
  public void restoreHealth(GameEntity entity, double health) {
    mob(entity).health = Math.min(mob(entity).maxHealth, health);
  }

  @Override
  public GameEntity spawnCopy(GameEntity source, double raise, boolean copyName) {
    if (rejectSpawns) {
      return null;
    }
    if (failCopies) {
      throw new IllegalStateException("copy failed");
    }
    Mob original = mob(source);
    GameEntity copy = add(original.type, original.x);
    Mob created = mob(copy);
    created.tamed = original.tamed;
    created.owner = original.owner;
    created.flags.addAll(original.flags);
    if (copyName) {
      created.name = original.name;
    }
    spawned.add(copy);
    return copy;
  }

  @Override
  public void untame(GameEntity entity) {
    mob(entity).tamed = false;
    mob(entity).owner = null;
  }

  @Override
  public void discard(GameEntity entity) {
    mob(entity).dead = true;
    discarded.add(entity);
  }

  @Override
  public void sendCollect(GameEntity collected, GameEntity collector) {
    this.collected.add(new GameEntity[]{collected, collector});
  }

  @Override
  public boolean hasFlag(GameEntity entity, String flag) {
    return mob(entity).flags.contains(flag);
  }

  @Override
  public void setFlag(GameEntity entity, String flag, boolean value) {
    if (value) {
      mob(entity).flags.add(flag);
    } else {
      mob(entity).flags.remove(flag);
    }
  }

  @Override
  public String customName(GameEntity entity) {
    return mob(entity).name;
  }

  @Override
  public void setCustomName(GameEntity entity, String name) {
    mob(entity).name = name;
  }

  @Override
  public String stackLabel(GameEntity entity) {
    return mob(entity).label;
  }

  @Override
  public void setStackLabel(GameEntity entity, String label) {
    mob(entity).label = label;
  }

  @Override
  public void clearStackLabel(GameEntity entity) {
    mob(entity).label = null;
  }

  @Override
  public boolean showOverlay(GameEntity entity, int count) {
    return false;
  }

  @Override
  public boolean isChunkLoaded(ReactWorld world, int chunkX, int chunkZ) {
    return true;
  }

  @Override
  public List<GameEntity> loadedChunkEntities(ReactWorld world, int chunkX, int chunkZ) {
    return new ArrayList<>(mobs.keySet());
  }

  @Override
  public ChunkCursor openLoadedChunks() {
    return maximum -> List.of();
  }
}
