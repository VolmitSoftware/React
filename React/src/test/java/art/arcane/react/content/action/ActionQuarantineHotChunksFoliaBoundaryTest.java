package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ActionQuarantineHotChunksFoliaBoundaryTest {
  @Test
  void playerSafetyUsesOnlyTheImmutableIndexSnapshot() {
    ActionQuarantineHotChunks action = new ActionQuarantineHotChunks();
    NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    World world = Mockito.mock(World.class);
    Mockito.when(world.getPlayers()).thenThrow(new AssertionError("cross-region world player scan"));
    Mockito.when(playerIndex.isInitialSeedReady()).thenReturn(true);
    Mockito.when(playerIndex.hasNearbyPlayerInColumn(world, 40D, -24D, 56D)).thenReturn(true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);

      Assertions.assertTrue(action.hasNearbyPlayer(world, 2, -2, 56D));

      Mockito.verify(world, Mockito.never()).getPlayers();
      Mockito.verify(playerIndex).hasNearbyPlayerInColumn(world, 40D, -24D, 56D);
    }
  }

  @Test
  void incompleteInitialPlayerSeedFailsClosed() {
    ActionQuarantineHotChunks action = new ActionQuarantineHotChunks();
    NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    World world = Mockito.mock(World.class);
    Mockito.when(playerIndex.isInitialSeedReady()).thenReturn(false);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);

      Assertions.assertTrue(action.hasNearbyPlayer(world, 0, 0, 56D));
      Mockito.verify(playerIndex, Mockito.never()).hasNearbyPlayerInColumn(
          Mockito.any(World.class),
          Mockito.anyDouble(),
          Mockito.anyDouble(),
          Mockito.anyDouble()
      );
    }
  }

  @Test
  void missingPlayerIndexFailsClosed() {
    ActionQuarantineHotChunks action = new ActionQuarantineHotChunks();
    World world = Mockito.mock(World.class);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(null);

      Assertions.assertTrue(action.hasNearbyPlayer(world, 0, 0, 56D));
      Mockito.verifyNoInteractions(world);
    }
  }
}
