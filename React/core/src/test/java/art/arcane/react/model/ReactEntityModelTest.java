package art.arcane.react.model;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

class ReactEntityModelTest {
  private static final double TOLERANCE = 1e-9;
  private final FakeHost host = new FakeHost();

  @BeforeEach
  void bind() {
    ReactPlatforms.bind(new FakePlatform(host));
    ReactEntity.clearScratch();
  }

  @AfterEach
  void unbind() {
    ReactEntity.releaseAllManagedState();
    ReactEntity.awaitManagedCleanup(1000L);
    ReactEntity.clearScratch();
    ReactPlatforms.unbind();
  }

  @Test
  void typePrioritiesUseBukkitNamesAndBaselineForModdedTypes() {
    EntityPriority priority = new EntityPriority();

    Assertions.assertEquals(100D * 10D, priority.getPriority("ITEM"), TOLERANCE);
    Assertions.assertEquals(100D * 1.25D, priority.getPriority("ZOMBIE"), TOLERANCE);
    Assertions.assertEquals(100D * (1.15D * 2.5D), priority.getPriority("WOLF"), TOLERANCE);
    Assertions.assertEquals(-1D, priority.getPriority("PLAYER"), TOLERANCE);
    Assertions.assertEquals(EntityPriority.BASELINE, priority.getPriority("create:contraption"), TOLERANCE);
  }

  @Test
  void livingPriorityAppliesAgeMotionHealthTameAndAgeFactors() {
    EntityPriority priority = new EntityPriority();
    FakeEntity wolf = new FakeEntity("WOLF");
    wolf.living = true;
    wolf.mob = true;
    wolf.ticksLived = 600;
    wolf.speed = 0.5D;
    wolf.health = 4D;
    wolf.maxHealth = 8D;
    wolf.tameable = true;
    wolf.tamed = true;
    wolf.breedable = true;
    wolf.adult = false;

    double expected = 100D * (1.15D * 2.5D);
    expected *= M.lerp(1.15D, 0.6D, 600D / 1200D);
    expected *= M.lerp(1D, 1.1D, 0.5D);
    expected *= 5D;
    expected *= M.lerp(1.35D, 0.75D, 0.5D);
    expected *= 2.5D * 15D;
    expected *= 1.4D;

    Assertions.assertEquals(expected, priority.getPriority(wolf), TOLERANCE);
  }

  @Test
  void itemValueRaisesDroppedItemPriority() {
    EntityPriority priority = new EntityPriority();
    FakeEntity item = new FakeEntity("ITEM");
    item.ticksLived = 2000;
    host.itemValues.put(item.id, 40D);

    Assertions.assertEquals(100D * 10D * 0.6D + 40D * 1.5D, priority.getPriority(item), TOLERANCE);
  }

  @Test
  void playersAreNeverRemovalCandidates() {
    FakeEntity player = new FakeEntity("PLAYER");
    player.player = true;

    Assertions.assertEquals(-1D, new EntityPriority().getPriority(player), TOLERANCE);
  }

  @Test
  void crowdingCountsNeighboursOfSimilarPriority() {
    EntityPriority priority = new EntityPriority();
    FakeEntity source = new FakeEntity("ZOMBIE");
    FakeEntity similar = new FakeEntity("ZOMBIE");
    FakeEntity unrelated = new FakeEntity("ITEM");
    host.neighbours.put(source.id, List.of(similar, unrelated));
    double sourcePriority = priority.getPriority(source);

    priority.updateCrowd(source, sourcePriority, System.currentTimeMillis());

    double neighbourPriority = priority.getPriority(similar);
    double expected = 1D + M.lerp(1.2D, 0.8D, M.lerpInverse(sourcePriority * 0.25D, sourcePriority * 1.15D, neighbourPriority));
    Assertions.assertEquals(expected, ReactEntity.getCrowding(source), TOLERANCE);
  }

  @Test
  void crowdAndNearbyPlayersShapeTheTickedPriority() {
    EntityPriority priority = new EntityPriority();
    FakeEntity source = new FakeEntity("ZOMBIE");
    FakeEntity a = new FakeEntity("ZOMBIE");
    FakeEntity b = new FakeEntity("ZOMBIE");
    FakeEntity c = new FakeEntity("ZOMBIE");
    host.neighbours.put(source.id, List.of(a, b, c));
    host.playerDistanceSquared = 16D * 16D;

    Assertions.assertTrue(ReactEntity.tick(source, priority));

    double raw = priority.getPriority(source);
    double nearest = M.lerp(1.65D, 0.7D, M.lerpInverse(0D, 64D * 64D, 256D));
    double crowd = ReactEntity.getCrowding(source);
    Assertions.assertEquals(1D + 3D * M.lerp(1.2D, 0.8D, 0.75D / 0.9D), crowd, TOLERANCE);
    Assertions.assertEquals(nearest, ReactEntity.getNearestPlayer(source), TOLERANCE);
    Assertions.assertEquals(raw * nearest * Math.pow(0.9D, crowd - 1D), ReactEntity.getPriority(source), TOLERANCE);
    Assertions.assertFalse(ReactEntity.tick(source, priority));
  }

