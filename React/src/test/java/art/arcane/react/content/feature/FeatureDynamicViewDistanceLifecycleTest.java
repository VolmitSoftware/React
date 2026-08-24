package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class FeatureDynamicViewDistanceLifecycleTest {
  @Test
  void disableRestoresTheExactDistancesCapturedBeforeFirstMutation() throws ReflectiveOperationException {
    FeatureDynamicViewDistance feature = new FeatureDynamicViewDistance();
    WorldDistanceState distances = new WorldDistanceState(12, 8);
    World world = distances.world();
    Server server = server();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      configureBukkit(bukkit, server, world);

      feature.onActivate();
      updateWorlds(feature, System.currentTimeMillis(), 140D);
      Assertions.assertEquals(6, distances.viewDistance.get());
      Assertions.assertEquals(4, distances.simulationDistance.get());

      distances.viewDistance.set(10);
      distances.simulationDistance.set(7);
      updateWorlds(feature, System.currentTimeMillis() + 121_000L, 45D);
      Assertions.assertEquals(16, distances.viewDistance.get());
      Assertions.assertEquals(10, distances.simulationDistance.get());

      feature.onDeactivate();

      Assertions.assertEquals(12, distances.viewDistance.get());
      Assertions.assertEquals(8, distances.simulationDistance.get());
    }
  }

  @Test
  void queuedUpdateCannotMutateAWorldAfterDisable() throws ReflectiveOperationException {
    FeatureDynamicViewDistance feature = new FeatureDynamicViewDistance();
    setInt(feature, "warmupSeconds", 0);
    WorldDistanceState distances = new WorldDistanceState(12, 8);
    World world = distances.world();
    Server server = server();
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(140D);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      configureBukkit(bukkit, server, world);

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, queued.size());

      feature.onDeactivate();
      queued.getFirst().run();

      Assertions.assertEquals(12, distances.viewDistance.get());
      Assertions.assertEquals(8, distances.simulationDistance.get());
      Assertions.assertEquals(0, distances.mutations.get());
    }
  }

  private static void configureBukkit(MockedStatic<Bukkit> bukkit, Server server, World world) {
    bukkit.when(Bukkit::getServer).thenReturn(server);
    bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
    bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.<Player>of());
  }

  private static Server server() {
    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getViewDistance()).thenReturn(16);
    Mockito.when(server.getSimulationDistance()).thenReturn(10);
    return server;
  }

  private static void updateWorlds(FeatureDynamicViewDistance feature, long now, double tickAverage)
      throws ReflectiveOperationException {
    Method method = FeatureDynamicViewDistance.class.getDeclaredMethod(
        "updateWorlds",
        long.class,
        double.class,
        long.class
    );
    method.setAccessible(true);
    method.invoke(feature, now, tickAverage, lifecycleGeneration(feature));
  }

  private static long lifecycleGeneration(FeatureDynamicViewDistance feature) throws ReflectiveOperationException {
    Field field = FeatureDynamicViewDistance.class.getDeclaredField("lifecycleGeneration");
    field.setAccessible(true);
    AtomicLong generation = (AtomicLong) field.get(feature);
    return generation.get();
  }

  private static void setInt(Object target, String fieldName, int value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(target, value);
  }

  private static final class WorldDistanceState {
    private final AtomicInteger viewDistance;
    private final AtomicInteger simulationDistance;
    private final AtomicInteger mutations;
    private final World world;

    private WorldDistanceState(int viewDistance, int simulationDistance) {
      this.viewDistance = new AtomicInteger(viewDistance);
      this.simulationDistance = new AtomicInteger(simulationDistance);
      mutations = new AtomicInteger(0);
      world = Mockito.mock(World.class);
      Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
      Mockito.when(world.getViewDistance()).thenAnswer(invocation -> this.viewDistance.get());
      Mockito.when(world.getSimulationDistance()).thenAnswer(invocation -> this.simulationDistance.get());
      Mockito.doAnswer(invocation -> {
        this.viewDistance.set(invocation.getArgument(0));
        mutations.incrementAndGet();
        return null;
      }).when(world).setViewDistance(Mockito.anyInt());
      Mockito.doAnswer(invocation -> {
        this.simulationDistance.set(invocation.getArgument(0));
        mutations.incrementAndGet();
        return null;
      }).when(world).setSimulationDistance(Mockito.anyInt());
    }

    private World world() {
      return world;
    }
  }
}
