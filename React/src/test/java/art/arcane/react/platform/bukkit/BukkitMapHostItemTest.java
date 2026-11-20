package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.MapItemTags;
import art.arcane.react.platform.MapItemView;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class BukkitMapHostItemTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void writingAMapIdOntoAFreshMapItemBindsTheView() {
    MapMeta meta = unboundMeta();
    ItemStack item = mapItem(meta);
    MapView view = Mockito.mock(MapView.class);
    Mockito.when(view.getId()).thenReturn(7);
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getMap(7)).thenReturn(view);
      new BukkitMapHost(null).writeMapItem(item, 7, new MapItemTags(true, "react-metrics", null), List.of());
    }

    Mockito.verify(meta).setMapView(view);
    Mockito.verify(item).setItemMeta(meta);
  }

  @Test
  void readingAMapItemWithoutAViewReportsNoMapId() {
    ItemStack item = mapItem(unboundMeta());

    MapItemView read = new BukkitMapHost(null).readMapItem(item);

    Assertions.assertEquals(-1, read.mapId());
  }

  /** Paper refuses {@link MapMeta#getMapView()} on an item that has no map yet. */
  private static MapMeta unboundMeta() {
    MapMeta meta = Mockito.mock(MapMeta.class);
    Mockito.when(meta.hasMapView()).thenReturn(false);
    Mockito.when(meta.getMapView()).thenThrow(new IllegalStateException("Item does not have map associated - check hasMapView() first!"));
    PersistentDataContainer data = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(data.getOrDefault(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(invocation -> invocation.getArgument(2));
    Mockito.when(meta.getPersistentDataContainer()).thenReturn(data);
    return meta;
  }

  private static ItemStack mapItem(MapMeta meta) {
    ItemStack item = Mockito.mock(ItemStack.class);
    Mockito.when(item.getType()).thenReturn(Material.FILLED_MAP);
    Mockito.when(item.getItemMeta()).thenReturn(meta);
    return item;
  }
}
