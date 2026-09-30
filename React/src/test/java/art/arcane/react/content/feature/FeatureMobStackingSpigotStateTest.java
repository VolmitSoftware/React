package art.arcane.react.content.feature;

import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.util.project.world.CustomMobChecker;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class FeatureMobStackingSpigotStateTest {
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

  @AfterAll
  static void closeAttributeRegistry() {
    registryAccess.close();
  }

  @Test
  void paperApiSelectsThePaperStateAccess() {
    Assertions.assertTrue(MobStateAccess.supportsPaperState());
    Assertions.assertInstanceOf(PaperMobStateAccess.class, MobStateAccess.detect());
  }

  @Test
  void spigotCreepersMergeWithoutReadingPaperIgnitionAndPrimedCreepersStaySeparate() {
    Creeper source = spigotCreeper();
    Creeper target = spigotCreeper();
    Assertions.assertTrue(canMerge(source, target, EntityType.CREEPER));
    Mockito.when(source.getFuseTicks()).thenReturn(6);
    Assertions.assertFalse(canMerge(source, target, EntityType.CREEPER));
  }

  @Test
  void spigotEndermenMergeWithoutReadingPaperAngerAndTargetingEndermenStaySeparate() {
    Enderman source = spigotEnderman();
    Enderman target = spigotEnderman();
    Assertions.assertTrue(canMerge(source, target, EntityType.ENDERMAN));
    Mockito.when(source.getTarget()).thenReturn(Mockito.mock(LivingEntity.class));
    Assertions.assertFalse(canMerge(source, target, EntityType.ENDERMAN));
  }

  @Test
  void spigotFarmAnimalsMergeWithoutReadingPaperSoundVariantsOrJockeyState() {
    Assertions.assertTrue(canMerge(spigotCow(), spigotCow(), EntityType.COW));
    Assertions.assertTrue(canMerge(spigotChicken(), spigotChicken(), EntityType.CHICKEN));
    Assertions.assertTrue(canMerge(spigotPig(), spigotPig(), EntityType.PIG));
    Pig saddled = spigotPig();
    Mockito.when(saddled.hasSaddle()).thenReturn(true);
    Assertions.assertFalse(canMerge(saddled, spigotPig(), EntityType.PIG));
  }

  @Test
  void equipmentIsDetectedFromItemTypeAndAmountWithoutPaperIsEmpty() {
    Creeper armed = spigotCreeper();
    EntityEquipment equipment = Mockito.mock(EntityEquipment.class);
    ItemStack held = spigotItem(Material.STONE, 1);
    ItemStack empty = spigotItem(Material.AIR, 0);
    Mockito.when(equipment.getItemInMainHand()).thenReturn(held);
    Mockito.when(equipment.getItemInOffHand()).thenReturn(empty);
    Mockito.when(equipment.getArmorContents()).thenReturn(new ItemStack[0]);
    Mockito.when(armed.getEquipment()).thenReturn(equipment);
    Assertions.assertFalse(canMerge(armed, spigotCreeper(), EntityType.CREEPER));

    Mockito.when(held.getAmount()).thenReturn(0);
    Assertions.assertTrue(canMerge(armed, spigotCreeper(), EntityType.CREEPER));
  }

  @Test
  void spigotReplacementsCopySharedStateWithoutPaperOnlySetters() throws ReflectiveOperationException {
    Creeper creeper = spigotCreeper();
    Creeper copiedCreeper = spigotCreeper();
    creeper.setPowered(true);
    copyState(creeper, copiedCreeper);
    Mockito.verify(copiedCreeper).setPowered(true);
    Mockito.verify(copiedCreeper).setFuseTicks(0);

    Enderman enderman = spigotEnderman();
    Enderman copiedEnderman = spigotEnderman();
    copyState(enderman, copiedEnderman);
    Mockito.verify(copiedEnderman).setCarriedBlock(null);

    Chicken chicken = spigotChicken();
    Chicken copiedChicken = spigotChicken();
    copyState(chicken, copiedChicken);
    Mockito.verify(copiedChicken).setVariant(Mockito.any());

    Cow cow = spigotCow();
    Cow copiedCow = spigotCow();
    copyState(cow, copiedCow);
    Mockito.verify(copiedCow).setVariant(Mockito.any());

    Pig pig = spigotPig();
    Pig copiedPig = spigotPig();
    Mockito.when(pig.hasSaddle()).thenReturn(true);
    copyState(pig, copiedPig);
    Mockito.verify(copiedPig).setSaddle(true);
  }

  @Test
  void spigotOwnerIdResolvesThroughTheOwnerWithoutPaperOwnerUniqueId() {
    Wolf wolf = Mockito.mock(Wolf.class);
    Mockito.when(wolf.getOwnerUniqueId()).thenThrow(paperOnly());
    AnimalTamer owner = Mockito.mock(AnimalTamer.class);
    UUID ownerId = UUID.randomUUID();
    Mockito.when(owner.getUniqueId()).thenReturn(ownerId);
    Mockito.when(wolf.getOwner()).thenReturn(owner);
    MobStateAccess access = new SpigotMobStateAccess();

    Assertions.assertEquals(ownerId, access.ownerId(wolf));
    Mockito.when(wolf.getOwner()).thenReturn(null);
    Assertions.assertNull(access.ownerId(wolf));
  }

  private static NoSuchMethodError paperOnly() {
    return new NoSuchMethodError("Paper-only API is absent on Spigot");
  }

  private static Creeper spigotCreeper() {
    Creeper creeper = Mockito.mock(Creeper.class);
    Mockito.when(creeper.getMaxFuseTicks()).thenReturn(30);
    Mockito.when(creeper.getExplosionRadius()).thenReturn(3);
    Mockito.when(creeper.isIgnited()).thenThrow(paperOnly());
    Mockito.doThrow(paperOnly()).when(creeper).setIgnited(Mockito.anyBoolean());
    AtomicBoolean powered = new AtomicBoolean();
    Mockito.when(creeper.isPowered()).thenAnswer(invocation -> powered.get());
    Mockito.doAnswer(invocation -> {
      powered.set(invocation.getArgument(0));
      return null;
    }).when(creeper).setPowered(Mockito.anyBoolean());
    return creeper;
  }

  private static Enderman spigotEnderman() {
    Enderman enderman = Mockito.mock(Enderman.class);
    Mockito.when(enderman.isScreaming()).thenThrow(paperOnly());
    Mockito.when(enderman.hasBeenStaredAt()).thenThrow(paperOnly());
    Mockito.doThrow(paperOnly()).when(enderman).setScreaming(Mockito.anyBoolean());
    Mockito.doThrow(paperOnly()).when(enderman).setHasBeenStaredAt(Mockito.anyBoolean());
    return enderman;
  }

  private static Cow spigotCow() {
    Cow cow = Mockito.mock(Cow.class);
    Mockito.when(cow.getSoundVariant()).thenThrow(paperOnly());
    Mockito.doThrow(paperOnly()).when(cow).setSoundVariant(Mockito.any());
    return cow;
  }

  private static Chicken spigotChicken() {
    Chicken chicken = Mockito.mock(Chicken.class);
    Mockito.when(chicken.getSoundVariant()).thenThrow(paperOnly());
    Mockito.when(chicken.isChickenJockey()).thenThrow(paperOnly());
    Mockito.when(chicken.getEggLayTime()).thenThrow(paperOnly());
    Mockito.doThrow(paperOnly()).when(chicken).setSoundVariant(Mockito.any());
    Mockito.doThrow(paperOnly()).when(chicken).setIsChickenJockey(Mockito.anyBoolean());
    Mockito.doThrow(paperOnly()).when(chicken).setEggLayTime(Mockito.anyInt());
    return chicken;
  }

  private static Pig spigotPig() {
    Pig pig = Mockito.mock(Pig.class);
    Mockito.when(pig.getSoundVariant()).thenThrow(paperOnly());
    Mockito.doThrow(paperOnly()).when(pig).setSoundVariant(Mockito.any());
    return pig;
  }

  private static ItemStack spigotItem(Material type, int amount) {
    ItemStack item = Mockito.mock(ItemStack.class);
    Mockito.when(item.getType()).thenReturn(type);
    Mockito.when(item.getAmount()).thenReturn(amount);
    Mockito.when(item.isEmpty()).thenThrow(paperOnly());
    return item;
  }

  private static FeatureMobStacking spigotFeature() {
    return new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class), new SpigotMobStateAccess());
  }

  private static boolean canMerge(LivingEntity source, LivingEntity target, EntityType type) {
    FeatureMobStacking feature = Mockito.spy(spigotFeature());
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

  private static void copyState(LivingEntity source, LivingEntity target) throws ReflectiveOperationException {
    Method method = FeatureMobStacking.class.getDeclaredMethod("copyState", LivingEntity.class, LivingEntity.class);
    method.setAccessible(true);
    method.invoke(spigotFeature(), source, target);
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
}
