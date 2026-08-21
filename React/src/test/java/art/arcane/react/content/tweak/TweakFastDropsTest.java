package art.arcane.react.content.tweak;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TweakFastDropsTest {
  @Test
  public void blockDropsIgnoreAlreadyClaimedEvents() throws Exception {
    Method handler = TweakFastDrops.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    Assertions.assertNotNull(annotation);
    Assertions.assertTrue(annotation.ignoreCancelled());
  }

  @Test
  public void blockDropsClaimOnlyItemsTransferredByFastDrops() {
    TweakFastDrops tweak = new TweakFastDrops();
    BlockDropItemEvent event = Mockito.mock(BlockDropItemEvent.class);
    Block block = Mockito.mock(Block.class);
    BlockState blockState = Mockito.mock(BlockState.class);
    Player player = Mockito.mock(Player.class);
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    World world = Mockito.mock(World.class);
    Item entity = Mockito.mock(Item.class);
    Location location = Mockito.mock(Location.class);
    ItemStack original = Mockito.mock(ItemStack.class);
    ItemStack transfer = Mockito.mock(ItemStack.class);
    ItemStack overflow = Mockito.mock(ItemStack.class);
    List<Item> drops = new ArrayList<>(List.of(entity));
    HashMap<Integer, ItemStack> leftovers = new HashMap<>();
    leftovers.put(0, overflow);

    Mockito.when(event.getBlock()).thenReturn(block);
    Mockito.when(block.getState()).thenReturn(blockState);
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getItems()).thenReturn(drops);
    Mockito.when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(entity.getItemStack()).thenReturn(original);
    Mockito.when(entity.getLocation()).thenReturn(location);
    Mockito.when(original.clone()).thenReturn(transfer);
    Mockito.when(inventory.addItem(transfer)).thenAnswer(invocation -> {
      Assertions.assertTrue(drops.isEmpty());
      return leftovers;
    });

    tweak.on(event);

    Assertions.assertTrue(drops.isEmpty());
    Mockito.verify(inventory).addItem(transfer);
    Mockito.verify(inventory, Mockito.never()).addItem(original);
    Mockito.verify(world).dropItemNaturally(location, overflow);
    Mockito.verify(event, Mockito.never()).setCancelled(true);
  }

  @Test
  public void entityDropAndExperienceTogglesAreIndependent() throws Exception {
    TweakFastDrops tweak = new TweakFastDrops();
    setBoolean(tweak, "teleportBlockDrops", false);
    setBoolean(tweak, "teleportEntityDrops", true);
    setBoolean(tweak, "teleportEntityXP", false);

    EntityDeathEvent event = Mockito.mock(EntityDeathEvent.class);
    LivingEntity victim = Mockito.mock(LivingEntity.class);
    Player killer = Mockito.mock(Player.class);
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    ItemStack original = Mockito.mock(ItemStack.class);
    ItemStack transfer = Mockito.mock(ItemStack.class);
    ItemStack overflow = Mockito.mock(ItemStack.class);
    List<ItemStack> drops = new ArrayList<>(List.of(original));
    HashMap<Integer, ItemStack> leftovers = new HashMap<>();
    leftovers.put(0, overflow);

    Mockito.when(event.getEntity()).thenReturn(victim);
    Mockito.when(event.getDrops()).thenReturn(drops);
    Mockito.when(event.getDroppedExp()).thenReturn(7);
    Mockito.when(victim.getKiller()).thenReturn(killer);
    Mockito.when(victim.getLocation()).thenReturn(location);
    Mockito.when(killer.getLocation()).thenReturn(location);
    Mockito.when(killer.getGameMode()).thenReturn(GameMode.SURVIVAL);
    Mockito.when(killer.getInventory()).thenReturn(inventory);
    Mockito.when(killer.getWorld()).thenReturn(world);
    Mockito.when(original.clone()).thenReturn(transfer);
    Mockito.when(inventory.addItem(transfer)).thenReturn(leftovers);

    tweak.on(event);

    Assertions.assertTrue(drops.isEmpty());
    Mockito.verify(inventory).addItem(transfer);
    Mockito.verify(world).dropItemNaturally(location, overflow);
    Mockito.verify(event, Mockito.never()).setDroppedExp(Mockito.anyInt());
    Mockito.verify(killer, Mockito.never()).giveExp(Mockito.anyInt());
  }

  @Test
  public void blockExperienceDoesNotDependOnEntityExperienceToggle() throws Exception {
    TweakFastDrops tweak = new TweakFastDrops();
    setBoolean(tweak, "teleportBlockXP", true);
    setBoolean(tweak, "teleportEntityXP", false);

    BlockBreakEvent event = Mockito.mock(BlockBreakEvent.class);
    Player player = Mockito.mock(Player.class);
    Mockito.when(event.getPlayer()).thenReturn(player);
    Mockito.when(event.getExpToDrop()).thenReturn(7);
    Mockito.when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

    tweak.on(event);

    Mockito.verify(event).setExpToDrop(0);
    Mockito.verify(player).giveExp(7);
  }

  private static void setBoolean(TweakFastDrops tweak, String name, boolean value) throws Exception {
    Field field = TweakFastDrops.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(tweak, value);
  }
}
