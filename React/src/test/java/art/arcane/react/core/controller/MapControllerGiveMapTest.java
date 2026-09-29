package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class MapControllerGiveMapTest {
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
  void heldPartialStackSurvivesWhenTheDashboardTakesTheHand() {
    FakeInventory inventory = new FakeInventory();
    inventory.slots[0] = FakeInventory.stack(Material.DIRT, 10);
    List<ItemStack> dropped = new ArrayList<>();
    World world = Mockito.mock(World.class);
    Mockito.when(world.dropItem(Mockito.any(Location.class), Mockito.any(ItemStack.class))).thenAnswer(invocation -> {
      dropped.add(invocation.getArgument(1));
      return null;
    });
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getInventory()).thenReturn(inventory.mock);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    ItemStack dashboard = FakeInventory.stack(Material.FILLED_MAP, 1);
    ReactRenderer renderer = Mockito.mock(ReactRenderer.class);
    MapController controller = Mockito.spy(new MapController());
    Mockito.doReturn(dashboard).when(controller).createMap(world, renderer);

    controller.giveMap(player, renderer);

    Assertions.assertSame(dashboard, inventory.slots[0]);
    Assertions.assertEquals(10, inventory.count(Material.DIRT) + FakeInventory.count(dropped, Material.DIRT));
  }

  @Test
  void heldStackOverflowIsDroppedInsteadOfDeleted() {
    FakeInventory inventory = new FakeInventory();
    inventory.slots[0] = FakeInventory.stack(Material.DIRT, 10);
    for (int slot = 1; slot < inventory.slots.length; slot++) {
      inventory.slots[slot] = FakeInventory.stack(Material.STONE, 64);
    }
    List<ItemStack> dropped = new ArrayList<>();
    World world = Mockito.mock(World.class);
    Mockito.when(world.dropItem(Mockito.any(Location.class), Mockito.any(ItemStack.class))).thenAnswer(invocation -> {
      dropped.add(invocation.getArgument(1));
      return null;
    });
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getInventory()).thenReturn(inventory.mock);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    ItemStack dashboard = FakeInventory.stack(Material.FILLED_MAP, 1);
    ReactRenderer renderer = Mockito.mock(ReactRenderer.class);
    MapController controller = Mockito.spy(new MapController());
    Mockito.doReturn(dashboard).when(controller).createMap(world, renderer);

    controller.giveMap(player, renderer);

    Assertions.assertSame(dashboard, inventory.slots[0]);
    Assertions.assertEquals(10, FakeInventory.count(dropped, Material.DIRT));
  }

  private static final class FakeInventory {
    private final ItemStack[] slots = new ItemStack[36];
    private final PlayerInventory mock = Mockito.mock(PlayerInventory.class);

    private FakeInventory() {
      Mockito.when(mock.getItemInMainHand()).thenAnswer(invocation ->
          slots[0] == null ? stack(Material.AIR, 0) : slots[0]
      );
      Mockito.doAnswer(invocation -> {
        ItemStack item = invocation.getArgument(0);
        slots[0] = item == null || item.getType() == Material.AIR ? null : item;
        return null;
      }).when(mock).setItemInMainHand(Mockito.nullable(ItemStack.class));
      Mockito.when(mock.addItem(Mockito.any(ItemStack[].class))).thenAnswer(invocation -> {
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        Object[] items = invocation.getArguments();
        for (int index = 0; index < items.length; index++) {
          ItemStack remaining = add((ItemStack) items[index]);
          if (remaining != null) {
            leftovers.put(index, remaining);
          }
        }
        return leftovers;
      });
    }

    private ItemStack add(ItemStack item) {
      int remaining = item.getAmount();
      for (ItemStack slot : slots) {
        if (remaining <= 0) {
          return null;
        }
        if (slot != null && slot.getType() == item.getType() && slot.getAmount() < slot.getMaxStackSize()) {
          int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
          slot.setAmount(slot.getAmount() + moved);
          remaining -= moved;
        }
      }
      for (int index = 0; index < slots.length && remaining > 0; index++) {
        if (slots[index] == null) {
          slots[index] = stack(item.getType(), remaining);
          remaining = 0;
        }
      }
      return remaining <= 0 ? null : stack(item.getType(), remaining);
    }

    private int count(Material material) {
      int total = 0;
      for (ItemStack slot : slots) {
        if (slot != null && slot.getType() == material) {
          total += slot.getAmount();
        }
      }
      return total;
    }

    private static int count(List<ItemStack> stacks, Material material) {
      int total = 0;
      for (ItemStack stack : stacks) {
        if (stack.getType() == material) {
          total += stack.getAmount();
        }
      }
      return total;
    }

    private static ItemStack stack(Material material, int amount) {
      ItemStack stack = Mockito.mock(ItemStack.class);
      AtomicInteger size = new AtomicInteger(amount);
      Mockito.when(stack.getType()).thenReturn(material);
      Mockito.when(stack.getAmount()).thenAnswer(invocation -> size.get());
      Mockito.when(stack.getMaxStackSize()).thenReturn(64);
      Mockito.doAnswer(invocation -> {
        size.set(invocation.getArgument(0));
        return null;
      }).when(stack).setAmount(Mockito.anyInt());
      Mockito.when(stack.clone()).thenAnswer(invocation -> stack(material, size.get()));
      return stack;
    }
  }
}
