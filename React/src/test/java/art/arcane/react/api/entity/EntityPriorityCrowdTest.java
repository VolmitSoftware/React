package art.arcane.react.api.entity;

import art.arcane.react.React;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class EntityPriorityCrowdTest {
  private static final double TOLERANCE = 1e-9;
  private static final EntityType[] CLUSTER_TYPES = {
      EntityType.ZOMBIE,
      EntityType.COW,
      EntityType.SKELETON,
      EntityType.PIG,
      EntityType.ARROW,
      EntityType.SHEEP,
      EntityType.BAT,
      EntityType.CHICKEN
  };
  private static React previous;

  private MockedStatic<J> scheduling;

  @BeforeAll
  static void setUpPlugin() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    React.instance = plugin;
  }

  @AfterAll
  static void restorePlugin() {
    React.instance = previous;
  }

  @BeforeEach
  void setUp() {
    ReactEntity.clearScratch();
    scheduling = Mockito.mockStatic(J.class);
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
    ReactEntity.clearScratch();
  }

  @Test
  void cachedCrowdingMatchesUncachedComputation() {
    List<Entity> cluster = cluster(24);
    CountingPriority priority = new CountingPriority();
    EntityPriority reference = new EntityPriority();

    for (Entity entity : cluster) {
      Assertions.assertTrue(ReactEntity.tick(entity, priority));
    }

    double maxCrowding = 1;
    for (Entity entity : cluster) {
      double expectedCrowding = uncachedCrowding(reference, entity, cluster);
      double expectedPriority = reference.getPriorityWithCrowd(entity, reference.getPriority(entity), expectedCrowding);
      Assertions.assertEquals(expectedCrowding, ReactEntity.getCrowding(entity), TOLERANCE);
      Assertions.assertEquals(expectedPriority, ReactEntity.getPriority(entity), TOLERANCE);
      maxCrowding = Math.max(maxCrowding, expectedCrowding);
    }
    Assertions.assertTrue(maxCrowding > 2);
  }

  @Test
  void priorityEvaluationsPerPassAreLinearInClusterSize() {
    int size = 24;
    List<Entity> cluster = cluster(size);
    CountingPriority priority = new CountingPriority();

    for (Entity entity : cluster) {
      ReactEntity.tick(entity, priority);
    }
    Assertions.assertEquals(2 * size - 1, priority.calls);

    priority.calls = 0;
    for (Entity entity : cluster) {
      ReactEntity.setLastTick(entity, 0L);
    }
    for (Entity entity : cluster) {
      Assertions.assertTrue(ReactEntity.tick(entity, priority));
    }
    Assertions.assertEquals(size, priority.calls);
  }

  @Test
  void staleNeighbourPriorityIsRecomputed() {
    List<Entity> cluster = cluster(6);
    Entity source = cluster.get(0);
    Entity neighbour = cluster.get(1);
    CountingPriority priority = new CountingPriority();
    EntityPriority reference = new EntityPriority();
    long now = System.currentTimeMillis();
    for (Entity entity : cluster) {
      ReactEntity.setRawPriority(entity, reference.getPriority(entity), now);
    }
    ReactEntity.setRawPriority(neighbour, 1_000_000D, now - 20_000L);

    priority.updateCrowd(source, reference.getPriority(source), now);

    Assertions.assertEquals(1, priority.calls);
    Assertions.assertEquals(uncachedCrowding(reference, source, cluster), ReactEntity.getCrowding(source), TOLERANCE);
    Assertions.assertEquals(reference.getPriority(neighbour), ReactEntity.getRawPriority(neighbour, now), TOLERANCE);
  }

  @Test
  void rawPriorityExpiresAfterTheRefreshInterval() {
    Entity entity = cluster(1).get(0);
    long now = System.currentTimeMillis();

    Assertions.assertTrue(Double.isNaN(ReactEntity.getRawPriority(entity, now)));

    ReactEntity.setRawPriority(entity, 42D, now);

    Assertions.assertEquals(42D, ReactEntity.getRawPriority(entity, now + 10_000L));
    Assertions.assertTrue(Double.isNaN(ReactEntity.getRawPriority(entity, now + 10_001L)));
    Assertions.assertTrue(Double.isNaN(ReactEntity.getRawPriority(entity, now - 1L)));
  }

  private static double uncachedCrowding(EntityPriority priority, Entity source, List<Entity> cluster) {
    double sourcePriority = priority.getPriority(source);
    double minPriority = sourcePriority * 0.25;
    double maxPriority = sourcePriority * 1.15;
    double count = 1;
    for (Entity other : cluster) {
      if (other == source) {
        continue;
      }

      double otherPriority = priority.getPriority(other);
      if (otherPriority < minPriority || otherPriority > maxPriority) {
        continue;
      }

      count += M.lerp(1.2, 0.8, M.lerpInverse(minPriority, maxPriority, otherPriority));
    }
    return count;
  }

  private static List<Entity> cluster(int size) {
    List<Entity> cluster = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Entity entity = Mockito.mock(Entity.class);
      Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(entity.getType()).thenReturn(CLUSTER_TYPES[i % CLUSTER_TYPES.length]);
      Mockito.when(entity.getTicksLived()).thenReturn((i * 173) % 1800);
      cluster.add(entity);
    }
    for (Entity entity : cluster) {
      List<Entity> neighbours = new ArrayList<>(cluster);
      neighbours.remove(entity);
      Mockito.when(entity.getNearbyEntities(8D, 8D, 8D)).thenReturn(neighbours);
    }
    return cluster;
  }

  private static final class CountingPriority extends EntityPriority {
    private int calls;

    @Override
    public double getPriority(Entity e) {
      calls++;
      return super.getPriority(e);
    }
  }
}
