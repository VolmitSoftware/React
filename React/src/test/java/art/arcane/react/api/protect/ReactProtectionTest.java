package art.arcane.react.api.protect;

import art.arcane.react.api.protect.internal.ProtectionInstaller;
import art.arcane.react.api.protect.internal.ProtectionRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ReactProtectionTest {

  @AfterEach
  void uninstallEverything() {
    ProtectionInstaller.install(null);
  }

  private static Entity entity() {
    Map<NamespacedKey, Integer> integers = new HashMap<>();
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(container.isEmpty()).thenAnswer(i -> integers.isEmpty());
    Mockito.when(container.getKeys()).thenAnswer(i -> new LinkedHashSet<>(integers.keySet()));
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER)))
        .thenAnswer(i -> integers.get(i.<NamespacedKey>getArgument(0)));
    Mockito.when(container.has(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER)))
        .thenAnswer(i -> integers.containsKey(i.<NamespacedKey>getArgument(0)));
    Mockito.doAnswer(i -> {
      integers.put(i.getArgument(0), i.getArgument(2));
      return null;
    }).when(container).set(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER), Mockito.anyInt());
    Mockito.doAnswer(i -> {
      integers.remove(i.<NamespacedKey>getArgument(0));
      return null;
    }).when(container).remove(Mockito.any(NamespacedKey.class));

    World world = Mockito.mock(World.class);
    Mockito.when(world.getName()).thenReturn("world");
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getType()).thenReturn(EntityType.WOLF);
    Mockito.when(entity.getWorld()).thenReturn(world);
    Mockito.when(entity.getScoreboardTags()).thenReturn(Set.of());
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    return entity;
  }

  private static Plugin plugin(String name) {
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.when(plugin.getName()).thenReturn(name);
    return plugin;
  }

  @Test
  void everyCallIsSafeAndInertWhenReactIsAbsent() {
    ProtectionInstaller.install(null);
    Entity wolf = entity();
    Plugin owner = plugin("GuardianPets");

    Assertions.assertFalse(ReactProtection.available());
    Assertions.assertFalse(ReactProtection.protect(wolf, owner, ReactOperation.STACK));
    Assertions.assertFalse(ReactProtection.protect(wolf, owner, ReactOperations.all()));
    Assertions.assertFalse(ReactProtection.release(wolf, owner));
    Assertions.assertFalse(ReactProtection.invalidate(wolf));
    Assertions.assertFalse(ReactProtection.isProtected(wolf, ReactOperation.STACK));
    Assertions.assertEquals(ReactOperations.NONE, ReactProtection.operationsFor(wolf));
    Assertions.assertEquals("", ReactProtection.ownerOf(wolf));
    Mockito.verify(wolf, Mockito.never()).getPersistentDataContainer();
  }

  @Test
  void aNullOwnerIsRefusedEvenWhenBound() {
    ProtectionInstaller.install(new ProtectionRegistry("react"));
    Entity wolf = entity();

    Assertions.assertTrue(ReactProtection.available());
    Assertions.assertFalse(ReactProtection.protect(wolf, null, ReactOperation.STACK));
    Assertions.assertFalse(ReactProtection.release(wolf, null));
  }

  @Test
  void protectAndReleaseRoundTripThroughTheBoundRegistry() {
    ProtectionInstaller.install(new ProtectionRegistry("react"));
    Entity wolf = entity();
    Plugin owner = plugin("GuardianPets");

    Assertions.assertTrue(ReactProtection.protect(wolf, owner, ReactOperation.STACK, ReactOperation.SLEEP));
    Assertions.assertTrue(ReactProtection.isProtected(wolf, ReactOperation.STACK));
    Assertions.assertTrue(ReactProtection.isProtected(wolf, ReactOperation.SLEEP));
    Assertions.assertFalse(ReactProtection.isProtected(wolf, ReactOperation.PURGE));
    Assertions.assertEquals("guardianpets", ReactProtection.ownerOf(wolf));

    Assertions.assertTrue(ReactProtection.release(wolf, owner));
    Assertions.assertEquals(ReactOperations.NONE, ReactProtection.operationsFor(wolf));
  }

  @Test
  void ruleDeclarationsReachTheFacadeWithoutAnyClaimBeingWritten() {
    ProtectionRegistry registry = new ProtectionRegistry("react").withBuiltInRules(
        List.of(ReactProtectionRule.of("wolves", ReactOperation.TRIM).withEntityTypes(EntityType.WOLF)));
    ProtectionInstaller.install(registry);

    Assertions.assertTrue(ReactProtection.isProtected(entity(), ReactOperation.TRIM));
  }

  @Test
  void invalidateIsTheDocumentedEscapeHatchForStateWrittenOutsideTheApi() {
    ProtectionRegistry registry = new ProtectionRegistry("react").withBuiltInRules(
        List.of(ReactProtectionRule.of("wolves", ReactOperation.TRIM).withEntityTypes(EntityType.WOLF)));
    ProtectionInstaller.install(registry);
    Entity wolf = entity();

    Assertions.assertTrue(ReactProtection.isProtected(wolf, ReactOperation.TRIM));
    Assertions.assertEquals(1, registry.cache().size());
    Assertions.assertTrue(ReactProtection.invalidate(wolf));
    Assertions.assertEquals(0, registry.cache().size());
  }

  @Test
  void uninstallOnlyClearsTheMatchingRegistry() {
    ProtectionRegistry first = new ProtectionRegistry("react");
    ProtectionRegistry second = new ProtectionRegistry("react");

    ProtectionInstaller.install(first);
    ProtectionInstaller.uninstall(second);
    Assertions.assertSame(first, ProtectionInstaller.binding());

    ProtectionInstaller.uninstall(first);
    Assertions.assertNull(ProtectionInstaller.binding());
  }
}
