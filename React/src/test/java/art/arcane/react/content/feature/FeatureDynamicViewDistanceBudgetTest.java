package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.model.MinMax;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureDynamicViewDistanceBudgetTest {
  @Test
  void capturesOnceAndCalculatesOffThreadBeforeApplyingBudget() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.feature.onTick();
      assertEquals(1, fixture.global.size());
      fixture.global.removeFirst().run();
      assertEquals(1, fixture.async.size());
      assertEquals(16, fixture.view.get());
      Mockito.verify(fixture.index, Mockito.times(1)).playerSnapshots();
      fixture.async.removeFirst().run();
      assertEquals(16, fixture.view.get());
      fixture.global.removeFirst().run();
      assertEquals(7, fixture.view.get());
      assertEquals(5, fixture.simulation.get());
      fixture.feature.onDeactivate();
      assertEquals(16, fixture.view.get());
      assertEquals(10, fixture.simulation.get());
    }
  }

  @Test
  void unseededIndexAndUnknownTelemetryNeverMutateDistances() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Mockito.when(fixture.index.isInitialSeedReady()).thenReturn(false);
      fixture.runCycle();
      assertTrue(fixture.async.isEmpty());
      Mockito.verify(fixture.index, Mockito.never()).playerSnapshots();
      Mockito.when(fixture.index.isInitialSeedReady()).thenReturn(true);
      Mockito.when(fixture.sampler.isSampleAvailable()).thenReturn(false);
      fixture.feature.onTick();
      assertTrue(fixture.global.isEmpty());
      assertEquals(16, fixture.view.get());
      assertEquals(10, fixture.simulation.get());
    }
  }

  @Test
  void recoveryCountsCooldownSpacedEvaluationsAndResetsOnMissingTelemetry() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.view.set(6);
      fixture.simulation.set(4);
      fixture.runCycle();
      assertEquals(6, fixture.view.get());
      fixture.runCycle();
      Mockito.verify(fixture.index, Mockito.times(1)).playerSnapshots();
      fixture.nextCycle();
      assertEquals(6, fixture.view.get());
      Mockito.when(fixture.sampler.isSampleAvailable()).thenReturn(false);
      fixture.feature.onTick();
      Mockito.when(fixture.sampler.isSampleAvailable()).thenReturn(true);
      fixture.nextCycle();
      fixture.nextCycle();
      assertEquals(6, fixture.view.get());
      fixture.nextCycle();
      assertEquals(6, fixture.view.get());
      assertEquals(5, fixture.simulation.get());
      fixture.nextCycle();
      fixture.nextCycle();
      fixture.nextCycle();
      assertEquals(7, fixture.view.get());
      assertEquals(5, fixture.simulation.get());
    }
  }

  @Test
  void calculationFromRetiredGenerationCannotApplyAfterReactivation() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.feature.onTick();
      fixture.global.removeFirst().run();
      fixture.async.removeFirst().run();
      Runnable staleApply = fixture.global.removeFirst();
      fixture.feature.onDeactivate();
      fixture.feature.onActivate();
      fixture.feature.onTick();
      assertEquals(1, fixture.global.size());
      staleApply.run();
      fixture.feature.onTick();
      assertEquals(1, fixture.global.size());
      assertEquals(16, fixture.view.get());
      assertEquals(10, fixture.simulation.get());
    }
  }

  @Test
  void configuredMinimumCannotExceedTheServerCeiling() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.feature.onDeactivate();
      field(fixture.feature, "viewDistance", new MinMax(20, 30));
      field(fixture.feature, "simulationDistance", new MinMax(20, 30));
      Mockito.when(fixture.server.getViewDistance()).thenReturn(5);
      Mockito.when(fixture.server.getSimulationDistance()).thenReturn(3);
      fixture.feature.onActivate();
      fixture.runCycle();
      assertEquals(5, fixture.view.get());
      assertEquals(3, fixture.simulation.get());
    }
  }

  private static void field(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class Fixture implements AutoCloseable {
    private final MockedStatic<React> react = Mockito.mockStatic(React.class);
    private final MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
    private final MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
    private final FeatureDynamicViewDistance feature = new FeatureDynamicViewDistance();
    private final NearbyPlayerIndexController index = Mockito.mock(NearbyPlayerIndexController.class);
    private final Sampler sampler = Mockito.mock(Sampler.class);
    private final Server server = Mockito.mock(Server.class);
    private final World world = Mockito.mock(World.class);
    private final AtomicInteger view = new AtomicInteger(16);
    private final AtomicInteger simulation = new AtomicInteger(10);
    private final ArrayDeque<Runnable> global = new ArrayDeque<>();
    private final ArrayDeque<Runnable> async = new ArrayDeque<>();

    private Fixture() throws Exception {
      field(feature, "playerChunkBudgetEnabled", true);
      field(feature, "playerTickingChunkBudget", 121L);
      field(feature, "playerViewOnlyChunkBudget", 104L);
      field(feature, "warmupSeconds", 0);
      UUID worldId = UUID.randomUUID();
      Mockito.when(world.getUID()).thenReturn(worldId);
      Mockito.when(world.getViewDistance()).thenAnswer(invocation -> view.get());
      Mockito.when(world.getSimulationDistance()).thenAnswer(invocation -> simulation.get());
      Mockito.doAnswer(invocation -> { view.set(invocation.getArgument(0)); return null; }).when(world).setViewDistance(Mockito.anyInt());
      Mockito.doAnswer(invocation -> { simulation.set(invocation.getArgument(0)); return null; }).when(world).setSimulationDistance(Mockito.anyInt());
      Mockito.when(server.getViewDistance()).thenReturn(16);
      Mockito.when(server.getSimulationDistance()).thenReturn(10);
      Mockito.when(index.isInitialSeedReady()).thenReturn(true);
      Mockito.when(index.playerSnapshots()).thenReturn(List.of(
          new PlayerViewSnapshot(UUID.randomUUID(), "Player", worldId, -0.5D, 64D, -16.5D, 0D, false, false)));
      Mockito.when(sampler.sample()).thenReturn(20D);
      Mockito.when(sampler.isSampleAvailable()).thenReturn(true);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(index);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      bukkit.when(Bukkit::getServer).thenReturn(server);
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> { global.add(invocation.getArgument(0)); return null; });
      scheduling.when(() -> J.a(Mockito.any(Runnable.class))).thenAnswer(invocation -> { async.add(invocation.getArgument(0)); return null; });
      feature.onActivate();
    }

    private void runCycle() {
      feature.onTick();
      while (!global.isEmpty() || !async.isEmpty()) {
        if (!global.isEmpty()) global.removeFirst().run();
        if (!async.isEmpty()) async.removeFirst().run();
      }
    }

    private void nextCycle() throws Exception {
      field(feature, "lastBudgetEvaluationMs", 0L);
      runCycle();
    }

    @Override
    public void close() {
      scheduling.close();
      bukkit.close();
      react.close();
    }
  }
}
