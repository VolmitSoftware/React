package art.arcane.react.platform.bukkit;

import art.arcane.react.api.rendering.MapRendererRegistry;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.MapRuntimeConfig;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.api.rendering.RendererUnknown;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

class BukkitMapPipeBytesTest {
  private static final int MAP_ID = 7;

  @Test
  @SuppressWarnings("removal")
  void bukkitCanvasReceivesExactlyTheCoreSurface() throws Exception {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    MapView view = Mockito.mock(MapView.class);
    List<MapRenderer> renderers = new ArrayList<>();
    Mockito.when(view.getId()).thenReturn(MAP_ID);
    Mockito.when(view.getWorld()).thenReturn(world);
    Mockito.when(view.getRenderers()).thenAnswer(invocation -> new ArrayList<>(renderers));
    Mockito.doAnswer(invocation -> renderers.add(invocation.getArgument(0))).when(view).addRenderer(Mockito.any());
    Mockito.doAnswer(invocation -> renderers.remove(invocation.getArgument(0))).when(view).removeRenderer(Mockito.any());
    Player player = player(world);

    byte[] pixels = new byte[128 * 128];
    Arrays.fill(pixels, (byte) -1);
    MapCanvas canvas = Mockito.mock(MapCanvas.class);
    Mockito.when(canvas.getPixel(Mockito.anyInt(), Mockito.anyInt()))
        .thenAnswer(invocation -> pixels[(int) invocation.getArgument(1) * 128 + (int) invocation.getArgument(0)]);
    Mockito.doAnswer(invocation -> {
      pixels[(int) invocation.getArgument(1) * 128 + (int) invocation.getArgument(0)] = invocation.getArgument(2);
      return null;
    }).when(canvas).setPixel(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyByte());

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getMap(MAP_ID)).thenReturn(view);
      BukkitMapHost host = new BukkitMapHost(null);
      MapRuntime<ItemStack> runtime = new MapRuntime<ItemStack>(host, Mockito.mock(MapRuntimeConfig.class), new MapRendererRegistry());
      host.attach(runtime);
      runtime.start();
      runtime.bind(MAP_ID, worldId, new RendererUnknown());

      Assertions.assertTrue(host.isClaimed(MAP_ID));
      BukkitMapPipe pipe = (BukkitMapPipe) renderers.get(0);
      pipe.render(view, canvas, player);

      byte[] published = runtime.composer(MAP_ID).published();
      Assertions.assertFalse(contains(published, ReactRenderContext.UNTOUCHED));
      Assertions.assertArrayEquals(published, pixels);
      Assertions.assertEquals("84236d2c5216ff1e",
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels)).substring(0, 16));

      runtime.stop();
      Assertions.assertTrue(renderers.isEmpty(), "stopping the runtime detaches the pipe");
    }
  }

  private static boolean contains(byte[] values, byte needle) {
    for (byte value : values) {
      if (value == needle) {
        return true;
      }
    }
    return false;
  }

  private static Player player(World world) {
    Player player = Mockito.mock(Player.class);
    Location location = new Location(world, 0D, 64D, 0D);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getLocation()).thenReturn(location);
    Mockito.when(player.getEyeLocation()).thenReturn(new Location(world, 0D, 65.6D, 0D));
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    ItemStack air = Mockito.mock(ItemStack.class);
    Mockito.when(air.getType()).thenReturn(Material.AIR);
    Mockito.when(inventory.getItemInMainHand()).thenReturn(air);
    Mockito.when(inventory.getItemInOffHand()).thenReturn(air);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    return player;
  }
}
