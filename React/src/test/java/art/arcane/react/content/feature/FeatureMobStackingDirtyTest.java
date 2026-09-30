package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class FeatureMobStackingDirtyTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void rotationRevisitsDoNotRedirtyAQuietChunk() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = world();
    LivingEntity zombie = zombie(world, 4D, 4D);
    AtomicInteger stack = new AtomicInteger(1);
    Mockito.doAnswer(invocation -> stack.get()).when(feature).getStackCount(zombie);
    long key = FeatureMobStacking.packChunkKey(0, 0);

    feature.onTick(zombie);
    Assertions.assertTrue(dirty(feature, world).contains(key));
    completeQuietPass(feature, world, zombie);
    dirty(feature, world).clear();

    feature.onTick(zombie);
    feature.onTick(zombie);

    Assertions.assertFalse(dirty(feature, world).contains(key));
  }

  @Test
  void stackCountChangeRedirtiesAQuietChunk() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = world();
    LivingEntity zombie = zombie(world, 4D, 4D);
    AtomicInteger stack = new AtomicInteger(1);
    Mockito.doAnswer(invocation -> stack.get()).when(feature).getStackCount(zombie);
    long key = FeatureMobStacking.packChunkKey(0, 0);
    feature.onTick(zombie);
    completeQuietPass(feature, world, zombie);
    dirty(feature, world).clear();

    stack.set(2);
    feature.onTick(zombie);

    Assertions.assertTrue(dirty(feature, world).contains(key));
  }

  @Test
  void chunkChangeRedirtiesTheNewChunk() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = world();
    Location first = new Location(world, 4D, 64D, 4D);
    Location moved = new Location(world, 20D, 64D, 4D);
    LivingEntity zombie = zombie(world, 4D, 4D);
    Mockito.when(zombie.getLocation()).thenReturn(first, first, moved);
    Mockito.doReturn(1).when(feature).getStackCount(zombie);
    feature.onTick(zombie);
    completeQuietPass(feature, world, zombie);
    dirty(feature, world).clear();

    feature.onTick(zombie);
    Assertions.assertTrue(dirty(feature, world).isEmpty());
    feature.onTick(zombie);

    Assertions.assertTrue(dirty(feature, world).contains(FeatureMobStacking.packChunkKey(1, 0)));
  }

  @Test
  void paperClaimsAreQueuedAsOneJobEach() throws ReflectiveOperationException {
    FeatureMobStacking feature = activeFeature();
    World world = world();
    Set<Long> pending = dirty(feature, world);
    for (int chunkX = 0; chunkX < 3; chunkX++) {
      pending.add(FeatureMobStacking.packChunkKey(chunkX, 0));
    }
    List<Runnable> jobs = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });

      feature.onTick();
    }

    Assertions.assertEquals(3, jobs.size());
  }

  @Test
  void removingAnUnindexedEntitySkipsTheIndexMonitor() throws Exception {
    FeatureMobStacking feature = new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class));
    Entity arrow = Mockito.mock(Entity.class);
    Mockito.when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
    EntityRemoveEvent event = new EntityRemoveEvent(arrow, EntityRemoveEvent.Cause.DESPAWN);
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder = new Thread(() -> {
      synchronized (feature) {
        held.countDown();
        try {
          release.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    });
    holder.start();
    Assertions.assertTrue(held.await(5L, TimeUnit.SECONDS));
    Thread worker = new Thread(() -> feature.on(event));
    try {
      worker.start();
      worker.join(2_000L);
      Assertions.assertFalse(worker.isAlive(), "entity removal waited on the stack index monitor");
    } finally {
      release.countDown();
      holder.join(5_000L);
      worker.join(5_000L);
    }
  }

  private void completeQuietPass(FeatureMobStacking feature, World world, LivingEntity only)
      throws ReflectiveOperationException {
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(world.getChunkAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(chunk);
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{only});
    Method stackChunk = FeatureMobStacking.class.getDeclaredMethod("stackChunk", World.class, int.class, int.class);
    stackChunk.setAccessible(true);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      Assertions.assertTrue((boolean) stackChunk.invoke(feature, world, 0, 0));
    }
  }

  private FeatureMobStacking activeFeature() throws ReflectiveOperationException {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class)));
    Field active = FeatureMobStacking.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    return feature;
  }

  @SuppressWarnings("unchecked")
  private Set<Long> dirty(FeatureMobStacking feature, World world) throws ReflectiveOperationException {
    Field dirtyChunks = FeatureMobStacking.class.getDeclaredField("dirtyChunks");
    dirtyChunks.setAccessible(true);
    Map<UUID, Set<Long>> byWorld = (Map<UUID, Set<Long>>) dirtyChunks.get(feature);
    return byWorld.computeIfAbsent(world.getUID(), ignored -> ConcurrentHashMap.newKeySet());
  }

  private static World world() {
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    return world;
  }

  private static LivingEntity zombie(World world, double x, double z) {
    LivingEntity zombie = Mockito.mock(LivingEntity.class);
    Location location = new Location(world, x, 64D, z);
    Mockito.when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(zombie.getLocation()).thenReturn(location);
    return zombie;
  }
}
