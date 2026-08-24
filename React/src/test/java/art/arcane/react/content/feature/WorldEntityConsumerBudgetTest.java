package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class WorldEntityConsumerBudgetTest {
  private static React previous;

  @BeforeAll
  static void setUpPlugin() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterAll
  static void restorePlugin() {
    React.instance = previous;
  }

  @Test
  void paperConsumersRequestOnlyTheirConfiguredRotationBudgets() throws Exception {
    World world = Mockito.mock(World.class);
    FeatureAdaptiveEntitySleep sleep = active(new FeatureAdaptiveEntitySleep());
    FeatureDynamicActivationRange activation = active(new FeatureDynamicActivationRange());
    FeatureItemBackpressure backpressure = active(new FeatureItemBackpressure());
    FeaturePathfinderBudget pathfinder = active(new FeaturePathfinderBudget());

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      snapshots.when(() -> WorldEntitySnapshots.next(world, 320)).thenReturn(List.of());
      snapshots.when(() -> WorldEntitySnapshots.next(world, 240)).thenReturn(List.of());
      snapshots.when(() -> WorldEntitySnapshots.next(world, 220)).thenReturn(List.of());

      invoke(sleep, "applySleepScan", new Class<?>[]{long.class}, 1L);
      invoke(activation, "applyActivationRange", new Class<?>[]{long.class, double.class}, 1L, 32D);
      invoke(backpressure, "removeRemoteItems", new Class<?>[]{boolean.class, long.class}, true, 1L);
      invoke(pathfinder, "applyScan", new Class<?>[]{long.class}, 1L);

      snapshots.verify(() -> WorldEntitySnapshots.next(world, 320));
      snapshots.verify(() -> WorldEntitySnapshots.next(world, 240), Mockito.times(2));
      snapshots.verify(() -> WorldEntitySnapshots.next(world, 220));
      bukkit.verify(Bukkit::getWorlds, Mockito.times(4));
    }
  }

  private <T> T active(T feature) throws ReflectiveOperationException {
    Field active = feature.getClass().getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    Field lifecycle = feature.getClass().getDeclaredField("lifecycleGeneration");
    lifecycle.setAccessible(true);
    ((AtomicLong) lifecycle.get(feature)).set(1L);
    if (feature instanceof FeaturePathfinderBudget) {
      Field bridges = feature.getClass().getDeclaredField("bridgesAvailable");
      bridges.setAccessible(true);
      bridges.setBoolean(feature, true);
    }
    return feature;
  }

  private void invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments)
      throws ReflectiveOperationException {
    Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
    method.setAccessible(true);
    method.invoke(target, arguments);
  }
}
