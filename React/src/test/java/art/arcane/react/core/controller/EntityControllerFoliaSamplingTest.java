package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class EntityControllerFoliaSamplingTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React.instance = Mockito.mock(React.class);
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void onlinePlayersAreCapturedOnlyInsideTheGlobalTask() {
    EntityController controller = new EntityController();
    Player first = Mockito.mock(Player.class);
    Player second = Mockito.mock(Player.class);
    AtomicReference<Runnable> globalTask = new AtomicReference<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, second));
      scheduler.when(() -> FoliaScheduler.runGlobal(Mockito.same(React.instance), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            globalTask.set(invocation.getArgument(1));
            return true;
          });

      controller.requestFoliaPlayerSnapshot();

      bukkit.verify(Bukkit::getOnlinePlayers, Mockito.never());
      Assertions.assertNotNull(globalTask.get());
      globalTask.get().run();

      bukkit.verify(Bukkit::getOnlinePlayers);
      Assertions.assertArrayEquals(new Player[]{first, second}, controller.getFoliaPlayers());
    }
  }
}
