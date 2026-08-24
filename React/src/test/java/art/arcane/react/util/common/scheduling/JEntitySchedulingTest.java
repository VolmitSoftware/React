package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class JEntitySchedulingTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void rejectedFoliaEntityTaskNeverFallsBackToLastKnownRegion() {
    Entity entity = Mockito.mock(Entity.class);
    Server server = Mockito.mock(Server.class);
    AtomicInteger operations = new AtomicInteger();
    AtomicInteger retirements = new AtomicInteger();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> folia = Mockito.mockStatic(FoliaScheduler.class)) {
      bukkit.when(Bukkit::getServer).thenReturn(server);
      folia.when(() -> FoliaScheduler.isFoliaThreading(server)).thenReturn(true);
      folia.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
      folia.when(() -> FoliaScheduler.runEntity(
          Mockito.same(React.instance),
          Mockito.same(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0L),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable retired = invocation.getArgument(4);
        retired.run();
        return false;
      });

      boolean accepted = J.runEntity(entity, operations::incrementAndGet, 0, retirements::incrementAndGet);

      Assertions.assertFalse(accepted);
      Assertions.assertEquals(0, operations.get());
      Assertions.assertEquals(1, retirements.get());
      Mockito.verify(entity, Mockito.never()).getLocation();
    }
  }
}
