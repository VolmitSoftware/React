package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.project.world.CustomMobChecker;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMobStackingHostileStateTest {
  private static MockedStatic<RegistryAccess> registryAccess;

  @BeforeAll
  static void installAttributeRegistry() throws ClassNotFoundException {
    RegistryAccess access = Mockito.mock(RegistryAccess.class);
    registryAccess = Mockito.mockStatic(RegistryAccess.class);
    registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
    Mockito.when(access.getRegistry(Mockito.any(RegistryKey.class)))
        .thenAnswer(invocation -> registry(invocation.getArgument(0)));
    Mockito.when(access.getRegistry(Mockito.any(Class.class)))
        .thenAnswer(invocation -> registry(invocation.getArgument(0)));
    Class.forName(Attribute.class.getName(), true, Attribute.class.getClassLoader());
  }

  private static Object registry(Object key) {
    Class<?> entryType = key instanceof Class<?> type ? type
        : key == RegistryKey.ATTRIBUTE ? Attribute.class : null;
    Map<Object, Object> entries = new HashMap<>();
    return Proxy.newProxyInstance(
        Registry.class.getClassLoader(), new Class<?>[]{Registry.class}, (proxy, method, arguments) -> {
          if ((method.getName().equals("get") || method.getName().equals("getOrThrow")) && entryType != null) {
            return entries.computeIfAbsent(arguments[0], registryName -> Proxy.newProxyInstance(
                entryType.getClassLoader(), new Class<?>[]{entryType},
                (entry, operation, values) -> switch (operation.getName()) {
                  case "equals" -> entry == values[0];
                  case "hashCode" -> System.identityHashCode(entry);
                  case "toString" -> registryName.toString();
                  default -> null;
                }));
          }
          return null;
        });
  }

  @AfterAll
  static void closeAttributeRegistry() {
    registryAccess.close();
  }

  @Test
  void ordinaryCreepersAndEmptyHandedEndermenCanMerge() {
    assertTrue(canMerge(creeper(), creeper(), EntityType.CREEPER));
    assertTrue(canMerge(Mockito.mock(Enderman.class), Mockito.mock(Enderman.class), EntityType.ENDERMAN));
  }

  @Test
  void creeperPowerFuseConfigurationAndExplosionRadiusMustMatch() {
    Creeper source = creeper();
    Creeper target = creeper();
    Mockito.when(source.isPowered()).thenReturn(true);
    assertFalse(canMerge(source, target, EntityType.CREEPER));
    Mockito.when(target.isPowered()).thenReturn(true);
    assertTrue(canMerge(source, target, EntityType.CREEPER));
    Mockito.when(source.getMaxFuseTicks()).thenReturn(60);
    assertFalse(canMerge(source, target, EntityType.CREEPER));
    Mockito.when(target.getMaxFuseTicks()).thenReturn(60);
    Mockito.when(source.getExplosionRadius()).thenReturn(5);
    assertFalse(canMerge(source, target, EntityType.CREEPER));
  }

  @Test
  void primedOrIgnitedCreepersCannotAbsorbOtherMobs() {
    Creeper source = creeper();
    Creeper target = creeper();
    Mockito.when(source.getFuseTicks()).thenReturn(8);
    assertFalse(canMerge(source, target, EntityType.CREEPER));
    Mockito.when(source.getFuseTicks()).thenReturn(0);
    Mockito.when(source.isIgnited()).thenReturn(true);
    assertFalse(canMerge(source, target, EntityType.CREEPER));
  }

  @Test
  void endermenCarryingDifferentBlocksCannotMerge() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman target = Mockito.mock(Enderman.class);
    BlockData grass = block("minecraft:grass_block[snowy=false]");
    Mockito.when(source.getCarriedBlock()).thenReturn(grass);
    assertFalse(canMerge(source, target, EntityType.ENDERMAN));
    Mockito.when(target.getCarriedBlock()).thenReturn(grass);
    assertTrue(canMerge(source, target, EntityType.ENDERMAN));
    BlockData dirt = block("minecraft:dirt");
    Mockito.when(target.getCarriedBlock()).thenReturn(dirt);
    assertFalse(canMerge(source, target, EntityType.ENDERMAN));
  }

  @Test
  void angryOrStaredAtEndermenRemainSeparate() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman target = Mockito.mock(Enderman.class);
    Mockito.when(source.getTarget()).thenReturn(Mockito.mock(LivingEntity.class));
    assertFalse(canMerge(source, target, EntityType.ENDERMAN));
    Mockito.when(source.getTarget()).thenReturn(null);
    Mockito.when(source.isScreaming()).thenReturn(true);
    assertFalse(canMerge(source, target, EntityType.ENDERMAN));
    Mockito.when(source.isScreaming()).thenReturn(false);
    Mockito.when(source.hasBeenStaredAt()).thenReturn(true);
    assertFalse(canMerge(source, target, EntityType.ENDERMAN));
  }

  @Test
  void distinctEquivalentCarriedBlockDataCanMerge() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman target = Mockito.mock(Enderman.class);
    Mockito.when(source.getCarriedBlock()).thenReturn(equivalentBlock("minecraft:grass_block[snowy=false]"));
    Mockito.when(target.getCarriedBlock()).thenReturn(equivalentBlock("minecraft:grass_block[snowy=false]"));
    assertTrue(canMerge(source, target, EntityType.ENDERMAN));
  }

  @Test
  void mountedOrPassengerCarryingHostilesCannotMerge() {
    Creeper creeper = creeper();
    Mockito.when(creeper.isInsideVehicle()).thenReturn(true);
    assertFalse(canMerge(creeper, creeper(), EntityType.CREEPER));
    Enderman enderman = Mockito.mock(Enderman.class);
    Mockito.when(enderman.getPassengers()).thenReturn(List.of(Mockito.mock(Entity.class)));
    assertFalse(canMerge(enderman, Mockito.mock(Enderman.class), EntityType.ENDERMAN));
  }

  @Test
  void creeperDeathReplacementPreservesPowerAndExplosionConfiguration() {
    Creeper source = creeper();
    Creeper target = creeper();
    Mockito.when(source.isPowered()).thenReturn(true);
    Mockito.when(source.getMaxFuseTicks()).thenReturn(60);
    Mockito.when(source.getExplosionRadius()).thenReturn(5);
    Mockito.when(source.getFuseTicks()).thenReturn(12);
    Mockito.when(source.isIgnited()).thenReturn(true);

    FeatureMobStacking feature = deathReplacement(source, target, EntityType.CREEPER);

    Mockito.verify(target).setPowered(true);
    Mockito.verify(target).setMaxFuseTicks(60);
    Mockito.verify(target).setExplosionRadius(5);
    Mockito.verify(target).setFuseTicks(0);
    Mockito.verify(target).setIgnited(false);
    Mockito.verify(feature).setStackCount(target, 2);
  }

  @Test
  void endermanDeathReplacementPreservesCarriedBlockWithoutReusingMutableData() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman target = Mockito.mock(Enderman.class);
    BlockData original = block("minecraft:grass_block[snowy=false]");
    BlockData copied = block("minecraft:grass_block[snowy=false]");
    Mockito.when(original.clone()).thenReturn(copied);
    Mockito.when(source.getCarriedBlock()).thenReturn(original);

    FeatureMobStacking feature = deathReplacement(source, target, EntityType.ENDERMAN);

    Mockito.verify(target).setCarriedBlock(copied);
    Mockito.verify(feature).setStackCount(target, 2);
  }

  @Test
  void poweringSplitsTheUnpoweredRemainderButCancelledOrUnchangedPowerDoesNothing() {
    Creeper source = creeper();
    Creeper replacement = creeper();
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.CREEPER);
    CreeperPowerEvent event = new CreeperPowerEvent(source, CreeperPowerEvent.PowerCause.SET_ON);
    event.setCancelled(true);
    feature.onCreeperPower(event);
    feature.onCreeperPower(new CreeperPowerEvent(source, CreeperPowerEvent.PowerCause.SET_OFF));
    Mockito.verify(source.getWorld(), Mockito.never()).spawnEntity(Mockito.any(Location.class), Mockito.any(EntityType.class));

    event.setCancelled(false);
    feature.onCreeperPower(event);

    Mockito.verify(feature).setStackCount(replacement, 2);
    Mockito.verify(feature).setStackCount(source, 1);
    Mockito.verify(replacement).setPowered(false);
    Mockito.verify(source, Mockito.never()).setPowered(Mockito.anyBoolean());
  }

  @Test
  void endermanPickupSplitsBeforeTheCarriedBlockChangesAndHonorsCancellation() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman replacement = Mockito.mock(Enderman.class);
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.ENDERMAN);
    EntityChangeBlockEvent event = new EntityChangeBlockEvent(source, Mockito.mock(Block.class), block("minecraft:air"));
    event.setCancelled(true);
    feature.onEndermanChangeBlock(event);
    Mockito.verify(source.getWorld(), Mockito.never()).spawnEntity(Mockito.any(Location.class), Mockito.any(EntityType.class));

    event.setCancelled(false);
    feature.onEndermanChangeBlock(event);

    Mockito.verify(feature).setStackCount(replacement, 2);
    Mockito.verify(feature).setStackCount(source, 1);
    Mockito.verify(replacement).setCarriedBlock(null);
    Mockito.verify(source, Mockito.never()).setCarriedBlock(Mockito.any());
  }

  @Test
  void endermanPlacementLeavesTheRemainderHoldingItsBlocks() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman replacement = Mockito.mock(Enderman.class);
    BlockData original = block("minecraft:dirt");
    BlockData copied = block("minecraft:dirt");
    Mockito.when(original.clone()).thenReturn(copied);
    Mockito.when(source.getCarriedBlock()).thenReturn(original);
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.ENDERMAN);

    feature.onEndermanChangeBlock(new EntityChangeBlockEvent(source, Mockito.mock(Block.class), original));

    Mockito.verify(feature).setStackCount(replacement, 2);
    Mockito.verify(feature).setStackCount(source, 1);
    Mockito.verify(replacement).setCarriedBlock(copied);
    Mockito.verify(source, Mockito.never()).setCarriedBlock(Mockito.any());
  }

  @Test
  void explosionRemovalPreservesRemainderWithAFreshFuseButOtherRemovalDoesNot() {
    Creeper source = creeper();
    Creeper replacement = creeper();
    Mockito.when(source.isPowered()).thenReturn(true);
    Mockito.when(source.isIgnited()).thenReturn(true);
    Mockito.when(source.getFuseTicks()).thenReturn(30);
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.CREEPER);
    feature.on(new EntityRemoveEvent(source, EntityRemoveEvent.Cause.DEATH));
    Mockito.verify(source.getWorld(), Mockito.never()).spawnEntity(Mockito.any(Location.class), Mockito.any(EntityType.class));

    feature.on(new EntityRemoveEvent(source, EntityRemoveEvent.Cause.EXPLODE));

    Mockito.verify(feature).setStackCount(replacement, 2);
    Mockito.verify(replacement).setPowered(true);
    Mockito.verify(replacement).setFuseTicks(0);
    Mockito.verify(replacement).setIgnited(false);
  }

  @Test
  void failedReplacementCancelsPoweringWithoutReducingTheOriginalStack() {
    Creeper source = creeper();
    Creeper replacement = creeper();
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.CREEPER);
    Mockito.when(replacement.isValid()).thenReturn(false);
    CreeperPowerEvent event = new CreeperPowerEvent(source, CreeperPowerEvent.PowerCause.SET_ON);

    feature.onCreeperPower(event);

    assertTrue(event.isCancelled());
    Mockito.verify(feature, Mockito.never()).setStackCount(source, 1);
  }

  @Test
  void failedReplacementCancelsBlockPickupWithoutReducingTheOriginalStack() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman replacement = Mockito.mock(Enderman.class);
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.ENDERMAN);
    Mockito.when(replacement.isValid()).thenReturn(false);
    EntityChangeBlockEvent event = new EntityChangeBlockEvent(source, Mockito.mock(Block.class), block("minecraft:air"));

    feature.onEndermanChangeBlock(event);

    assertTrue(event.isCancelled());
    Mockito.verify(feature, Mockito.never()).setStackCount(source, 1);
  }

  @Test
  void vetoedPoweredStateCopyCancelsTheChangeAndRemovesTheIncompleteReplacement() {
    Creeper source = creeper();
    Creeper replacement = creeper();
    Mockito.when(source.isPowered()).thenReturn(true);
    Mockito.doNothing().when(replacement).setPowered(Mockito.anyBoolean());
    FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.CREEPER);
    CreeperPowerEvent event = new CreeperPowerEvent(source, CreeperPowerEvent.PowerCause.SET_OFF);

    feature.onCreeperPower(event);

    assertTrue(event.isCancelled());
    Mockito.verify(replacement).remove();
    Mockito.verify(feature, Mockito.never()).setStackCount(source, 1);
    Mockito.verify(feature, Mockito.never()).setStackCount(replacement, 2);
  }

  @Test
  void manualCreeperSplitPreservesPowerFuseConfigurationAndRadius() {
    Creeper source = creeper();
    Creeper replacement = creeper();
    Mockito.when(source.isPowered()).thenReturn(true);
    Mockito.when(source.getMaxFuseTicks()).thenReturn(70);
    Mockito.when(source.getExplosionRadius()).thenReturn(6);
    Mockito.when(source.getFuseTicks()).thenReturn(12);
    Mockito.when(source.isIgnited()).thenReturn(true);

    FeatureMobStacking feature = manualSplit(source, replacement, EntityType.CREEPER);

    Mockito.verify(replacement).setPowered(true);
    Mockito.verify(replacement).setMaxFuseTicks(70);
    Mockito.verify(replacement).setExplosionRadius(6);
    Mockito.verify(replacement).setFuseTicks(0);
    Mockito.verify(replacement).setIgnited(false);
    Mockito.verify(source, Mockito.never()).setIgnited(Mockito.anyBoolean());
    Mockito.verify(feature).setStackCount(replacement, 1);
    Mockito.verify(feature).setStackCount(source, 2);
  }

  @Test
  void manualEndermanSplitPreservesTheCarriedBlocks() {
    Enderman source = Mockito.mock(Enderman.class);
    Enderman replacement = Mockito.mock(Enderman.class);
    BlockData original = block("minecraft:dirt");
    BlockData copied = block("minecraft:dirt");
    Mockito.when(original.clone()).thenReturn(copied);
    Mockito.when(source.getCarriedBlock()).thenReturn(original);

    FeatureMobStacking feature = manualSplit(source, replacement, EntityType.ENDERMAN);

    Mockito.verify(replacement).setCarriedBlock(copied);
    Mockito.verify(feature).setStackCount(replacement, 1);
    Mockito.verify(feature).setStackCount(source, 2);
  }

  @Test
  void cancelledManualReplacementSpawnLeavesTheOriginalCountUntouched() {
    React previousPlugin = React.instance;
    React.instance = Mockito.mock(React.class);
    try {
      Creeper source = creeper();
      Creeper replacement = creeper();
      FeatureMobStacking feature = prepareReplacement(source, replacement, EntityType.CREEPER);
      Mockito.when(replacement.isValid()).thenReturn(false);
      Location location = source.getLocation();
      Mockito.when(location.add(0D, 0.5D, 0D)).thenReturn(location);
      Player player = Mockito.mock(Player.class);
      Mockito.when(player.isSneaking()).thenReturn(true);

      feature.onPlayerInteractEntity(new PlayerInteractEntityEvent(player, source, EquipmentSlot.HAND));

      Mockito.verify(feature, Mockito.never()).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());
      Mockito.verify(replacement).remove();
    } finally {
      React.instance = previousPlugin;
    }
  }

  private static FeatureMobStacking manualSplit(LivingEntity source, LivingEntity replacement, EntityType type) {
    React previousPlugin = React.instance;
    React.instance = Mockito.mock(React.class);
    try {
      FeatureMobStacking feature = prepareReplacement(source, replacement, type);
      Location location = source.getLocation();
      Mockito.when(location.add(0D, 0.5D, 0D)).thenReturn(location);
      Mockito.doNothing().when(feature).updateEntityCustomName(Mockito.any(Entity.class));
      Player player = Mockito.mock(Player.class);
      Mockito.when(player.isSneaking()).thenReturn(true);
      PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, source, EquipmentSlot.HAND);
      event.setCancelled(true);
      feature.onPlayerInteractEntity(event);
      Mockito.verify(source.getWorld(), Mockito.never()).spawnEntity(Mockito.any(Location.class), Mockito.any(EntityType.class));
      event.setCancelled(false);
      feature.onPlayerInteractEntity(event);
      return feature;
    } finally {
      React.instance = previousPlugin;
    }
  }

  private static Creeper creeper() {
    Creeper creeper = Mockito.mock(Creeper.class);
    AtomicBoolean powered = new AtomicBoolean();
    Mockito.when(creeper.isPowered()).thenAnswer(invocation -> powered.get());
    Mockito.doAnswer(invocation -> {
      powered.set(invocation.getArgument(0));
      return null;
    }).when(creeper).setPowered(Mockito.anyBoolean());
    Mockito.when(creeper.getMaxFuseTicks()).thenReturn(30);
    Mockito.when(creeper.getExplosionRadius()).thenReturn(3);
    return creeper;
  }

  private static BlockData block(String state) {
    BlockData block = Mockito.mock(BlockData.class);
    Mockito.when(block.getAsString()).thenReturn(state);
    return block;
  }

  private static BlockData equivalentBlock(String state) {
    return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getAsString", "toString" -> state;
          case "equals" -> arguments[0] instanceof BlockData other && state.equals(other.getAsString());
          case "hashCode" -> state.hashCode();
          case "clone" -> equivalentBlock(state);
          default -> null;
        });
  }

  private static FeatureMobStacking deathReplacement(LivingEntity source, LivingEntity target, EntityType type) {
    FeatureMobStacking feature = prepareReplacement(source, target, type);
    EntityDeathEvent event = Mockito.mock(EntityDeathEvent.class);
    Mockito.when(event.getEntity()).thenReturn(source);
    feature.onEntityDeath(event);
    return feature;
  }

  private static FeatureMobStacking prepareReplacement(LivingEntity source, LivingEntity target, EntityType type) {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(source.getType()).thenReturn(type);
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(source.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(source.isValid()).thenReturn(true);
    Mockito.when(target.isValid()).thenReturn(true);
    Mockito.when(world.spawnEntity(location, type)).thenReturn(target);
    Mockito.doReturn(3).when(feature).getStackCount(source);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());
    return feature;
  }

  private static boolean canMerge(LivingEntity source, LivingEntity target, EntityType type) {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    AttributeInstance health = Mockito.mock(AttributeInstance.class);
    Mockito.when(health.getValue()).thenReturn(20D);
    Mockito.when(source.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
    Mockito.when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
    Mockito.when(source.getHealth()).thenReturn(20D);
    Mockito.when(target.getHealth()).thenReturn(20D);
    Mockito.when(source.getEntityId()).thenReturn(1);
    Mockito.when(target.getEntityId()).thenReturn(2);
    Mockito.when(source.getType()).thenReturn(type);
    Mockito.when(target.getType()).thenReturn(type);
    Mockito.doReturn(1).when(feature).getStackCount(Mockito.any());
    try (MockedStatic<CustomMobChecker> customMobs = Mockito.mockStatic(CustomMobChecker.class)) {
      return feature.canMerge(source, target);
    }
  }
}
