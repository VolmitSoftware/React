package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FeatureEntityTrimmerAnchorTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void playersInNeighbouringChunksAreEachScannedEveryCycle() {
    World world = world();
    Player west = player(world, 0D, 64D, 0D);
    Player east = player(world, 31D, 64D, 0D);

    runCycles(world, 1, west, east);

    Mockito.verify(west).getNearbyEntities(32D, 32D, 32D);
    Mockito.verify(east).getNearbyEntities(32D, 32D, 32D);
  }

  @Test
  void playersInOneChunkColumnAtDifferentHeightsAreEachScannedEveryCycle() {
    World world = world();
    Player surface = player(world, 3D, 64D, 3D);
    Player mine = player(world, 5D, -40D, 4D);

    runCycles(world, 1, surface, mine);

    Mockito.verify(surface).getNearbyEntities(32D, 32D, 32D);
    Mockito.verify(mine).getNearbyEntities(32D, 32D, 32D);
  }

  @Test
  void playersSharingAChunkSectionTakeTurnsLeadingTheScan() {
    World world = world();
    Player first = player(world, 1D, 64D, 1D);
    Player second = player(world, 9D, 70D, 12D);
    Player third = player(world, 14D, 66D, 4D);

    runCycles(world, 3, first, second, third);

    Mockito.verify(first).getNearbyEntities(32D, 32D, 32D);
    Mockito.verify(second).getNearbyEntities(32D, 32D, 32D);
    Mockito.verify(third).getNearbyEntities(32D, 32D, 32D);
  }

  private static void runCycles(World world, int cycles, Player... players) {
    NearbyPlayerIndexController index = snapshotIndex(world.getUID(), players);
    List<Runnable> jobs = new ArrayList<>();
    FeatureEntityTrimmer feature = new FeatureEntityTrimmer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(index);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(players));

      for (int cycle = 0; cycle < cycles; cycle++) {
        feature.onTick();
        while (!jobs.isEmpty()) {
          jobs.removeFirst().run();
        }
      }
    }
  }

  private static World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }

  private static Player player(World world, double x, double y, double z) {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    Mockito.when(player.getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(List.of());
    return player;
  }

  private static NearbyPlayerIndexController snapshotIndex(UUID worldId, Player... players) {
    NearbyPlayerIndexController index = Mockito.mock(NearbyPlayerIndexController.class);
    for (Player player : players) {
      Location location = player.getLocation();
      UUID playerId = player.getUniqueId();
      Optional<NearbyPlayerIndexController.PlayerViewSnapshot> snapshot = Optional.of(
          new NearbyPlayerIndexController.PlayerViewSnapshot(
              playerId,
              "player",
              worldId,
              location.getX(),
              location.getY(),
              location.getZ(),
              0D,
              false,
              false
          )
      );
      Mockito.when(index.playerSnapshot(playerId)).thenReturn(snapshot);
    }
    Mockito.clearInvocations((Object[]) players);
    return index;
  }
}
