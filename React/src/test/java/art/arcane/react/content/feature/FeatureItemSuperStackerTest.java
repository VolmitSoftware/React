package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.web.KnobSerializer;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.integration.GlossDropNameIntegration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.BundleUtils;
import com.google.common.util.concurrent.AtomicDouble;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Chunk;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
  void bundleRemovalIsCountedOnlyByTheEntityRemoveEvent() {
    React previous = React.instance;
    React.instance = Mockito.mock(React.class);
    try {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
      SamplerEntities sampler = Mockito.spy(new SamplerEntities());
      Item item = Mockito.mock(Item.class);
      ItemStack stack = Mockito.mock(ItemStack.class);
      Location location = Mockito.mock(Location.class);
      Chunk chunk = Mockito.mock(Chunk.class);
      World world = Mockito.mock(World.class);
      EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
      AtomicDouble chunkCount = new AtomicDouble();
      Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(item.getItemStack()).thenReturn(stack);
      Mockito.when(item.getLocation()).thenReturn(location);
      Mockito.when(location.getChunk()).thenReturn(chunk);
      Mockito.when(chunk.getWorld()).thenReturn(world);
      Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
      Mockito.when(spawn.getEntity()).thenReturn(item);
      Mockito.when(spawn.getLocation()).thenReturn(location);
      Mockito.doReturn(chunkCount).when(sampler).getChunkCounter(chunk);

      try (MockedStatic<React> react = Mockito.mockStatic(React.class);
           MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
           MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
        react.when(() -> React.feature(FeatureHopperItemIndex.class)).thenReturn(null);
        react.when(() -> React.sampler(SamplerEntities.ID)).thenReturn(sampler);
        bundles.when(() -> BundleUtils.isFlagged(stack)).thenReturn(false);
        sampler.start();
        sampler.on(spawn);

        feature.explode(item);
        sampler.on(new EntityRemoveEvent(item, EntityRemoveEvent.Cause.PLUGIN));
        sampler.stop();
      }

      Mockito.verify(item).remove();
      Assertions.assertEquals(0, sampler.getEntities().get());
      Assertions.assertEquals(0D, chunkCount.get());
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
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
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
      Mockito.when(item.isValid()).thenReturn(true);
      Mockito.when(target.isDead()).thenReturn(false);
      Mockito.when(target.isValid()).thenReturn(true);
      Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(target.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(item.getWorld()).thenReturn(world);
      Mockito.when(item.getLocation()).thenReturn(location);
      Mockito.when(target.getWorld()).thenReturn(world);
      Mockito.when(target.getLocation()).thenReturn(location);
      Mockito.when(location.getWorld()).thenReturn(world);
      Mockito.when(item.getItemStack()).thenReturn(itemStack);
      Mockito.when(target.getItemStack()).thenReturn(targetStack);
      Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
      Mockito.doNothing().when(feature).effectMerge(item, target);
      react.when(() -> React.controller(EntityController.class)).thenReturn(null);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bundles.when(() -> BundleUtils.merge(itemStack, targetStack, 64)).thenReturn(bundle);

      feature.onActivate();
      feature.on(itemSpawn(target));
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
    Mockito.when(item.isValid()).thenReturn(true);
    Mockito.when(item.getUniqueId()).thenReturn(itemId);
    Mockito.when(item.getItemStack()).thenReturn(bundle);
    Mockito.when(item.getWorld()).thenReturn(world);
    Mockito.when(item.getLocation()).thenReturn(location);
    Mockito.when(world.getNearbyEntities(location, 3, 3, 3)).thenReturn(List.of());
    Mockito.when(glossDropNames.refresh(Mockito.eq(item), Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.anyInt())).thenReturn(true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
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

  @Test
  void matchingCobblestoneStacksMergeImmediatelyWithoutCreatingABundle() {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = Mockito.spy(new FeatureItemSuperStacker(glossDropNames));
      Item source = Mockito.mock(Item.class);
      Item target = Mockito.mock(Item.class);
      ItemStack sourceStack = Mockito.mock(ItemStack.class);
      ItemStack targetStack = Mockito.mock(ItemStack.class);
      ItemStack updatedTarget = Mockito.mock(ItemStack.class);
      World world = Mockito.mock(World.class);
      Location location = Mockito.mock(Location.class);
      Mockito.when(source.isDead()).thenReturn(false);
      Mockito.when(source.isValid()).thenReturn(true);
      Mockito.when(target.isDead()).thenReturn(false);
      Mockito.when(target.isValid()).thenReturn(true);
      Mockito.when(source.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(target.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(source.getWorld()).thenReturn(world);
      Mockito.when(source.getLocation()).thenReturn(location);
      Mockito.when(target.getWorld()).thenReturn(world);
      Mockito.when(target.getLocation()).thenReturn(location);
      Mockito.when(location.getWorld()).thenReturn(world);
      Mockito.when(source.getItemStack()).thenReturn(sourceStack);
      Mockito.when(target.getItemStack()).thenReturn(targetStack);
      Mockito.when(sourceStack.isSimilar(targetStack)).thenReturn(true);
      Mockito.when(sourceStack.getAmount()).thenReturn(4);
      Mockito.when(targetStack.getAmount()).thenReturn(60);
      Mockito.when(targetStack.getMaxStackSize()).thenReturn(64);
      Mockito.when(targetStack.clone()).thenReturn(updatedTarget);
      Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
      Mockito.when(location.getWorld()).thenReturn(world);
      Mockito.doNothing().when(feature).effectMerge(source, target);
      react.when(() -> React.controller(EntityController.class)).thenReturn(null);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bundles.when(() -> BundleUtils.merge(sourceStack, targetStack, 64)).thenReturn(null);

      feature.onActivate();
      feature.on(itemSpawn(target));
      feature.mergeWithNearbyItems(source);

      Mockito.verify(updatedTarget).setAmount(64);
      Mockito.verify(target).setItemStack(updatedTarget);
      Mockito.verify(glossDropNames).remove(source);
      Mockito.verify(source).remove();
      Mockito.verify(glossDropNames).refresh(
          target,
          "&eBundle &8(&e{total} items&8)",
          "&7- &f{count}x {type}",
          "&8+{remaining} more",
          3);
    }
  }

  @Test
  void onePassCollapsesSeveralTargetsButHonorsItsConfiguredBudget() throws ReflectiveOperationException {
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
      FeatureItemSuperStacker feature = Mockito.spy(new FeatureItemSuperStacker(glossDropNames));
      Item source = item(true);
      Item first = item(true);
      Item second = item(true);
      Item third = item(true);
      ItemStack bundled = Mockito.mock(ItemStack.class);
      World world = Mockito.mock(World.class);
      Location location = Mockito.mock(Location.class);
      Mockito.when(source.getWorld()).thenReturn(world);
      Mockito.when(source.getLocation()).thenReturn(location);
      Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
      Mockito.when(location.getWorld()).thenReturn(world);
      for (Item target : List.of(first, second, third)) {
        Mockito.when(target.getWorld()).thenReturn(world);
        Mockito.when(target.getLocation()).thenReturn(location);
      }
      Mockito.doNothing().when(feature).effectMerge(Mockito.any(Item.class), Mockito.any(Item.class));
      Field budget = FeatureItemSuperStacker.class.getDeclaredField("maxMergesPerPass");
      budget.setAccessible(true);
      budget.setInt(feature, 2);
      react.when(() -> React.controller(EntityController.class)).thenReturn(null);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bundles.when(() -> BundleUtils.merge(Mockito.any(ItemStack.class), Mockito.any(ItemStack.class), Mockito.eq(64)))
          .thenReturn(bundled);

      feature.onActivate();
      feature.on(itemSpawn(first));
      feature.on(itemSpawn(second));
      feature.on(itemSpawn(third));
      feature.mergeWithNearbyItems(source);

      long modifiedTargets = List.of(first, second, third).stream()
          .filter(target -> Mockito.mockingDetails(target).getInvocations().stream()
              .anyMatch(invocation -> invocation.getMethod().getName().equals("setItemStack")))
          .count();
      Assertions.assertEquals(2, modifiedTargets);
      Mockito.verify(feature, Mockito.times(1)).effectMerge(Mockito.any(Item.class), Mockito.any(Item.class));
    }
  }

  @Test
  void foliaMergeSkipsForeignTargetsBeforeReadingAnyTargetState() {
    GlossDropNameIntegration glossDropNames = Mockito.mock(GlossDropNameIntegration.class);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(glossDropNames);
    Item source = Mockito.mock(Item.class);
    Item foreignTarget = Mockito.mock(Item.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(source.isDead()).thenReturn(false);
    Mockito.when(source.isValid()).thenReturn(true);
    Mockito.when(source.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(foreignTarget.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(foreignTarget.getLocation()).thenReturn(location);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(location.getWorld()).thenReturn(world);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(null);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenAnswer(
          invocation -> invocation.getArgument(0) == source
      );
      feature.onActivate();
      feature.on(itemSpawn(foreignTarget));
      Mockito.clearInvocations(foreignTarget);
      Mockito.when(foreignTarget.isDead()).thenThrow(new AssertionError("foreign isDead read"));
      Mockito.when(foreignTarget.isValid()).thenThrow(new AssertionError("foreign isValid read"));
      Mockito.when(foreignTarget.getUniqueId()).thenThrow(new AssertionError("foreign UUID read"));

      feature.mergeWithNearbyItems(source);

      Mockito.verify(foreignTarget, Mockito.never()).isDead();
      Mockito.verify(foreignTarget, Mockito.never()).isValid();
      Mockito.verify(foreignTarget, Mockito.never()).getUniqueId();
      Mockito.verify(foreignTarget, Mockito.never()).getItemStack();
      feature.onDeactivate();
    }
  }

  @Test
  void foliaMergeSoundTouchesRecipientsOnlyOnTheirEntityOwners() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();
    Player player = Mockito.mock(Player.class);
    NearbyPlayerIndexController controller = Mockito.mock(NearbyPlayerIndexController.class);
    React.Audiences audiences = Mockito.mock(React.Audiences.class);
    Audience audience = Mockito.mock(Audience.class);
    World world = Mockito.mock(World.class);
    World playerWorld = Mockito.mock(World.class);
    Location source = Mockito.mock(Location.class);
    UUID worldId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    AtomicReference<Runnable> playerTask = new AtomicReference<>();
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getX()).thenReturn(4.5D);
    Mockito.when(source.getY()).thenReturn(70D);
    Mockito.when(source.getZ()).thenReturn(-2.5D);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(playerWorld.getUID()).thenReturn(worldId);
    Mockito.when(controller.playerSnapshotsInColumn(world, 4.5D, -2.5D, 32D, 64)).thenReturn(List.of(
        new NearbyPlayerIndexController.PlayerViewSnapshot(
            playerId,
            "Player",
            worldId,
            4.5D,
            70D,
            -2.5D,
            0D,
            false,
            false
        )
    ));
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getWorld()).thenReturn(playerWorld);
    Mockito.when(audiences.player(player)).thenReturn(audience);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(controller);
      react.when(React::audiences).thenReturn(audiences);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.eq(player),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.isNull()))
          .thenAnswer(invocation -> {
            playerTask.set(invocation.getArgument(1));
            return true;
          });

      feature.playMergeSound(source);

      Mockito.verify(player, Mockito.never()).isOnline();
      Mockito.verify(player, Mockito.never()).getWorld();
      Assertions.assertNotNull(playerTask.get());
      playerTask.get().run();
      Mockito.verify(audience).playSound(Mockito.any(), Mockito.eq(4.5D), Mockito.eq(70D), Mockito.eq(-2.5D));
    }
  }

  @Test
  void densePileQueuesOneBoundedIndexedOwnerPassWithoutNearbyEnumeration() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();
    World world = Mockito.mock(World.class);
    Location location = new Location(world, 8D, 64D, 8D);
    List<Item> items = new ArrayList<>(1_000);
    EntityController entityController = Mockito.mock(EntityController.class);
    ArgumentCaptor<Consumer<Entity>> listener = ArgumentCaptor.forClass(Consumer.class);
    AtomicInteger stateReads = new AtomicInteger();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    for (int index = 0; index < 1_000; index++) {
      Item item = Mockito.mock(Item.class);
      Mockito.when(item.getUniqueId()).thenReturn(new UUID(0L, index + 1L));
      Mockito.when(item.getLocation()).thenReturn(location);
      Mockito.when(item.getWorld()).thenReturn(world);
      Mockito.when(item.isDead()).thenAnswer(invocation -> {
        stateReads.incrementAndGet();
        return false;
      });
      Mockito.when(item.isValid()).thenAnswer(invocation -> {
        stateReads.incrementAndGet();
        return true;
      });
      Mockito.when(item.getItemStack()).thenReturn(Mockito.mock(ItemStack.class));
      items.add(item);
    }

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<BundleUtils> bundles = Mockito.mockStatic(BundleUtils.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(entityController);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Item.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            ownerTask.set(invocation.getArgument(1));
            return true;
          });
      bundles.when(() -> BundleUtils.merge(
          Mockito.any(ItemStack.class),
          Mockito.any(ItemStack.class),
          Mockito.eq(64)
      )).thenReturn(null);

      feature.onActivate();
      Mockito.verify(entityController).registerEntityTickListener(Mockito.eq(EntityType.ITEM), listener.capture());
      for (Item item : items) {
        listener.getValue().accept(item);
      }
      stateReads.set(0);
      feature.onTick();

      Assertions.assertEquals(0, stateReads.get());
      Assertions.assertNotNull(ownerTask.get());
      scheduling.verify(() -> J.runEntity(
          Mockito.any(Item.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(1));
      Mockito.verify(world, Mockito.never()).getNearbyEntities(
          Mockito.any(Location.class),
          Mockito.anyDouble(),
          Mockito.anyDouble(),
          Mockito.anyDouble()
      );

      ownerTask.get().run();
      Assertions.assertTrue(stateReads.get() <= 64);
      feature.onDeactivate();
    }
  }

  @Test
  void mergeSoundFanoutIsCappedAcrossAThousandNearbyPlayers() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();
    NearbyPlayerIndexController controller = new NearbyPlayerIndexController();
    World world = Mockito.mock(World.class);
    Location source = new Location(world, 8D, 64D, 8D);
    Player player = Mockito.mock(Player.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(player);
      controller.start();
      for (int index = 0; index < 1_000; index++) {
        controller.injectSynthetic(new UUID(1L, index + 1L), source);
      }
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(controller);
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.isNull()
      )).thenReturn(true);

      feature.playMergeSound(source);

      Assertions.assertEquals(64, controller.playerSnapshotsInColumn(world, 8D, 8D, 32D, 64).size());
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.times(64));
      scheduling.verify(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.isNull()
      ), Mockito.times(64));
      Mockito.verify(world, Mockito.never()).getPlayers();
      controller.stop();
    }
  }

  @Test
  void clusterControlsArePublishedAsReactorWebKnobs() {
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker();

    Set<String> keys = new KnobSerializer().knobs(feature).stream()
        .map(knob -> knob.key)
        .collect(Collectors.toSet());

    Assertions.assertTrue(keys.contains("searchRadius"));
    Assertions.assertTrue(keys.contains("mergeMatchingStacks"));
    Assertions.assertTrue(keys.contains("maxMergesPerPass"));
    Assertions.assertTrue(keys.contains("spawnMergeDelayTicks"));
  }

  private static Item item(boolean valid) {
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.isDead()).thenReturn(!valid);
    Mockito.when(item.isValid()).thenReturn(valid);
    Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(item.getItemStack()).thenReturn(Mockito.mock(ItemStack.class));
    return item;
  }

  private static ItemSpawnEvent itemSpawn(Item item) {
    ItemSpawnEvent event = Mockito.mock(ItemSpawnEvent.class);
    Mockito.when(event.getEntity()).thenReturn(item);
    return event;
  }
}
