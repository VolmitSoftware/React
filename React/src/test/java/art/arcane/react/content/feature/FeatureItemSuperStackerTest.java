package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.integration.GlossDropNameIntegration;
import art.arcane.react.util.project.world.BundleUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

class FeatureItemSuperStackerTest {
  @Test
  void inventoryClickStillUnpacksAfterStackingIsDisabled() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();
    InventoryClickEvent event = Mockito.mock(InventoryClickEvent.class);
    Player player = Mockito.mock(Player.class);
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    ItemStack bundle = Mockito.mock(ItemStack.class);
    ItemStack content = Mockito.mock(ItemStack.class);
    Mockito.when(event.getWhoClicked()).thenReturn(player);
    Mockito.when(event.getCurrentItem()).thenReturn(bundle);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(new HashMap<>());

    try (MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      bundles.when(() -> BundleUtils.isFlagged(bundle)).thenReturn(true);
      bundles.when(() -> BundleUtils.explode(bundle)).thenReturn(List.of(content));

      feature.on(event);
    }

    InOrder unpackOrder = Mockito.inOrder(event, inventory);
    unpackOrder.verify(event).setCancelled(true);
    unpackOrder.verify(event).setCurrentItem(null);
    unpackOrder.verify(inventory).addItem(content);
    Assertions.assertInstanceOf(FeatureIntegrityListener.class, feature);
  }

  @Test
  void hopperPickupRemovesBundleAfterCompleteTransfer() {
    React previous = React.instance;
    React.instance = null;
    try {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
      InventoryPickupItemEvent event = Mockito.mock(InventoryPickupItemEvent.class);
      Inventory inventory = Mockito.mock(Inventory.class);
      Item item = Mockito.mock(Item.class);
      ItemStack bundle = Mockito.mock(ItemStack.class);
      ItemStack content = Mockito.mock(ItemStack.class);
      ItemStack transfer = Mockito.mock(ItemStack.class);
      Mockito.when(event.getItem()).thenReturn(item);
      Mockito.when(event.getInventory()).thenReturn(inventory);
      Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(item.getItemStack()).thenReturn(bundle);
      Mockito.when(content.clone()).thenReturn(transfer);
      Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(new HashMap<>());

      try (MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
        bundles.when(() -> BundleUtils.isBundle(bundle)).thenReturn(true);
        bundles.when(() -> BundleUtils.isFlagged(bundle)).thenReturn(true);
        bundles.when(() -> BundleUtils.explode(bundle)).thenReturn(List.of(content));

        feature.on(event);
      }

      Mockito.verify(event).setCancelled(true);
      Mockito.verify(inventory).addItem(transfer);
      Mockito.verify(glossDropNames).remove(item);
      Mockito.verify(item).remove();
      Mockito.verify(item, Mockito.never()).setItemStack(Mockito.any(ItemStack.class));
    } finally {
      React.instance = previous;
    }
  }

  @Test
  void hopperPickupPreservesOverflowInsideResidualBundle() {
    GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
    InventoryPickupItemEvent event = Mockito.mock(InventoryPickupItemEvent.class);
    Inventory inventory = Mockito.mock(Inventory.class);
    Item item = Mockito.mock(Item.class);
    ItemStack bundle = Mockito.mock(ItemStack.class);
    ItemStack content = Mockito.mock(ItemStack.class);
    ItemStack transfer = Mockito.mock(ItemStack.class);
    ItemStack leftover = Mockito.mock(ItemStack.class);
    ItemStack residualBundle = Mockito.mock(ItemStack.class);
    HashMap<Integer, ItemStack> overflow = new HashMap<>();
    overflow.put(0, leftover);
    Mockito.when(event.getItem()).thenReturn(item);
    Mockito.when(event.getInventory()).thenReturn(inventory);
    Mockito.when(item.getItemStack()).thenReturn(bundle);
    Mockito.when(content.clone()).thenReturn(transfer);
    Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(overflow);

    try (MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      bundles.when(() -> BundleUtils.isBundle(bundle)).thenReturn(true);
      bundles.when(() -> BundleUtils.isFlagged(bundle)).thenReturn(true);
      bundles.when(() -> BundleUtils.explode(bundle)).thenReturn(List.of(content));
      bundles.when(() -> BundleUtils.createBundle(List.of(leftover))).thenReturn(residualBundle);

      feature.on(event);
    }

    Mockito.verify(event).setCancelled(true);
    Mockito.verify(item).setItemStack(residualBundle);
    Mockito.verify(glossDropNames).refresh(
        item,
        "&eBundle &8(&e{total} items&8)",
        "&7- &f{count}x {type}",
        "&8+{remaining} more",
        3);
    Mockito.verify(item, Mockito.never()).remove();
  }

  @Test
  void mergedBundleRefreshesTheSurvivingGlossDropName() {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = Mockito.spy(new FeatureItemSuperStacker(glossDropNames));
      Item item = Mockito.mock(Item.class);
      Item target = Mockito.mock(Item.class);
      ItemStack itemStack = Mockito.mock(ItemStack.class);
      ItemStack targetStack = Mockito.mock(ItemStack.class);
      ItemStack bundle = Mockito.mock(ItemStack.class);
      World world = Mockito.mock(World.class);
      Location location = Mockito.mock(Location.class);
      Mockito.when(item.isDead()).thenReturn(false);
      Mockito.when(target.isDead()).thenReturn(false);
      Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(target.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(item.getWorld()).thenReturn(world);
      Mockito.when(item.getLocation()).thenReturn(location);
      Mockito.when(item.getItemStack()).thenReturn(itemStack);
      Mockito.when(target.getItemStack()).thenReturn(targetStack);
      Mockito.when(world.getNearbyEntities(location, 3, 3, 3)).thenReturn(List.of(target));
      Mockito.doNothing().when(feature).effectMerge(item, target);
      react.when(() -> React.controller(EntityController.class)).thenReturn(null);
      bundles.when(() -> BundleUtils.merge(itemStack, targetStack, 64)).thenReturn(bundle);

      feature.onActivate();
      feature.mergeWithNearbyItems(item);

      InOrder mergeOrder = Mockito.inOrder(target, glossDropNames);
      mergeOrder.verify(target).setItemStack(bundle);
      mergeOrder.verify(glossDropNames).refresh(
          target,
          "&eBundle &8(&e{total} items&8)",
          "&7- &f{count}x {type}",
          "&8+{remaining} more",
          3);
      Mockito.verify(glossDropNames).remove(item);
    }
  }

  @Test
  void hopperPickupDropsOverflowOnceWhenResidualBundleCannotBeCreated() {
    React previous = React.instance;
    React.instance = null;
    try {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
      InventoryPickupItemEvent event = Mockito.mock(InventoryPickupItemEvent.class);
      Inventory inventory = Mockito.mock(Inventory.class);
      Item item = Mockito.mock(Item.class);
      ItemStack bundle = Mockito.mock(ItemStack.class);
      ItemStack content = Mockito.mock(ItemStack.class);
      ItemStack transfer = Mockito.mock(ItemStack.class);
      ItemStack leftover = Mockito.mock(ItemStack.class);
      Location location = Mockito.mock(Location.class);
      World world = Mockito.mock(World.class);
      HashMap<Integer, ItemStack> overflow = new HashMap<>();
      overflow.put(0, leftover);
      Mockito.when(event.getItem()).thenReturn(item);
      Mockito.when(event.getInventory()).thenReturn(inventory);
      Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(item.getItemStack()).thenReturn(bundle);
      Mockito.when(item.getLocation()).thenReturn(location);
      Mockito.when(item.getWorld()).thenReturn(world);
      Mockito.when(content.clone()).thenReturn(transfer);
      Mockito.when(inventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(overflow);

      try (MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
        bundles.when(() -> BundleUtils.isBundle(bundle)).thenReturn(true);
        bundles.when(() -> BundleUtils.isFlagged(bundle)).thenReturn(true);
        bundles.when(() -> BundleUtils.explode(bundle)).thenReturn(List.of(content));
        bundles.when(() -> BundleUtils.createBundle(List.of(leftover))).thenReturn(null);

        feature.on(event);
      }

      Mockito.verify(event).setCancelled(true);
      Mockito.verify(glossDropNames).remove(item);
      Mockito.verify(item).remove();
      Mockito.verify(world).dropItemNaturally(location, leftover);
    } finally {
      React.instance = previous;
    }
  }

  @Test
  void entityTickSubscriptionIsRemovedOnDeactivate() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();
    EntityController controller = Mockito.mock(EntityController.class);
    Item item = Mockito.mock(Item.class);
    ArgumentCaptor<Consumer<Entity>> listener = ArgumentCaptor.forClass(Consumer.class);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      feature.onActivate();
      feature.onDeactivate();
    }

    Mockito.verify(controller).registerEntityTickListener(Mockito.eq(EntityType.ITEM), listener.capture());
    Mockito.verify(controller).unregisterEntityTickListener(Mockito.same(listener.getValue()));
    listener.getValue().accept(item);
    Mockito.verifyNoInteractions(item);
  }

  @Test
  void sampledBundlesRepublishGlossStylingAtMostOncePerCacheWindow() {
    GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
    EntityController controller = Mockito.mock(EntityController.class);
    Item item = Mockito.mock(Item.class);
    ItemStack bundle = Mockito.mock(ItemStack.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    UUID itemId = UUID.randomUUID();
    ArgumentCaptor<Consumer<Entity>> listener = ArgumentCaptor.forClass(Consumer.class);
    Mockito.when(item.isDead()).thenReturn(false);
    Mockito.when(item.getUniqueId()).thenReturn(itemId);
    Mockito.when(item.getItemStack()).thenReturn(bundle);
    Mockito.when(item.getWorld()).thenReturn(world);
    Mockito.when(item.getLocation()).thenReturn(location);
    Mockito.when(world.getNearbyEntities(location, 3, 3, 3)).thenReturn(List.of());
    Mockito.when(glossDropNames.refresh(Mockito.eq(item), Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.anyInt())).thenReturn(true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      bundles.when(() -> BundleUtils.isBundle(bundle)).thenReturn(true);
      bundles.when(() -> BundleUtils.isFlagged(bundle)).thenReturn(true);
      feature.onActivate();
      Mockito.verify(controller).registerEntityTickListener(Mockito.eq(EntityType.ITEM), listener.capture());

      listener.getValue().accept(item);
      listener.getValue().accept(item);
      feature.onDeactivate();
    }

    Mockito.verify(glossDropNames, Mockito.times(1)).refresh(
        item,
        "&eBundle &8(&e{total} items&8)",
        "&7- &f{count}x {type}",
        "&8+{remaining} more",
        3);
  }
}
