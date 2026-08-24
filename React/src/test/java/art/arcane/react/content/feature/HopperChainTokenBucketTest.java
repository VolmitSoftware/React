package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.nms.TickDecision;
import art.arcane.react.util.common.scheduling.J;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HopperChainTokenBucketTest {

  @Test
  void syntheticMovesConsumeTheSameChunkBudgetAndInactiveBucketsAllowMoves() throws Exception {
    FeatureHopperTokenBucket bucket = new FeatureHopperTokenBucket();
    setBoolean(bucket, "bypassWhenNearbyPlayers", false);
    setDouble(bucket, "bucketCapacity", 1D);
    setDouble(bucket, "refillPerSecond", 0D);
    setDouble(bucket, "costPerMove", 1D);
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Location location = new Location(world, 15D, 64D, -1D);

    bucket.onActivate();

    assertTrue(bucket.tryConsume(location));
    assertFalse(bucket.tryConsume(location));
    assertFalse(bucket.tryConsume(null));

    bucket.onDeactivate();

    assertTrue(bucket.tryConsume(location));
  }

  @Test
  void nearbyPlayerBypassDoesNotSpendSyntheticBudget() throws Exception {
    FeatureHopperTokenBucket bucket = new FeatureHopperTokenBucket();
    setDouble(bucket, "bucketCapacity", 1D);
    setDouble(bucket, "refillPerSecond", 0D);
    setDouble(bucket, "costPerMove", 1D);
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Location location = new Location(world, 32D, 70D, 32D);
    bucket.onActivate();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.hasNearbyPlayer(location, 16D)).thenReturn(true);
      assertTrue(bucket.tryConsume(location));
      assertTrue(bucket.tryConsume(location));
    }

    setBoolean(bucket, "bypassWhenNearbyPlayers", false);
    assertTrue(bucket.tryConsume(location));
    assertFalse(bucket.tryConsume(location));
  }

  @Test
  void rejectedSyntheticMoveLeavesInventoriesUntouchedAndRestoresVanillaTicks() throws Exception {
    React previous = React.instance;
    React.instance = null;
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    setBoolean(feature, "featureActMode", true);
    NmsBridge bridge = Mockito.mock(NmsBridge.class);
    AtomicReference<HopperTickHook> installedHook = new AtomicReference<>();
    Mockito.when(bridge.installHopperTickHook(Mockito.any(HopperTickHook.class))).thenAnswer(invocation -> {
      installedHook.set(invocation.getArgument(0));
      return true;
    });

    try (MockedStatic<NmsBridges> bridges = Mockito.mockStatic(NmsBridges.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bridges.when(NmsBridges::get).thenReturn(bridge);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(true);
      feature.onActivate();
      setBoolean(feature, "engaged", true);

      FeatureHopperTokenBucket bucket = Mockito.mock(FeatureHopperTokenBucket.class);
      Mockito.when(bucket.isEnforcing()).thenReturn(true);
      Mockito.when(bucket.tryConsume(Mockito.any(Location.class))).thenReturn(false);
      setObject(feature, "hopperTokenBucket", bucket);

      World world = Mockito.mock(World.class);
      UUID worldId = UUID.randomUUID();
      Mockito.when(world.getUID()).thenReturn(worldId);
      Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
      Inventory headInventory = Mockito.mock(Inventory.class);
      Inventory recipientInventory = Mockito.mock(Inventory.class);
      ItemStack sourceStack = Mockito.mock(ItemStack.class);
      ItemStack transfer = Mockito.mock(ItemStack.class);
      Material sourceMaterial = Mockito.mock(Material.class);
      Mockito.when(sourceMaterial.isAir()).thenReturn(false);
      Mockito.when(sourceStack.getType()).thenReturn(sourceMaterial);
      Mockito.when(sourceStack.getAmount()).thenReturn(2);
      Mockito.when(sourceStack.clone()).thenReturn(transfer);
      Mockito.when(headInventory.isEmpty()).thenReturn(false);
      Mockito.when(headInventory.getStorageContents()).thenReturn(new ItemStack[]{sourceStack});
      bindInventory(world, 0, 64, 0, headInventory);
      bindInventory(world, 3, 64, 0, recipientInventory);

      Object chain = chain(new long[]{packPos(0, 64, 0), packPos(1, 64, 0), packPos(2, 64, 0)});
      setBoolean(chain, "fastPathEligible", true);
      putMiddleMapping(feature, worldId, packPos(1, 64, 0), chain);

      applyChainTransfer(feature, world, chain);

      Mockito.verify(bucket).tryConsume(Mockito.any(Location.class));
      Mockito.verify(recipientInventory, Mockito.never()).addItem(Mockito.any(ItemStack[].class));
      Mockito.verify(headInventory, Mockito.never()).setStorageContents(Mockito.any(ItemStack[].class));
      assertEquals(TickDecision.RUN_VANILLA, installedHook.get().decide(world, 1, 64, 0));

      setBoolean(feature, "featureBucketBypass", true);
      assertEquals(TickDecision.SKIP, installedHook.get().decide(world, 1, 64, 0));
      feature.onDeactivate();
    } finally {
      React.instance = previous;
    }
  }

  @Test
  void configuredBucketBypassMovesOneItemWithoutConsultingTheBucket() throws Exception {
    React previous = React.instance;
    React.instance = null;
    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    feature.onActivate();
    setBoolean(feature, "featureBucketBypass", true);
    FeatureHopperTokenBucket bucket = Mockito.mock(FeatureHopperTokenBucket.class);
    setObject(feature, "hopperTokenBucket", bucket);
    World world = Mockito.mock(World.class);
    Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Inventory headInventory = Mockito.mock(Inventory.class);
    Inventory recipientInventory = Mockito.mock(Inventory.class);
    ItemStack sourceStack = Mockito.mock(ItemStack.class);
    ItemStack transfer = Mockito.mock(ItemStack.class);
    Material sourceMaterial = Mockito.mock(Material.class);
    Mockito.when(sourceMaterial.isAir()).thenReturn(false);
    Mockito.when(sourceStack.getType()).thenReturn(sourceMaterial);
    Mockito.when(sourceStack.getAmount()).thenReturn(2);
    Mockito.when(sourceStack.clone()).thenReturn(transfer);
    Mockito.when(headInventory.isEmpty()).thenReturn(false);
    Mockito.when(headInventory.getStorageContents()).thenReturn(new ItemStack[]{sourceStack});
    Mockito.when(recipientInventory.addItem(Mockito.any(ItemStack[].class))).thenReturn(new HashMap<>());
    bindInventory(world, 0, 64, 0, headInventory);
    bindInventory(world, 3, 64, 0, recipientInventory);
    Object chain = chain(new long[]{packPos(0, 64, 0), packPos(1, 64, 0), packPos(2, 64, 0)});

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(true);
      applyChainTransfer(feature, world, chain);
    } finally {
      feature.onDeactivate();
      React.instance = previous;
    }

    Mockito.verifyNoInteractions(bucket);
    Mockito.verify(recipientInventory).addItem(Mockito.any(ItemStack[].class));
    Mockito.verify(sourceStack).setAmount(1);
    Mockito.verify(headInventory).setStorageContents(Mockito.any(ItemStack[].class));
    assertEquals(1L, feature.readAndResetSynthesizedTransfers());
  }

  private static void bindInventory(World world, int x, int y, int z, Inventory inventory) {
    Block block = Mockito.mock(Block.class);
    Hopper holder = Mockito.mock(Hopper.class);
    Mockito.when(block.getState()).thenReturn(holder);
    Mockito.when(holder.getInventory()).thenReturn(inventory);
    Mockito.when(world.getBlockAt(x, y, z)).thenReturn(block);
  }

  private static Object chain(long[] positions) throws Exception {
    Class<?> chainType = nestedClass("HopperChain");
    Constructor<?> constructor = chainType.getDeclaredConstructor(long[].class, BlockFace.class);
    constructor.setAccessible(true);
    return constructor.newInstance(positions, BlockFace.EAST);
  }

  private static void applyChainTransfer(
      FeatureHopperChainCoalescing feature,
      World world,
      Object chain
  ) throws Exception {
    Method method = FeatureHopperChainCoalescing.class.getDeclaredMethod(
        "applyChainTransfer",
        World.class,
        nestedClass("HopperChain"),
        int.class,
        int.class,
        int.class,
        int.class,
        int.class,
        int.class,
        int.class,
        int.class,
        int.class
    );
    method.setAccessible(true);
    method.invoke(feature, world, chain, 0, 64, 0, 2, 64, 0, 1, 0, 0);
  }

  private static void putMiddleMapping(
      FeatureHopperChainCoalescing feature,
      UUID worldId,
      long middlePosition,
      Object chain
  ) throws Exception {
    Map<?, ?> mappings = field(feature, "middleHopperToChain", Map.class);
    Long2ObjectOpenHashMap<Object> worldMapping = new Long2ObjectOpenHashMap<>();
    worldMapping.put(middlePosition, chain);
    Method put = Map.class.getMethod("put", Object.class, Object.class);
    put.invoke(mappings, worldId, worldMapping);
  }

  private static Class<?> nestedClass(String simpleName) {
    for (Class<?> type : FeatureHopperChainCoalescing.class.getDeclaredClasses()) {
      if (type.getSimpleName().equals(simpleName)) {
        return type;
      }
    }
    throw new IllegalStateException("Missing nested class " + simpleName);
  }

  private static long packPos(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
  }

  private static void setBoolean(Object target, String name, boolean value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(target, value);
  }

  private static void setDouble(Object target, String name, double value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setDouble(target, value);
  }

  private static void setObject(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }
}
