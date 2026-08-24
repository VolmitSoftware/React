package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.NMS;
import art.arcane.react.util.common.scheduling.J;
import com.google.common.util.concurrent.AtomicDouble;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class MobStackMergeTest {

  @Test
  void stackLimitNotExceededWhenSumUnderMax() {
    Assertions.assertFalse(FeatureMobStacking.exceedsStackLimit(5, 3, 10));
  }

  @Test
  void stackLimitExceededWhenSumOverMax() {
    Assertions.assertTrue(FeatureMobStacking.exceedsStackLimit(7, 4, 10));
  }

  @Test
  void stackLimitNotExceededWhenSumEqualsMax() {
    Assertions.assertFalse(FeatureMobStacking.exceedsStackLimit(5, 5, 10));
  }

  @Test
  void healthLimitWithinWhenCombinedUnderMax() {
    Assertions.assertTrue(FeatureMobStacking.withinHealthLimit(20.0, 20.0, 100.0));
  }

  @Test
  void healthLimitExceededWhenCombinedOverMax() {
    Assertions.assertFalse(FeatureMobStacking.withinHealthLimit(60.0, 50.0, 100.0));
  }

  @Test
  void healthLimitWithinWhenCombinedEqualsMax() {
    Assertions.assertTrue(FeatureMobStacking.withinHealthLimit(50.0, 50.0, 100.0));
  }

  @Test
  void theoreticalMaxStackCountFromHealthRatio() {
    Assertions.assertEquals(5, FeatureMobStacking.theoreticalMaxStackCount(100.0, 20.0, 10));
  }

  @Test
  void theoreticalMaxStackCountClampedByConfiguredMax() {
    Assertions.assertEquals(3, FeatureMobStacking.theoreticalMaxStackCount(100.0, 20.0, 3));
  }

  @Test
  void theoreticalMaxStackCountClampedByMaxWhenRatioHigher() {
    Assertions.assertEquals(10, FeatureMobStacking.theoreticalMaxStackCount(100.0, 7.0, 10));
  }

  @Test
  void theoreticalMaxStackCountZeroWhenEntityHealthExceedsBudget() {
    Assertions.assertEquals(0, FeatureMobStacking.theoreticalMaxStackCount(15.0, 20.0, 10));
  }

  @Property(tries = 200)
  void exceedsStackLimitMonotonicInSourceCount(@ForAll @IntRange(min = 0, max = 64) int intoCount,
                                               @ForAll @IntRange(min = 0, max = 64) int sourceCount,
                                               @ForAll @IntRange(min = 0, max = 64) int extra,
                                               @ForAll @IntRange(min = 0, max = 128) int maxStackSize) {
    boolean low = FeatureMobStacking.exceedsStackLimit(intoCount, sourceCount, maxStackSize);
    boolean high = FeatureMobStacking.exceedsStackLimit(intoCount, sourceCount + extra, maxStackSize);
    if (low) {
      Assertions.assertTrue(high);
    }
  }

  @Property(tries = 200)
  void withinHealthLimitMonotonicInCombinedHealth(@ForAll @DoubleRange(min = 0.0, max = 200.0) double sourceHealth,
                                                  @ForAll @DoubleRange(min = 0.0, max = 200.0) double intoHealth,
                                                  @ForAll @DoubleRange(min = 0.0, max = 200.0) double extra,
                                                  @ForAll @DoubleRange(min = 0.0, max = 400.0) double maxHealth) {
    boolean low = FeatureMobStacking.withinHealthLimit(sourceHealth, intoHealth, maxHealth);
    boolean high = FeatureMobStacking.withinHealthLimit(sourceHealth + extra, intoHealth, maxHealth);
    if (!low) {
      Assertions.assertFalse(high);
    }
  }

  @Property(tries = 200)
  void theoreticalMaxStackCountStaysWithinBounds(@ForAll @DoubleRange(min = 1.0, max = 2000.0) double maxHealth,
                                                 @ForAll @DoubleRange(min = 0.5, max = 200.0) double entityMaxHealth,
                                                 @ForAll @IntRange(min = 1, max = 64) int maxStackSize) {
    int result = FeatureMobStacking.theoreticalMaxStackCount(maxHealth, entityMaxHealth, maxStackSize);
    Assertions.assertTrue(result >= 0 && result <= maxStackSize);
  }

  @Property(tries = 200)
  void theoreticalMaxStackCountMonotonicInHealthBudget(@ForAll @DoubleRange(min = 1.0, max = 2000.0) double maxHealth,
                                                       @ForAll @DoubleRange(min = 0.0, max = 2000.0) double bump,
                                                       @ForAll @DoubleRange(min = 0.5, max = 200.0) double entityMaxHealth,
                                                       @ForAll @IntRange(min = 1, max = 64) int maxStackSize) {
    int low = FeatureMobStacking.theoreticalMaxStackCount(maxHealth, entityMaxHealth, maxStackSize);
    int high = FeatureMobStacking.theoreticalMaxStackCount(maxHealth + bump, entityMaxHealth, maxStackSize);
    Assertions.assertTrue(high >= low);
  }

  @Property(tries = 200)
  void chunkKeyPackRoundTrips(@ForAll @IntRange(min = -2_000_000, max = 2_000_000) int chunkX,
                              @ForAll @IntRange(min = -2_000_000, max = 2_000_000) int chunkZ) {
    long key = FeatureMobStacking.packChunkKey(chunkX, chunkZ);
    Assertions.assertEquals(chunkX, FeatureMobStacking.chunkKeyX(key));
    Assertions.assertEquals(chunkZ, FeatureMobStacking.chunkKeyZ(key));
  }

  @Test
  void chunkKeysDistinctForNegativeCoordinates() {
    Assertions.assertNotEquals(
        FeatureMobStacking.packChunkKey(-1, 0),
        FeatureMobStacking.packChunkKey(0, -1));
  }

  @Test
  void foliaStackPassReadsOnlyTheOwnedExactChunk() throws ReflectiveOperationException {
    FeatureMobStacking feature = new FeatureMobStacking();
    World world = Mockito.mock(World.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    LivingEntity owned = Mockito.mock(LivingEntity.class);
    LivingEntity foreign = Mockito.mock(LivingEntity.class);
    Field active = FeatureMobStacking.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    Mockito.when(world.isChunkLoaded(4, -3)).thenReturn(true);
    Mockito.when(world.getChunkAt(4, -3)).thenReturn(chunk);
    Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{owned, foreign});
    Mockito.when(owned.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(owned.isDead()).thenReturn(false);
    Mockito.clearInvocations(owned, foreign);
    Method stackChunk = FeatureMobStacking.class.getDeclaredMethod("stackChunk", World.class, int.class, int.class);
    stackChunk.setAccessible(true);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(owned)).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(foreign)).thenReturn(false);

      stackChunk.invoke(feature, world, 4, -3);
    }

    Mockito.verify(chunk).getEntities();
    Mockito.verify(world).getChunkAt(4, -3);
    Mockito.verify(world, Mockito.never()).getNearbyEntities(
        Mockito.any(BoundingBox.class),
        Mockito.any()
    );
    Mockito.verifyNoInteractions(foreign);
    Mockito.verify(owned, Mockito.atLeastOnce()).getType();
  }

  @Test
  void mergedMobRemovalIsCountedOnlyByTheEntityRemoveEvent() throws ReflectiveOperationException {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    SamplerEntities sampler = Mockito.spy(new SamplerEntities());
    Entity source = Mockito.mock(Entity.class);
    Entity target = Mockito.mock(Entity.class);
    Location location = Mockito.mock(Location.class);
    Chunk chunk = Mockito.mock(Chunk.class);
    EntitySpawnEvent spawn = Mockito.mock(EntitySpawnEvent.class);
    AtomicDouble chunkCount = new AtomicDouble();
    UUID sourceId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(chunk.getWorld()).thenReturn(world);
    Field active = FeatureMobStacking.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    Mockito.when(source.getUniqueId()).thenReturn(sourceId);
    Mockito.when(source.getEntityId()).thenReturn(1);
    Mockito.when(target.getEntityId()).thenReturn(2);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(location.getChunk()).thenReturn(chunk);
    Mockito.when(spawn.getEntity()).thenReturn(source);
    Mockito.when(spawn.getLocation()).thenReturn(location);
    Mockito.doReturn(chunkCount).when(sampler).getChunkCounter(chunk);
    Mockito.doReturn(true).when(feature).canMerge(source, target);
    Mockito.doReturn(1).when(feature).getStackCount(source);
    Mockito.doReturn(1).when(feature).getStackCount(target);
    Mockito.doNothing().when(feature).setStackCount(Mockito.eq(target), Mockito.eq(2));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<ReactProtection> protection = Mockito.mockStatic(ReactProtection.class);
         MockedStatic<NMS> nms = Mockito.mockStatic(NMS.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerEntities.ID)).thenReturn(sampler);
      sampler.start();
      sampler.on(spawn);

      Assertions.assertTrue(feature.merge(source, target));
      sampler.on(new EntityRemoveEvent(source, EntityRemoveEvent.Cause.PLUGIN));
      sampler.stop();
    }

    Mockito.verify(source).remove();
    Assertions.assertEquals(0, sampler.getEntities().get());
    Assertions.assertEquals(0D, chunkCount.get());
  }

  @Test
  void withinMergeRadiusAcceptsAxisAlignedBoundary() {
    Assertions.assertTrue(FeatureMobStacking.withinMergeRadius(0, 0, 0, 6, 0, 0, 6));
    Assertions.assertTrue(FeatureMobStacking.withinMergeRadius(0, 0, 0, 6, 6, 6, 6));
  }

  @Test
  void withinMergeRadiusRejectsBeyondRadiusOnAnyAxis() {
    Assertions.assertFalse(FeatureMobStacking.withinMergeRadius(0, 0, 0, 6.01, 0, 0, 6));
    Assertions.assertFalse(FeatureMobStacking.withinMergeRadius(0, 0, 0, 0, 7, 0, 6));
    Assertions.assertFalse(FeatureMobStacking.withinMergeRadius(0, 0, 0, 0, 0, -6.5, 6));
  }

  @Test
  void differentlySizedMagmaCubesDoNotMerge() {
    FeatureMobStacking feature = new FeatureMobStacking();
    MagmaCube source = Mockito.mock(MagmaCube.class);
    MagmaCube sameSize = Mockito.mock(MagmaCube.class);
    MagmaCube differentSize = Mockito.mock(MagmaCube.class);
    Mockito.when(source.getEntityId()).thenReturn(1);
    Mockito.when(differentSize.getEntityId()).thenReturn(2);
    Mockito.when(source.getType()).thenReturn(EntityType.MAGMA_CUBE);
    Mockito.when(differentSize.getType()).thenReturn(EntityType.MAGMA_CUBE);
    Mockito.when(source.getSize()).thenReturn(4);
    Mockito.when(sameSize.getSize()).thenReturn(4);
    Mockito.when(differentSize.getSize()).thenReturn(2);

    Assertions.assertFalse(feature.canMerge(source, differentSize));
    Assertions.assertTrue(FeatureMobStacking.sameCubeSize(source, sameSize));
  }

  @Test
  void slimeSizeComparisonRemainsUnchanged() {
    Slime source = Mockito.mock(Slime.class);
    Slime target = Mockito.mock(Slime.class);
    Mockito.when(source.getSize()).thenReturn(3);
    Mockito.when(target.getSize()).thenReturn(1);

    Assertions.assertFalse(FeatureMobStacking.sameCubeSize(source, target));
  }

  @Test
  void tamedWolfCannotMergeIntoUntamedWolf() {
    FeatureMobStacking feature = new FeatureMobStacking();
    Wolf source = Mockito.mock(Wolf.class);
    Wolf target = Mockito.mock(Wolf.class);
    stubMergeIdentity(source, target, EntityType.WOLF);
    Mockito.when(source.isTamed()).thenReturn(true);

    Assertions.assertFalse(feature.canMerge(source, target));
  }

  @Test
  void untamedCatCannotMergeIntoTamedCat() {
    FeatureMobStacking feature = new FeatureMobStacking();
    Cat source = Mockito.mock(Cat.class);
    Cat target = Mockito.mock(Cat.class);
    stubMergeIdentity(source, target, EntityType.CAT);
    Mockito.when(target.isTamed()).thenReturn(true);

    Assertions.assertFalse(feature.canMerge(source, target));
  }

  @Test
  void untamedWolfIsNotProtectedByTameGuard() {
    Wolf wolf = Mockito.mock(Wolf.class);
    Mockito.when(wolf.isTamed()).thenReturn(false);

    Assertions.assertFalse(FeatureMobStacking.isTamedPet(wolf));
  }

  @Test
  void equippedMobsCannotMergeAndLoseTheirEquipment() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    LivingEntity source = Mockito.mock(LivingEntity.class);
    LivingEntity target = Mockito.mock(LivingEntity.class);
    EntityEquipment equipment = Mockito.mock(EntityEquipment.class);
    ItemStack sword = Mockito.mock(ItemStack.class);
    stubMergeIdentity(source, target, EntityType.ZOMBIE);
    Mockito.doReturn(true).when(feature).isStackableType(EntityType.ZOMBIE);
    Mockito.when(source.getEquipment()).thenReturn(equipment);
    Mockito.when(equipment.getItemInMainHand()).thenReturn(sword);
    Mockito.when(sword.getType()).thenReturn(Material.IRON_SWORD);
    Mockito.when(sword.isEmpty()).thenReturn(false);

    Assertions.assertFalse(feature.canMerge(source, target));
    Mockito.verify(source, Mockito.never()).remove();
    Mockito.verify(target, Mockito.never()).remove();
  }

  @Test
  void deathReplacementCopiesLegacyStackEquipmentEffectsAndHealth() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    LivingEntity source = Mockito.mock(LivingEntity.class);
    LivingEntity replacement = Mockito.mock(LivingEntity.class);
    EntityDeathEvent event = Mockito.mock(EntityDeathEvent.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    EntityEquipment sourceEquipment = Mockito.mock(EntityEquipment.class);
    EntityEquipment targetEquipment = Mockito.mock(EntityEquipment.class);
    ItemStack mainHand = Mockito.mock(ItemStack.class);
    ItemStack mainHandCopy = Mockito.mock(ItemStack.class);
    Mockito.when(event.getEntity()).thenReturn(source);
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(source.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(world.spawnEntity(location, EntityType.ZOMBIE)).thenReturn(replacement);
    Mockito.when(source.getEquipment()).thenReturn(sourceEquipment);
    Mockito.when(replacement.getEquipment()).thenReturn(targetEquipment);
    Mockito.when(sourceEquipment.getArmorContents()).thenReturn(new ItemStack[0]);
    Mockito.when(sourceEquipment.getItemInMainHand()).thenReturn(mainHand);
    Mockito.when(mainHand.clone()).thenReturn(mainHandCopy);
    Mockito.when(source.getActivePotionEffects()).thenReturn(List.of());
    Mockito.when(source.getMaxHealth()).thenReturn(40D);
    Mockito.doReturn(2).when(feature).getStackCount(source);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    feature.onEntityDeath(event);

    Mockito.verify(targetEquipment).setItemInMainHand(mainHandCopy);
    Mockito.verify(replacement).addPotionEffects(List.of());
    Mockito.verify(replacement).setMaxHealth(40D);
    Mockito.verify(replacement).setHealth(40D);
    Mockito.verify(feature).setStackCount(replacement, 1);
  }

  @Test
  void tamingStackedWolfSeparatesPetFromUntamedRemainder() {
    verifyTamingSplitsStack(Mockito.mock(Wolf.class), Mockito.mock(Wolf.class), EntityType.WOLF);
  }

  @Test
  void tamingStackedCatSeparatesPetFromUntamedRemainder() {
    verifyTamingSplitsStack(Mockito.mock(Cat.class), Mockito.mock(Cat.class), EntityType.CAT);
  }

  @Test
  void tamingSinglePetDoesNotSpawnRemainder() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    Wolf pet = Mockito.mock(Wolf.class);
    EntityTameEvent event = Mockito.mock(EntityTameEvent.class);
    Mockito.when(event.getEntity()).thenReturn(pet);
    Mockito.doReturn(1).when(feature).getStackCount(pet);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      feature.onEntityTame(event);
      scheduling.verifyNoInteractions();
    }
  }

  @Test
  void unsuccessfulTameDoesNotSplitStack() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    Wolf pet = Mockito.mock(Wolf.class);
    EntityTameEvent event = Mockito.mock(EntityTameEvent.class);
    AnimalTamer owner = Mockito.mock(AnimalTamer.class);
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    Mockito.when(event.getEntity()).thenReturn(pet);
    Mockito.when(event.getOwner()).thenReturn(owner);
    Mockito.when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(pet.isValid()).thenReturn(true);
    Mockito.when(pet.isTamed()).thenReturn(false);
    Mockito.doReturn(5).when(feature).getStackCount(pet);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(Mockito.eq(pet), Mockito.any(Runnable.class), Mockito.eq(1)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(1));
            return true;
          });
      feature.onEntityTame(event);
    }

    Assertions.assertNotNull(scheduled.get());
    scheduled.get().run();
    Mockito.verify(feature, Mockito.never()).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());
  }

  @Test
  void existingTamedStackSelfHealsDuringEntityTick() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    Wolf pet = Mockito.mock(Wolf.class);
    Wolf remainder = Mockito.mock(Wolf.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(pet.isTamed()).thenReturn(true);
    Mockito.when(pet.getWorld()).thenReturn(world);
    Mockito.when(pet.getLocation()).thenReturn(location);
    Mockito.when(pet.getType()).thenReturn(EntityType.WOLF);
    Mockito.when(world.spawnEntity(location, EntityType.WOLF)).thenReturn(remainder);
    Mockito.doReturn(3).when(feature).getStackCount(pet);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    feature.onTick(pet);

    Mockito.verify(feature).setStackCount(remainder, 2);
    Mockito.verify(feature).setStackCount(pet, 1);
  }

  @Test
  void failedRemainderSpawnPreservesTamedStackCount() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    Wolf pet = Mockito.mock(Wolf.class);
    Entity invalidRemainder = Mockito.mock(Entity.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(pet.isTamed()).thenReturn(true);
    Mockito.when(pet.getWorld()).thenReturn(world);
    Mockito.when(pet.getLocation()).thenReturn(location);
    Mockito.when(pet.getType()).thenReturn(EntityType.WOLF);
    Mockito.when(world.spawnEntity(location, EntityType.WOLF)).thenReturn(invalidRemainder);
    Mockito.doReturn(3).when(feature).getStackCount(pet);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    feature.onTick(pet);

    Mockito.verify(invalidRemainder).remove();
    Mockito.verify(feature, Mockito.never()).setStackCount(pet, 1);
  }

  @Property(tries = 200)
  void withinMergeRadiusIsSymmetric(@ForAll @DoubleRange(min = -64.0, max = 64.0) double ax,
                                    @ForAll @DoubleRange(min = -64.0, max = 64.0) double ay,
                                    @ForAll @DoubleRange(min = -64.0, max = 64.0) double az,
                                    @ForAll @DoubleRange(min = -64.0, max = 64.0) double bx,
                                    @ForAll @DoubleRange(min = -64.0, max = 64.0) double by,
                                    @ForAll @DoubleRange(min = -64.0, max = 64.0) double bz,
                                    @ForAll @DoubleRange(min = 0.0, max = 16.0) double radius) {
    Assertions.assertEquals(
        FeatureMobStacking.withinMergeRadius(ax, ay, az, bx, by, bz, radius),
        FeatureMobStacking.withinMergeRadius(bx, by, bz, ax, ay, az, radius));
  }

  private static void stubMergeIdentity(LivingEntity source, LivingEntity target, EntityType type) {
    Mockito.when(source.getEntityId()).thenReturn(1);
    Mockito.when(target.getEntityId()).thenReturn(2);
    Mockito.when(source.getType()).thenReturn(type);
    Mockito.when(target.getType()).thenReturn(type);
  }

  private static void verifyTamingSplitsStack(LivingEntity pet, LivingEntity remainder, EntityType type) {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    EntityTameEvent event = Mockito.mock(EntityTameEvent.class);
    AnimalTamer owner = Mockito.mock(AnimalTamer.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    UUID ownerId = UUID.randomUUID();
    AtomicReference<Runnable> scheduled = new AtomicReference<>();
    Tameable tameablePet = (Tameable) pet;
    Tameable tameableRemainder = (Tameable) remainder;

    Mockito.when(event.getEntity()).thenReturn(pet);
    Mockito.when(event.getOwner()).thenReturn(owner);
    Mockito.when(owner.getUniqueId()).thenReturn(ownerId);
    Mockito.when(pet.isValid()).thenReturn(true);
    Mockito.when(tameablePet.isTamed()).thenReturn(true);
    Mockito.when(tameablePet.getOwnerUniqueId()).thenReturn(ownerId);
    Mockito.when(pet.getWorld()).thenReturn(world);
    Mockito.when(pet.getLocation()).thenReturn(location);
    Mockito.when(pet.getType()).thenReturn(type);
    Mockito.when(world.spawnEntity(location, type)).thenReturn(remainder);
    Mockito.doReturn(5).when(feature).getStackCount(pet);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(Mockito.eq(pet), Mockito.any(Runnable.class), Mockito.eq(1)))
          .thenAnswer(invocation -> {
            scheduled.set(invocation.getArgument(1));
            return true;
          });
      feature.onEntityTame(event);
    }

    Assertions.assertNotNull(scheduled.get());
    scheduled.get().run();
    Mockito.verify(feature).setStackCount(remainder, 4);
    Mockito.verify(feature).setStackCount(pet, 1);
    Mockito.verify(tameableRemainder).setOwner(null);
    Mockito.verify(tameableRemainder).setTamed(false);
  }
}
