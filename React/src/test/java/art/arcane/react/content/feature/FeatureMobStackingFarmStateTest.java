package art.arcane.react.content.feature;

import art.arcane.react.util.project.world.CustomMobChecker;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.DyeColor;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Sheep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class FeatureMobStackingFarmStateTest {
  private static MockedStatic<RegistryAccess> registryAccess;

  @BeforeAll
  static void installRegistries() throws ClassNotFoundException {
    RegistryAccess access = Mockito.mock(RegistryAccess.class);
    registryAccess = Mockito.mockStatic(RegistryAccess.class);
    registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
    Mockito.when(access.getRegistry(Mockito.any(RegistryKey.class))).thenAnswer(invocation -> registry(invocation.getArgument(0)));
    Mockito.when(access.getRegistry(Mockito.any(Class.class))).thenAnswer(invocation -> registry(invocation.getArgument(0)));
    for (Class<?> type : new Class<?>[]{Attribute.class, Cow.Variant.class, Cow.SoundVariant.class,
        Chicken.Variant.class, Chicken.SoundVariant.class, Pig.Variant.class, Pig.SoundVariant.class}) {
      Class.forName(type.getName(), true, type.getClassLoader());
    }
  }

  @AfterAll
  static void closeRegistries() {
    registryAccess.close();
  }

  @Test
  void ordinaryFarmAnimalsPassTheirStateGateAndAreNoLongerExcluded() throws ReflectiveOperationException {
    assertSupported(Cow.class, EntityType.COW);
    assertSupported(Chicken.class, EntityType.CHICKEN);
    assertSupported(Pig.class, EntityType.PIG);
    assertSupported(Sheep.class, EntityType.SHEEP);
  }

  @Test
  void breedingAndAgeDifferencesKeepCowsSeparate() throws ReflectiveOperationException {
    Cow source = Mockito.mock(Cow.class);
    Cow target = Mockito.mock(Cow.class);
    Mockito.when(source.getAge()).thenReturn(-200);
    Assertions.assertFalse(matches(source, target));
    Mockito.when(target.getAge()).thenReturn(-200);
    Assertions.assertTrue(matches(source, target));
    Mockito.when(source.getAgeLock()).thenReturn(true);
    Assertions.assertFalse(matches(source, target));
    Mockito.when(target.getAgeLock()).thenReturn(true);
    Mockito.when(source.getLoveModeTicks()).thenReturn(400);
    Assertions.assertFalse(matches(source, target));
    Mockito.when(target.getLoveModeTicks()).thenReturn(400);
    Mockito.when(source.getBreedCause()).thenReturn(UUID.randomUUID());
    Assertions.assertFalse(matches(source, target));
  }

  @Test
  void shearedStateSaddlesAndJockeyStateMustMatch() throws ReflectiveOperationException {
    Sheep sheep = Mockito.mock(Sheep.class);
    Mockito.when(sheep.isSheared()).thenReturn(true);
    Assertions.assertFalse(matches(sheep, Mockito.mock(Sheep.class)));
    Pig pig = Mockito.mock(Pig.class);
    Mockito.when(pig.hasSaddle()).thenReturn(true);
    Assertions.assertFalse(matches(pig, Mockito.mock(Pig.class)));
    Chicken chicken = Mockito.mock(Chicken.class);
    Mockito.when(chicken.isChickenJockey()).thenReturn(true);
    Assertions.assertFalse(matches(chicken, Mockito.mock(Chicken.class)));
  }

  @Test
  void leashedAnimalsCannotDiscardTheirLeashInAMerge() throws ReflectiveOperationException {
    Cow source = Mockito.mock(Cow.class);
    Mockito.when(source.isLeashed()).thenReturn(true);
    Assertions.assertFalse(matches(source, Mockito.mock(Cow.class)));
  }

  @Test
  void replacementKeepsShearingColorAndExactBreedingState() throws ReflectiveOperationException {
    Sheep source = Mockito.mock(Sheep.class);
    Sheep target = Mockito.mock(Sheep.class);
    UUID breeder = UUID.randomUUID();
    Mockito.when(source.getColor()).thenReturn(DyeColor.BLUE);
    Mockito.when(source.isSheared()).thenReturn(true);
    Mockito.when(source.getAge()).thenReturn(-500);
    Mockito.when(source.getAgeLock()).thenReturn(true);
    Mockito.when(source.getLoveModeTicks()).thenReturn(120);
    Mockito.when(source.getBreedCause()).thenReturn(breeder);
    copyState(source, target);
    Mockito.verify(target).setColor(DyeColor.BLUE);
    Mockito.verify(target).setSheared(true);
    Mockito.verify(target).setAge(-500);
    Mockito.verify(target).setAgeLock(true);
    Mockito.verify(target).setLoveModeTicks(120);
    Mockito.verify(target).setBreedCause(breeder);
  }

  @Test
  void chickenReplacementKeepsRepresentativeEggTimerAndJockeyState() throws ReflectiveOperationException {
    Chicken source = Mockito.mock(Chicken.class);
    Chicken target = Mockito.mock(Chicken.class);
    Mockito.when(source.getEggLayTime()).thenReturn(812);
    Mockito.when(source.isChickenJockey()).thenReturn(true);
    Mockito.when(source.getVariant()).thenReturn(Chicken.Variant.WARM);
    Mockito.when(source.getSoundVariant()).thenReturn(Chicken.SoundVariant.PICKY);
    copyState(source, target);
    Mockito.verify(target).setEggLayTime(812);
    Mockito.verify(target).setIsChickenJockey(true);
    Mockito.verify(target).setVariant(Chicken.Variant.WARM);
    Mockito.verify(target).setSoundVariant(Chicken.SoundVariant.PICKY);
  }

  @Test
  void cowAndPigReplacementsKeepBothVariantsAndSaddleState() throws ReflectiveOperationException {
    Cow cow = Mockito.mock(Cow.class);
    Cow copiedCow = Mockito.mock(Cow.class);
    Mockito.when(cow.getVariant()).thenReturn(Cow.Variant.COLD);
    Mockito.when(cow.getSoundVariant()).thenReturn(Cow.SoundVariant.MOODY);
    copyState(cow, copiedCow);
    Mockito.verify(copiedCow).setVariant(Cow.Variant.COLD);
    Mockito.verify(copiedCow).setSoundVariant(Cow.SoundVariant.MOODY);
    Pig pig = Mockito.mock(Pig.class);
    Pig copiedPig = Mockito.mock(Pig.class);
    Mockito.when(pig.getVariant()).thenReturn(Pig.Variant.WARM);
    Mockito.when(pig.getSoundVariant()).thenReturn(Pig.SoundVariant.MINI);
    Mockito.when(pig.hasSaddle()).thenReturn(true);
    copyState(pig, copiedPig);
    Mockito.verify(copiedPig).setVariant(Pig.Variant.WARM);
    Mockito.verify(copiedPig).setSoundVariant(Pig.SoundVariant.MINI);
    Mockito.verify(copiedPig).setSaddle(true);
  }

  @Test
  void visualAndSoundVariantsMustMatchBeforePublicMergeEligibility() {
    Cow cow = Mockito.mock(Cow.class);
    Cow otherCow = Mockito.mock(Cow.class);
    Mockito.when(cow.getVariant()).thenReturn(Cow.Variant.COLD);
    Mockito.when(otherCow.getVariant()).thenReturn(Cow.Variant.WARM);
    Assertions.assertFalse(canMerge(cow, otherCow, EntityType.COW));
    Mockito.when(otherCow.getVariant()).thenReturn(Cow.Variant.COLD);
    Mockito.when(cow.getSoundVariant()).thenReturn(Cow.SoundVariant.CLASSIC);
    Mockito.when(otherCow.getSoundVariant()).thenReturn(Cow.SoundVariant.MOODY);
    Assertions.assertFalse(canMerge(cow, otherCow, EntityType.COW));

    Chicken chicken = Mockito.mock(Chicken.class);
    Chicken otherChicken = Mockito.mock(Chicken.class);
    Mockito.when(chicken.getVariant()).thenReturn(Chicken.Variant.COLD);
    Mockito.when(otherChicken.getVariant()).thenReturn(Chicken.Variant.WARM);
    Assertions.assertFalse(canMerge(chicken, otherChicken, EntityType.CHICKEN));
    Mockito.when(otherChicken.getVariant()).thenReturn(Chicken.Variant.COLD);
    Mockito.when(chicken.getSoundVariant()).thenReturn(Chicken.SoundVariant.CLASSIC);
    Mockito.when(otherChicken.getSoundVariant()).thenReturn(Chicken.SoundVariant.PICKY);
    Assertions.assertFalse(canMerge(chicken, otherChicken, EntityType.CHICKEN));

    Pig pig = Mockito.mock(Pig.class);
    Pig otherPig = Mockito.mock(Pig.class);
    Mockito.when(pig.getVariant()).thenReturn(Pig.Variant.COLD);
    Mockito.when(otherPig.getVariant()).thenReturn(Pig.Variant.WARM);
    Assertions.assertFalse(canMerge(pig, otherPig, EntityType.PIG));
    Mockito.when(otherPig.getVariant()).thenReturn(Pig.Variant.COLD);
    Mockito.when(pig.getSoundVariant()).thenReturn(Pig.SoundVariant.CLASSIC);
    Mockito.when(otherPig.getSoundVariant()).thenReturn(Pig.SoundVariant.MINI);
    Assertions.assertFalse(canMerge(pig, otherPig, EntityType.PIG));

    Sheep sheep = Mockito.mock(Sheep.class);
    Sheep otherSheep = Mockito.mock(Sheep.class);
    Mockito.when(sheep.getColor()).thenReturn(DyeColor.BLUE);
    Mockito.when(otherSheep.getColor()).thenReturn(DyeColor.RED);
    Assertions.assertFalse(canMerge(sheep, otherSheep, EntityType.SHEEP));
  }

  private static <T extends Animals> void assertSupported(Class<T> type, EntityType entityType)
      throws ReflectiveOperationException {
    T source = Mockito.mock(type);
    T target = Mockito.mock(type);
    if (source instanceof Cow left && target instanceof Cow right) {
      Mockito.when(left.getVariant()).thenReturn(Cow.Variant.COLD);
      Mockito.when(right.getVariant()).thenReturn(Cow.Variant.COLD);
      Mockito.when(left.getSoundVariant()).thenReturn(Cow.SoundVariant.CLASSIC);
      Mockito.when(right.getSoundVariant()).thenReturn(Cow.SoundVariant.CLASSIC);
    } else if (source instanceof Chicken left && target instanceof Chicken right) {
      Mockito.when(left.getVariant()).thenReturn(Chicken.Variant.COLD);
      Mockito.when(right.getVariant()).thenReturn(Chicken.Variant.COLD);
      Mockito.when(left.getSoundVariant()).thenReturn(Chicken.SoundVariant.CLASSIC);
      Mockito.when(right.getSoundVariant()).thenReturn(Chicken.SoundVariant.CLASSIC);
    } else if (source instanceof Pig left && target instanceof Pig right) {
      Mockito.when(left.getVariant()).thenReturn(Pig.Variant.COLD);
      Mockito.when(right.getVariant()).thenReturn(Pig.Variant.COLD);
      Mockito.when(left.getSoundVariant()).thenReturn(Pig.SoundVariant.CLASSIC);
      Mockito.when(right.getSoundVariant()).thenReturn(Pig.SoundVariant.CLASSIC);
    }
    Assertions.assertTrue(canMerge(source, target, entityType));
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

  private static Object registry(Object key) {
    Class<?> entryType = key instanceof Class<?> type ? type
        : key == RegistryKey.ATTRIBUTE ? Attribute.class
        : key == RegistryKey.COW_VARIANT ? Cow.Variant.class
        : key == RegistryKey.COW_SOUND_VARIANT ? Cow.SoundVariant.class
        : key == RegistryKey.CHICKEN_VARIANT ? Chicken.Variant.class
        : key == RegistryKey.CHICKEN_SOUND_VARIANT ? Chicken.SoundVariant.class
        : key == RegistryKey.PIG_VARIANT ? Pig.Variant.class
        : key == RegistryKey.PIG_SOUND_VARIANT ? Pig.SoundVariant.class : null;
    Map<Object, Object> entries = new HashMap<>();
    return Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
        (proxy, method, arguments) -> {
          if ((method.getName().equals("get") || method.getName().equals("getOrThrow")) && entryType != null) {
            return entries.computeIfAbsent(arguments[0], ignored -> Proxy.newProxyInstance(entryType.getClassLoader(),
                new Class<?>[]{entryType}, (entry, operation, values) -> switch (operation.getName()) {
                  case "equals" -> entry == values[0];
                  case "hashCode" -> System.identityHashCode(entry);
                  case "toString" -> arguments[0].toString();
                  default -> null;
                }));
          }
          return null;
        });
  }

  private static boolean matches(LivingEntity source, LivingEntity target) throws ReflectiveOperationException {
    Method method = FeatureMobStacking.class.getDeclaredMethod("hasMatchingFarmState", LivingEntity.class, LivingEntity.class);
    method.setAccessible(true);
    return (boolean) method.invoke(new FeatureMobStacking(), source, target);
  }

  private static void copyState(LivingEntity source, LivingEntity target) throws ReflectiveOperationException {
    Method method = FeatureMobStacking.class.getDeclaredMethod("copyState", LivingEntity.class, LivingEntity.class);
    method.setAccessible(true);
    method.invoke(new FeatureMobStacking(), source, target);
  }
}