  @Test
  void aiStaysPausedUntilEveryOwnerReleasesIt() {
    FakeEntity mob = mob();

    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
    ReactEntity.releasePause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertFalse(mob.ai);
    Assertions.assertTrue(ReactEntity.isPaused(mob));
    Assertions.assertTrue(ReactEntity.isPausedBy(mob, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE));

    ReactEntity.releasePause(mob, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);

    Assertions.assertTrue(mob.ai);
    Assertions.assertFalse(ReactEntity.isPaused(mob));
    Assertions.assertTrue(mob.tags.isEmpty());
  }

  @Test
  void aiDisabledByOthersIsNeverClaimedOrRestored() {
    FakeEntity mob = mob();
    mob.ai = false;

    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.releasePause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertFalse(mob.ai);
    Assertions.assertTrue(mob.tags.isEmpty());
  }

  @Test
  void ownerSweepReleasesOnlyThatOwner() {
    FakeEntity sleeping = mob();
    FakeEntity ranged = mob();
    ReactEntity.requestPause(sleeping, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestPause(ranged, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);

    ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertTrue(ReactEntity.awaitManagedCleanup(1000L));
    Assertions.assertTrue(sleeping.ai);
    Assertions.assertFalse(ranged.ai);
  }

  @Test
  void dozeRestoresOnlyAwarenessReactTook() {
    FakeEntity mob = mob();

    ReactEntity.requestDoze(mob);
    Assertions.assertFalse(mob.aware);
    Assertions.assertTrue(ReactEntity.isDozing(mob));

    ReactEntity.releaseDoze(mob);
    Assertions.assertTrue(mob.aware);
    Assertions.assertFalse(ReactEntity.isDozing(mob));

    mob.aware = false;
    ReactEntity.requestDoze(mob);
    ReactEntity.releaseDoze(mob);
    Assertions.assertFalse(mob.aware);
  }

  @Test
  void reloadedEntityWithStaleMarkersIsRestored() {
    FakeEntity mob = mob();
    mob.ai = false;
    mob.aware = false;
    mob.tags.add(ReactEntity.PAUSED_KEY);
    mob.tags.add(ReactEntity.DOZING_KEY);

    ReactEntity.reconcileManagedState(mob);

    Assertions.assertTrue(mob.ai);
    Assertions.assertTrue(mob.aware);
    Assertions.assertTrue(mob.tags.isEmpty());
  }

  @Test
  void unloadReleasesEveryClaim() {
    FakeEntity mob = mob();
    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestDoze(mob);

    ReactEntity.releaseManagedState(mob);

    Assertions.assertTrue(mob.ai);
    Assertions.assertTrue(mob.aware);
    Assertions.assertTrue(mob.tags.isEmpty());
    Assertions.assertFalse(ReactEntity.isPausedBy(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));
  }

  @Test
  void nonMobsAreNeverPausedOrDozed() {
    FakeEntity stand = new FakeEntity("ARMOR_STAND");
    stand.living = true;

    ReactEntity.requestDoze(stand);
    ReactEntity.requestPause(stand, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertTrue(stand.aware);
    Assertions.assertTrue(stand.tags.isEmpty());
  }

  @Test
  void stackCountDefaultsToOneAndPersists() {
    FakeEntity mob = mob();

    Assertions.assertEquals(1, ReactEntity.getStackCount(mob));
    ReactEntity.setStackCount(mob, 7);
    Assertions.assertEquals(7, ReactEntity.getStackCount(mob));
  }

  private FakeEntity mob() {
    FakeEntity mob = new FakeEntity("ZOMBIE");
    mob.living = true;
    mob.mob = true;
    host.known.put(mob.id, mob);
    return mob;
  }

  private static final class FakeEntity implements GameEntity {
    private final UUID id = UUID.randomUUID();
    private final String type;
    private final Set<String> tags = new HashSet<>();
    private final Map<String, Integer> ints = new HashMap<>();
    private boolean living;
    private boolean mob;
    private boolean player;
    private boolean ai = true;
    private boolean aware = true;
    private int ticksLived;
    private double speed;
    private double health;
    private double maxHealth = Double.NaN;
    private boolean tameable;
    private boolean tamed;
    private boolean breedable;
    private boolean adult = true;

    private FakeEntity(String type) {
      this.type = type;
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
      return null;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public int ticksLived() {
      return ticksLived;
    }

    @Override
    public boolean hasCustomName() {
      return false;
    }

    @Override
    public boolean isPlayer() {
      return player;
    }

    @Override
    public Object handle() {
      return this;
    }

    @Override
    public boolean isLiving() {
      return living;
    }

    @Override
    public boolean isMob() {
      return mob;
    }

    @Override
    public double speed() {
      return speed;
    }

    @Override
    public double health() {
      return health;
    }

    @Override
    public double maxHealth() {
      return maxHealth;
    }

    @Override
    public boolean isTameable() {
      return tameable;
    }

    @Override
    public boolean isTamed() {
      return tamed;
    }

    @Override
    public boolean isBreedable() {
      return breedable;
    }

    @Override
    public boolean isAdult() {
      return adult;
    }
  }

  private static final class FakeHost implements EntityHost {
    private final Map<UUID, FakeEntity> known = new HashMap<>();
    private final Map<UUID, List<GameEntity>> neighbours = new HashMap<>();
    private final Map<UUID, Double> itemValues = new HashMap<>();
    private double playerDistanceSquared = Double.POSITIVE_INFINITY;

    @Override
    public List<GameEntity> nearby(ReactPlayer player, double radius) {
      return List.of();
    }

    @Override
    public List<GameEntity> entities(ReactWorld world) {
      return new ArrayList<>(known.values());
    }

    @Override
    public List<GameEntity> chunkEntities(ReactWorld world, int chunkX, int chunkZ) {
      return List.of();
    }

    @Override
    public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ) {
      return -1;
    }

    @Override
    public int worldEntityCount(UUID worldId) {
      return known.size();
    }

    @Override
    public Collection<String> entityTypes() {
      return List.of();
    }

    @Override
    public double priority(GameEntity entity) {
      return ReactEntity.getPriority(entity);
    }

    @Override
    public double typePriority(String type) {
      return EntityPriority.active().getPriority(type);
    }

    @Override
    public boolean isProtected(GameEntity entity, ReactOperation operation) {
      return false;
    }

    @Override
    public boolean allows(GameEntity entity, ReactOperation operation) {
      return true;
    }

    @Override
    public boolean protectsNamed(GameEntity entity, boolean protectNamedEntities) {
      return false;
    }

    @Override
    public boolean isCustomMob(GameEntity entity) {
      return false;
    }

    @Override
    public void remove(GameEntity entity) {
    }

    @Override
    public void kill(GameEntity entity, int seconds) {
    }

    @Override
    public void removeNow(GameEntity entity) {
    }

    @Override
    public boolean isDead(GameEntity entity) {
      return false;
    }

    @Override
    public boolean isTamed(GameEntity entity) {
      return entity.isTamed();
    }

    @Override
    public boolean isVillager(GameEntity entity) {
      return "VILLAGER".equals(entity.type());
    }

    @Override
    public boolean isBoss(GameEntity entity) {
      return false;
    }

    @Override
    public Subscription onTick(Collection<String> types, Consumer<GameEntity> listener) {
      return Subscription.NONE;
    }

    @Override
    public GameEntity wrap(Object handle) {
      return (FakeEntity) handle;
    }

    @Override
    public List<GameEntity> nearbyEntities(GameEntity entity, double radius) {
      return neighbours.getOrDefault(entity.id(), List.of());
    }

    @Override
    public double nearestPlayerDistanceSquared(GameEntity entity, double maxDistance) {
      return playerDistanceSquared;
    }

    @Override
    public double itemValue(GameEntity entity) {
      return itemValues.getOrDefault(entity.id(), 0D);
    }

    @Override
    public int persistentInt(GameEntity entity, String key, int fallback) {
      return fake(entity).ints.getOrDefault(key, fallback);
    }

    @Override
    public void setPersistentInt(GameEntity entity, String key, int value) {
      fake(entity).ints.put(key, value);
    }

    @Override
    public boolean persistentFlag(GameEntity entity, String key) {
      return fake(entity).tags.contains(key);
    }

    @Override
    public void setPersistentFlag(GameEntity entity, String key, boolean value) {
      if (value) {
        fake(entity).tags.add(key);
      } else {
        fake(entity).tags.remove(key);
      }
    }

    @Override
    public boolean hasAi(GameEntity entity) {
      return fake(entity).mob && fake(entity).ai;
    }

    @Override
    public void setAi(GameEntity entity, boolean ai) {
      if (fake(entity).mob) {
        fake(entity).ai = ai;
      }
    }

    @Override
    public boolean isAware(GameEntity entity) {
      return fake(entity).mob && fake(entity).aware;
    }

    @Override
    public void setAware(GameEntity entity, boolean aware) {
      if (fake(entity).mob) {
        fake(entity).aware = aware;
      }
    }

    private static FakeEntity fake(GameEntity entity) {
      return (FakeEntity) entity;
    }
  }

  private static final class FakePlatform implements ReactPlatform {
    private final EntityHost host;
    private final PlatformScheduler scheduler = Mockito.mock(PlatformScheduler.class);

    private FakePlatform(EntityHost host) {
      this.host = host;
      Mockito.when(scheduler.isPrimaryThread()).thenReturn(true);
      Mockito.when(scheduler.isRegionized()).thenReturn(false);
    }

    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "Test";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return scheduler;
    }

    @Override
    public ExecutorService asyncExecutor() {
      return Mockito.mock(ExecutorService.class);
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
      throw new AssertionError(context, failure);
    }

    @Override
    public Optional<EntityHost> entityHost() {
      return Optional.of(host);
    }
  }
}
