package art.arcane.react.platform.bukkit;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BukkitMonitorDisplayFoliaTest {
  @Test
  void offOwnerRendersCoalesceUntilTheAcceptedTaskTerminates() {
    UUID viewerId = UUID.randomUUID();
    Player player = Mockito.mock(Player.class);
    AtomicReference<Runnable> operation = new AtomicReference<>();
    AtomicInteger renders = new AtomicInteger();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(player);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            operation.set(invocation.getArgument(1));
            return true;
          });

      for (int index = 0; index < 1_000; index++) {
        BukkitMonitorDisplay.INSTANCE.render(viewerId, renders::incrementAndGet);
      }
      scheduling.verify(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class), Mockito.eq(0), Mockito.any(Runnable.class)),
          Mockito.times(1));

      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      operation.get().run();
      Assertions.assertEquals(1, renders.get());

      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      BukkitMonitorDisplay.INSTANCE.render(viewerId, renders::incrementAndGet);
      scheduling.verify(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class), Mockito.eq(0), Mockito.any(Runnable.class)),
          Mockito.times(2));
      BukkitMonitorDisplay.INSTANCE.clear(viewerId, false);
    }
  }

  @Test
  void rejectedSubmissionsReleaseTheRenderClaimEachTime() {
    UUID viewerId = UUID.randomUUID();
    Player player = Mockito.mock(Player.class);
    AtomicInteger attempts = new AtomicInteger();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(player);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class), Mockito.eq(0), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            attempts.incrementAndGet();
            Runnable retired = invocation.getArgument(3);
            retired.run();
            return false;
          });

      BukkitMonitorDisplay.INSTANCE.render(viewerId, () -> {
      });
      BukkitMonitorDisplay.INSTANCE.render(viewerId, () -> {
      });
      BukkitMonitorDisplay.INSTANCE.render(viewerId, () -> {
      });

      Assertions.assertEquals(3, attempts.get());
    }
  }
}
