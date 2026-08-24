package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class FeatureRandomTickGovernorLifecycleTest {
  @Test
  void tailFirstReleasePreventsAnOlderEngageFromApplying() throws Exception {
    TestRandomTickGovernor feature = new TestRandomTickGovernor();
    World world = world();
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });

      feature.onActivate();
      setEngaged(feature, true);
      invoke(feature, "engage");
      setEngaged(feature, false);
      invoke(feature, "release");
      Assertions.assertEquals(2, queued.size());

      queued.get(1).run();
      queued.get(0).run();

      Assertions.assertTrue(feature.writes.isEmpty());
    }
  }

  @Test
  void deactivationRestoresBeforeAStaleEngageCanRun() throws Exception {
    TestRandomTickGovernor feature = new TestRandomTickGovernor();
    World world = world();
    UUID worldId = world.getUID();
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      scheduling.when(J::isPrimaryThread).thenReturn(true);

      feature.onActivate();
      setEngaged(feature, true);
      invoke(feature, "engage");
      queued.removeFirst().run();
      invoke(feature, "engage");
      Assertions.assertEquals(1, queued.size());

      feature.onDeactivate();
      queued.getFirst().run();

      Assertions.assertEquals(List.of(1, 3), feature.writes);
    }
  }

  private World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }

  private void setEngaged(FeatureRandomTickGovernor feature, boolean engaged)
      throws ReflectiveOperationException {
    Field field = FeatureRandomTickGovernor.class.getDeclaredField("gate");
    field.setAccessible(true);
    PressureGate gate = (PressureGate) field.get(feature);
    gate.reset();
    if (!engaged) {
      return;
    }

    gate.update(1L, true, false, 0L, 0L);
    gate.update(2L, true, false, 0L, 0L);
  }

  private void invoke(FeatureRandomTickGovernor feature, String methodName)
      throws ReflectiveOperationException {
    Method method = FeatureRandomTickGovernor.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(feature);
  }

  private static final class TestRandomTickGovernor extends FeatureRandomTickGovernor {
    private final Map<UUID, Integer> values = new HashMap<>();
    private final List<Integer> writes = new ArrayList<>();

    @Override
    Integer readRandomTickSpeed(World world) {
      return values.computeIfAbsent(world.getUID(), ignored -> 3);
    }

    @Override
    boolean writeRandomTickSpeed(World world, int value) {
      values.put(world.getUID(), value);
      writes.add(value);
      return true;
    }
  }
}
