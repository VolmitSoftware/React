package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.integration.GlossDropNameIntegration;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class FeatureItemSuperStackerAnchorTest {
  private MockedStatic<React> react;
  private MockedStatic<J> scheduling;
  private World world;
  private Location location;
  private FeatureItemSuperStacker feature;

  @BeforeEach
  void setUp() {
    react = Mockito.mockStatic(React.class);
    scheduling = Mockito.mockStatic(J.class);
    react.when(() -> React.controller(EntityController.class)).thenReturn(null);
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> J.runEntity(Mockito.any(Entity.class), Mockito.any(Runnable.class), Mockito.eq(0),
        Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    location = new Location(world, 8.5D, 64D, 8.5D);
    feature = Mockito.spy(new FeatureItemSuperStacker(Mockito.mock(GlossDropNameIntegration.class)));
    Mockito.doNothing().when(feature).effectMerge(Mockito.any(Item.class), Mockito.any(Item.class));
    feature.onActivate();
  }

  @AfterEach
  void tearDown() {
    feature.onDeactivate();
    scheduling.close();
    react.close();
  }

  @Test
  void fullBundleFirstInTheBucketDoesNotStarveNeighbourMerges() {
    BundleMeta fullContents = bundleMeta(64);
    Item full = item(1L, bundle(fullContents));
    AtomicInteger firstAmount = new AtomicInteger(10);
    AtomicInteger secondAmount = new AtomicInteger(10);
    ItemStack firstStack = cobble(firstAmount);
    ItemStack secondStack = cobble(secondAmount);
    Mockito.when(firstStack.isSimilar(secondStack)).thenReturn(true);
    Mockito.when(secondStack.isSimilar(firstStack)).thenReturn(true);
    Item first = item(2L, firstStack);
    Item second = item(3L, secondStack);

    feature.on(loaded(full, first, second));
    feature.onTick();

    Item removed = Mockito.mockingDetails(first).getInvocations().stream()
        .anyMatch(invocation -> invocation.getMethod().getName().equals("remove")) ? first : second;
    Item survivor = removed == first ? second : first;
    Mockito.verify(removed).remove();
    Mockito.verify(survivor).setItemStack(Mockito.argThat(stack -> stack.getAmount() == 20));
    Mockito.verify(full, Mockito.never()).remove();
    Mockito.verify(full, Mockito.never()).setItemStack(Mockito.any(ItemStack.class));
  }

  @Test
  void fullBundleNeighboursAreSkippedWithoutReopeningTheirContents() {
    BundleMeta fullContents = bundleMeta(64);
    Item full = item(1L, bundle(fullContents));
    Item loose = item(2L, cobble(new AtomicInteger(10)));

    feature.on(loaded(full, loose));
    for (int pass = 0; pass < 3; pass++) {
      feature.on(loaded(full, loose));
      feature.onTick();
    }

    Mockito.verify(fullContents, Mockito.atMost(1)).getItems();
    Mockito.verify(loose, Mockito.never()).remove();
    Mockito.verify(full, Mockito.never()).setItemStack(Mockito.any(ItemStack.class));
  }

  private EntitiesLoadEvent loaded(Item... items) {
    EntitiesLoadEvent event = Mockito.mock(EntitiesLoadEvent.class);
    List<Entity> entities = List.of(items);
    Mockito.when(event.getEntities()).thenReturn(entities);
    return event;
  }

  private Item item(long id, ItemStack stack) {
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.getUniqueId()).thenReturn(new UUID(0L, id));
    Mockito.when(item.getLocation()).thenReturn(location);
    Mockito.when(item.getWorld()).thenReturn(world);
    Mockito.when(item.isDead()).thenReturn(false);
    Mockito.when(item.isValid()).thenReturn(true);
    Mockito.when(item.getItemStack()).thenReturn(stack);
    return item;
  }

  private static ItemStack cobble(AtomicInteger amount) {
    ItemStack stack = plainCobble(amount);
    AtomicInteger copied = new AtomicInteger();
    ItemStack clone = plainCobble(copied);
    Mockito.when(stack.clone()).thenAnswer(invocation -> {
      copied.set(amount.get());
      return clone;
    });
    return stack;
  }

  private static ItemStack plainCobble(AtomicInteger amount) {
    ItemStack stack = Mockito.mock(ItemStack.class);
    Mockito.when(stack.getType()).thenReturn(Material.COBBLESTONE);
    Mockito.when(stack.getAmount()).thenAnswer(invocation -> amount.get());
    Mockito.when(stack.getMaxStackSize()).thenReturn(64);
    Mockito.doAnswer(invocation -> {
      amount.set(invocation.getArgument(0));
      return null;
    }).when(stack).setAmount(Mockito.anyInt());
    return stack;
  }

  private static BundleMeta bundleMeta(int contents) {
    BundleMeta meta = Mockito.mock(BundleMeta.class);
    ItemStack inside = cobble(new AtomicInteger(contents));
    Mockito.when(meta.getLore()).thenReturn(List.of("REACT SUPER STACK"));
    Mockito.when(meta.getItems()).thenReturn(List.of(inside));
    return meta;
  }

  private static ItemStack bundle(BundleMeta meta) {
    ItemStack stack = Mockito.mock(ItemStack.class);
    Mockito.when(stack.getType()).thenReturn(Material.BUNDLE);
    Mockito.when(stack.getAmount()).thenReturn(1);
    Mockito.when(stack.getMaxStackSize()).thenReturn(1);
    Mockito.when(stack.getItemMeta()).thenReturn(meta);
    return stack;
  }
}
