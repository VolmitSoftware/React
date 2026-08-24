package art.arcane.react;

import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

class ReactNearbyPlayerTest {
  private React previous;
  private Registry<IController> controllers;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws ReflectiveOperationException {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    controllers = Mockito.mock(Registry.class);
    Field registryField = React.class.getDeclaredField("controllerRegistry");
    registryField.setAccessible(true);
    registryField.set(plugin, controllers);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void foliaOffRegionQueryUsesConcurrentIndexBeforeOwnershipGuard() {
    Location location = location(0D, 64D, 0D);
    NearbyPlayerIndexController index = Mockito.mock(NearbyPlayerIndexController.class);
    Mockito.when(controllers.get(NearbyPlayerIndexController.class)).thenReturn(index);
    Mockito.when(index.hasNearbyPlayer(location, 32D)).thenReturn(true);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);

      Assertions.assertTrue(React.hasNearbyPlayer(location, 32D));

      scheduling.verifyNoInteractions();
      Mockito.verify(index).hasNearbyPlayer(location, 32D);
    }
  }

  @Test
  void registeredIndexIsAuthoritativeWhenItHasNoNearbyPlayer() {
    Location location = location(0D, 64D, 0D);
    World world = location.getWorld();
    NearbyPlayerIndexController index = Mockito.mock(NearbyPlayerIndexController.class);
    Mockito.when(controllers.get(NearbyPlayerIndexController.class)).thenReturn(index);
    Mockito.when(index.hasNearbyPlayer(location, 32D)).thenReturn(false);

    Assertions.assertFalse(React.hasNearbyPlayer(location, 32D));

    Mockito.verify(world, Mockito.never()).getPlayers();
  }

  @Test
  void paperFallbackReadsWorldPlayersWithoutARegionOwnershipCheck() {
    World world = world();
    Location location = new Location(world, 0D, 64D, 0D);
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 3D, 68D, 0D));
    Mockito.when(world.getPlayers()).thenReturn(List.of(player));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      Assertions.assertTrue(React.hasNearbyPlayer(location, 5D));

      scheduling.verify(() -> J.isOwnedByCurrentRegion(location), Mockito.never());
    }
  }

  @Test
  void foliaFallbackReadsWorldPlayersOnlyFromTheOwningRegion() {
    World world = world();
    Location location = new Location(world, 0D, 64D, 0D);
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 0D, 64D, 1D));
    Mockito.when(world.getPlayers()).thenReturn(List.of(player));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(location)).thenReturn(true);

      Assertions.assertTrue(React.hasNearbyPlayer(location, 2D));
    }
  }

  @Test
  void foliaOffRegionFallbackDoesNotTouchBukkitPlayerCollections() {
    World world = world();
    Location location = new Location(world, 0D, 64D, 0D);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(location)).thenReturn(false);

      Assertions.assertFalse(React.hasNearbyPlayer(location, 32D));

      Mockito.verify(world, Mockito.never()).getPlayers();
    }
  }

  private Location location(double x, double y, double z) {
    return new Location(world(), x, y, z);
  }

  private World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
    return world;
  }
}
