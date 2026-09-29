package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.testutil.Fakes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class FeatureIrisTerrainSurgeGuardMoveTest {
  @Test
  void movesInsideOneChunkNeverEvaluateTheSurgeVerdict() {
    FeatureIrisTerrainSurgeGuard guard = new FeatureIrisTerrainSurgeGuard();
    World world = Fakes.world("surge");

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      guard.on(move(world, 1D, 1D, 14D, 14D));
      guard.on(move(world, 2D, 2D, 3D, 9D));

      react.verify(() -> React.controller(IntegrationController.class), Mockito.never());
    }
  }

  @Test
  void crossChunkMovesReuseTheWorldVerdictWithinOneSecond() {
    FeatureIrisTerrainSurgeGuard guard = new FeatureIrisTerrainSurgeGuard();
    World world = Fakes.world("surge");
    IntegrationController integration = Mockito.mock(IntegrationController.class);
    RemoteSamplerBridge bridge = Mockito.mock(RemoteSamplerBridge.class);
    Mockito.when(integration.getRemoteSamplerBridge()).thenReturn(bridge);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(integration);

      guard.on(move(world, 1D, 1D, 17D, 1D));
      guard.on(move(world, 17D, 1D, 33D, 1D));
      guard.on(move(world, 33D, 1D, 49D, 1D));

      react.verify(() -> React.controller(IntegrationController.class), Mockito.times(1));
    }
  }

  private static PlayerMoveEvent move(World world, double fromX, double fromZ, double toX, double toZ) {
    PlayerMoveEvent event = Mockito.mock(PlayerMoveEvent.class);
    Player player = Fakes.player("walker", world);
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getFrom()).thenReturn(new Location(world, fromX, 64D, fromZ));
    Mockito.when(event.getTo()).thenReturn(new Location(world, toX, 64D, toZ));
    return event;
  }
}
