package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ProtectionRegistryTest {
  private static final NamespacedKey LEGACY = new NamespacedKey("volmit", "no-stack");
  private static final NamespacedKey PET = new NamespacedKey("example", "pet");

  private static final class FakeContainer {
    private final Map<NamespacedKey, Integer> integers = new HashMap<>();
    private final Set<NamespacedKey> flags = new LinkedHashSet<>();
    private final AtomicInteger keyScans = new AtomicInteger();

    private PersistentDataContainer mock() {
      PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
      Mockito.when(container.isEmpty()).thenAnswer(i -> integers.isEmpty() && flags.isEmpty());
      Mockito.when(container.getKeys()).thenAnswer(i -> {
        keyScans.incrementAndGet();
        Set<NamespacedKey> keys = new LinkedHashSet<>(flags);
        keys.addAll(integers.keySet());
        return keys;
      });
      Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER)))
          .thenAnswer(i -> integers.get(i.<NamespacedKey>getArgument(0)));
      Mockito.when(container.has(Mockito.any(NamespacedKey.class)))
          .thenAnswer(i -> {
            NamespacedKey key = i.getArgument(0);
            return flags.contains(key) || integers.containsKey(key);
          });
      Mockito.when(container.has(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER)))
          .thenAnswer(i -> integers.containsKey(i.<NamespacedKey>getArgument(0)));
      Mockito.doAnswer(i -> {
        integers.put(i.getArgument(0), i.getArgument(2));
        return null;
      }).when(container).set(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.INTEGER), Mockito.anyInt());
      Mockito.doAnswer(i -> {
        NamespacedKey key = i.getArgument(0);
        integers.remove(key);
        flags.remove(key);
        return null;
      }).when(container).remove(Mockito.any(NamespacedKey.class));
      return container;
    }
  }

  private static final class FakeEntity {
    private final FakeContainer data = new FakeContainer();
    private final Entity entity;
    private final AtomicInteger containerFetches = new AtomicInteger();
    private final AtomicInteger worldFetches = new AtomicInteger();

    private FakeEntity(EntityType type, String worldName, Set<String> scoreboardTags) {
      PersistentDataContainer container = data.mock();
      World world = Mockito.mock(World.class);
      Mockito.when(world.getName()).thenReturn(worldName);
      entity = Mockito.mock(Entity.class);
      Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
      Mockito.when(entity.getType()).thenReturn(type);
      Mockito.when(entity.getWorld()).thenAnswer(i -> {
        worldFetches.incrementAndGet();
        return world;
      });
      Mockito.when(entity.getScoreboardTags()).thenReturn(scoreboardTags);
      Mockito.when(entity.getPersistentDataContainer()).thenAnswer(i -> {
        containerFetches.incrementAndGet();
        return container;
      });
    }

    private static FakeEntity of(EntityType type) {
      return new FakeEntity(type, "world", Set.of());
    }
  }

  private static ProtectionRegistry registryWithLegacyRule() {
    return new ProtectionRegistry("react").withBuiltInRules(List.of(
        ReactProtectionRule.of("legacy-no-stack", ReactOperations.of(
                ReactOperation.STACK, ReactOperation.TRIM, ReactOperation.PURGE,
                ReactOperation.SLEEP, ReactOperation.DESPAWN))
            .withMarkerKeys(LEGACY)));
  }

  private static ReactProtectionProvider provider(String id, List<ReactProtectionRule> rules) {
    return new ReactProtectionProvider() {
      @Override
      public List<ReactProtectionRule> rules() {
        if (rules == null) {
          throw new IllegalStateException("hostile");
        }

        return rules;
      }

      @Override
      public String providerId() {
        return id;
      }
    };
  }

  @Test
  void unknownEntityWithNoRulesAndNoClaimsIsUnprotected() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity wolf = FakeEntity.of(EntityType.WOLF);
    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(wolf.entity));
  }

  @Test
  void nullEntityIsUnprotected() {
    Assertions.assertEquals(ReactOperations.NONE, new ProtectionRegistry("react").operationsFor(null));
    Assertions.assertFalse(new ProtectionRegistry("react").isProtected(null, ReactOperation.TRIM));
    Assertions.assertEquals("", new ProtectionRegistry("react").ownerOf(null));
  }

  @Test
  void legacyNoStackMarkerGrantsTheLegacyOperationSetIncludingSleep() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);

    int mask = registry.operationsFor(minion.entity);
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.PURGE));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.DESPAWN));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.SLEEP));
    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.SPAWN_CAP));
  }

  @Test
  void entityWithoutTheLegacyMarkerIsNotProtectedByIt() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity plain = FakeEntity.of(EntityType.ZOMBIE);
    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(plain.entity));
  }

  @Test
  void theClaimScanHappensOnceAndLaterReadsReuseIt() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);

    registry.operationsFor(minion.entity);
    Assertions.assertEquals(1, minion.data.keyScans.get());

    for (int i = 0; i < 50; i++) {
      registry.operationsFor(minion.entity);
    }

    Assertions.assertEquals(1, minion.data.keyScans.get());
    Assertions.assertEquals(1, registry.cache().size());
  }

  @Test
  void aCacheHitTouchesNoEntityStateWhenNoRuleUsesMarkers() {
    ProtectionRegistry registry = new ProtectionRegistry("react").withBuiltInRules(
        List.of(ReactProtectionRule.of("wolves", ReactOperation.TRIM).withEntityTypes(EntityType.WOLF)));
    FakeEntity wolf = FakeEntity.of(EntityType.WOLF);

    registry.operationsFor(wolf.entity);
    int fetchesAfterFirst = wolf.containerFetches.get();
    Assertions.assertTrue(fetchesAfterFirst > 0);

    for (int i = 0; i < 50; i++) {
      registry.operationsFor(wolf.entity);
    }

    Assertions.assertFalse(registry.ruleSet().isMarkerSensitive());
    Assertions.assertEquals(fetchesAfterFirst, wolf.containerFetches.get());
  }

  @Test
  void aMarkerWrittenOutsideTheApiAfterHydrationIsSeenOnTheNextRead() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);

    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(minion.entity));

    minion.data.flags.add(LEGACY);

    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(minion.entity), ReactOperation.STACK));
  }

  @Test
  void aMarkerRemovedOutsideTheApiStopsProtectingOnTheNextRead() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    registry.protect(minion.entity, "PluginA", ReactOperations.of(ReactOperation.SPAWN_CAP));
    minion.data.flags.add(LEGACY);

    int before = registry.operationsFor(minion.entity);
    Assertions.assertTrue(ReactOperations.covers(before, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(before, ReactOperation.SPAWN_CAP));

    minion.data.flags.remove(LEGACY);

    int after = registry.operationsFor(minion.entity);
    Assertions.assertFalse(ReactOperations.covers(after, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(after, ReactOperation.SPAWN_CAP));
  }

  @Test
  void invalidateDropsTheCachedMaskSoTagChangesAreSeen() {
    Set<String> tags = new LinkedHashSet<>();
    ProtectionRegistry registry = new ProtectionRegistry("react").withBuiltInRules(
        List.of(ReactProtectionRule.of("guarded", ReactOperation.TRIM).withScoreboardTags("guarded")));
    FakeEntity minion = new FakeEntity(EntityType.ZOMBIE, "world", tags);

    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(minion.entity));

    tags.add("guarded");
    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(minion.entity));

    Assertions.assertTrue(registry.invalidate(minion.entity));
    Assertions.assertEquals(0, registry.cache().size());
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(minion.entity), ReactOperation.TRIM));
    Assertions.assertFalse(registry.invalidate(null));
  }

  @Test
  void aHostileRuleListDoesNotStopLaterProvidersFromCompiling() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    List<ReactProtectionRule> hostile = new AbstractList<>() {
      @Override
      public ReactProtectionRule get(int index) {
        throw new IllegalStateException("get() is hostile");
      }

      @Override
      public int size() {
        throw new IllegalStateException("size() is hostile");
      }

      @Override
      public Iterator<ReactProtectionRule> iterator() {
        throw new IllegalStateException("iterator() is hostile");
      }
    };

    int compiled = registry.reconcile(List.of(
        new ProtectionRegistry.ProviderBinding(provider("hostile", hostile), "Hostile"),
        new ProtectionRegistry.ProviderBinding(
            provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP))), "GuardianPets")));

    Assertions.assertEquals(1, compiled);
    Assertions.assertEquals(List.of("pets/owned"), registry.ruleSet().ruleIds());
  }

  @Test
  void anEmptyContainerIsNeverKeyScanned() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity plain = FakeEntity.of(EntityType.ZOMBIE);

    registry.operationsFor(plain.entity);

    Assertions.assertEquals(0, plain.data.keyScans.get());
  }

  @Test
  void hydrationDoesNotAskForTheWorldWhenNoRuleFiltersOnWorlds() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);

    registry.operationsFor(minion.entity);

    Assertions.assertEquals(0, minion.worldFetches.get());
    Assertions.assertFalse(registry.ruleSet().isWorldSensitive());
  }

  @Test
  void hydrationAsksForTheWorldWhenARuleFiltersOnWorlds() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("arena", ReactOperation.TRIM).withWorlds("world"))),
        "GuardianPets")));
    FakeEntity minion = new FakeEntity(EntityType.ZOMBIE, "world", Set.of());

    Assertions.assertTrue(registry.ruleSet().isWorldSensitive());
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(minion.entity), ReactOperation.TRIM));
    Assertions.assertTrue(minion.worldFetches.get() > 0);
  }

  @Test
  void protectWritesAClaimThatSurvivesRehydration() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);

    Assertions.assertTrue(registry.protect(pet.entity, "GuardianPets",
        ReactOperations.of(ReactOperation.STACK, ReactOperation.SLEEP)));

    int mask = registry.operationsFor(pet.entity);
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.SLEEP));
    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertEquals("guardianpets", registry.ownerOf(pet.entity));
  }

  @Test
  void protectWithNoOperationsOrNoOwnerIsRefused() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);

    Assertions.assertFalse(registry.protect(pet.entity, "GuardianPets", ReactOperations.NONE));
    Assertions.assertFalse(registry.protect(pet.entity, "", ReactOperations.all()));
    Assertions.assertFalse(registry.protect(null, "GuardianPets", ReactOperations.all()));
  }

  @Test
  void protectFromASecondOwnerUnionsTheMask() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);

    registry.protect(pet.entity, "PluginA", ReactOperations.of(ReactOperation.STACK));
    registry.protect(pet.entity, "PluginB", ReactOperations.of(ReactOperation.TRIM));

    int mask = registry.operationsFor(pet.entity);
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertEquals("plugina,pluginb", registry.ownerOf(pet.entity));
  }

  @Test
  void releaseClearsOnlyTheCallersClaim() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);

    registry.protect(pet.entity, "PluginA", ReactOperations.of(ReactOperation.STACK));
    registry.protect(pet.entity, "PluginB", ReactOperations.of(ReactOperation.TRIM));

    Assertions.assertTrue(registry.release(pet.entity, "PluginA"));

    int mask = registry.operationsFor(pet.entity);
    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertEquals("pluginb", registry.ownerOf(pet.entity));
  }

  @Test
  void releasingAClaimYouNeverMadeChangesNothing() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);

    registry.protect(pet.entity, "PluginA", ReactOperations.of(ReactOperation.STACK));
    Assertions.assertFalse(registry.release(pet.entity, "PluginB"));
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(pet.entity), ReactOperation.STACK));
  }

  @Test
  void ruleDerivedProtectionCannotBeReleased() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);

    registry.release(minion.entity, "GuardianPets");

    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(minion.entity), ReactOperation.STACK));
  }

  @Test
  void entityStateThatIsNotAccessibleFromThisThreadIsNeverTouchedOrCached() {
    ProtectionRegistry registry = registryWithLegacyRule().withStateAccessibility(entity -> false);
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);

    Assertions.assertEquals(ReactOperations.NONE, registry.operationsFor(minion.entity));
    Assertions.assertEquals(0, minion.containerFetches.get());
    Assertions.assertEquals(0, registry.cache().size());
    Assertions.assertFalse(registry.protect(minion.entity, "Someone", ReactOperations.all()));
    Assertions.assertFalse(registry.release(minion.entity, "Someone"));
  }

  @Test
  void reconcileCompilesProviderRulesAndTheyApply() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP).withMarkerKeys(PET))),
        "GuardianPets")));

    FakeEntity pet = FakeEntity.of(EntityType.WOLF);
    pet.data.flags.add(PET);

    Assertions.assertEquals(1, registry.ruleSet().size());
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(pet.entity), ReactOperation.SLEEP));
  }

  @Test
  void aFaultingProviderKeepsItsLastKnownGoodRulesInForce() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    ReactProtectionProvider healthy = provider("pets",
        List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP).withMarkerKeys(PET)));
    ReactProtectionProvider hostile = provider("pets", null);

    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(healthy, "GuardianPets")));
    Assertions.assertEquals(1, registry.ruleSet().size());

    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(hostile, "GuardianPets")));

    Assertions.assertEquals(1, registry.ruleSet().size());
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);
    pet.data.flags.add(PET);
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(pet.entity), ReactOperation.SLEEP));
  }

  @Test
  void aProviderThatDisappearsLosesItsRules() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP))), "GuardianPets")));
    Assertions.assertEquals(1, registry.ruleSet().size());

    registry.reconcile(List.of());
    Assertions.assertEquals(0, registry.ruleSet().size());
  }

  @Test
  void anUnchangedReconcileDoesNotFlushTheHydratedMaskCache() {
    ProtectionRegistry registry = registryWithLegacyRule();
    List<ProtectionRegistry.ProviderBinding> bindings = List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP))), "GuardianPets"));

    registry.reconcile(bindings);
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);
    registry.operationsFor(minion.entity);
    int scans = minion.data.keyScans.get();

    registry.reconcile(bindings);

    Assertions.assertEquals(1, registry.cache().size());
    registry.operationsFor(minion.entity);
    Assertions.assertEquals(scans, minion.data.keyScans.get());
  }

  @Test
  void aChangedRuleSetFlushesTheHydratedMaskCache() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);
    registry.operationsFor(minion.entity);
    Assertions.assertEquals(1, registry.cache().size());

    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SPAWN_CAP))), "GuardianPets")));

    Assertions.assertEquals(0, registry.cache().size());
  }

  @Test
  void spawnProtectionUsesTypeWorldAndReasonOnly() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("summons", ReactOperation.SPAWN_CAP)
            .withEntityTypes(EntityType.WOLF)
            .withSpawnReasons(CreatureSpawnEvent.SpawnReason.CUSTOM))),
        "GuardianPets")));

    Assertions.assertTrue(registry.spawnProtected(
        EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.CUSTOM));
    Assertions.assertFalse(registry.spawnProtected(
        EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.NATURAL));
    Assertions.assertFalse(registry.spawnProtected(
        EntityType.ZOMBIE, "world", CreatureSpawnEvent.SpawnReason.CUSTOM));
  }

  @Test
  void spawnProtectionIsFalseWhenNobodyDeclaredSpawnCap() {
    ProtectionRegistry registry = registryWithLegacyRule();
    Assertions.assertFalse(registry.spawnProtected(
        EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.CUSTOM));
  }

  @Test
  void guardAllowsWhenNobodyIsListening() {
    ProtectionRegistry registry = new ProtectionRegistry("react");
    FakeEntity pet = FakeEntity.of(EntityType.WOLF);
    Assertions.assertTrue(registry.guardAllows(pet.entity, ReactOperation.TRIM));
    Assertions.assertTrue(registry.guardAllows(null, ReactOperation.TRIM));
    Assertions.assertTrue(registry.guardAllows(pet.entity, null));
  }

  @Test
  void sweepEvictsStaleMasksAndTheyRehydrate() {
    ProtectionRegistry registry = registryWithLegacyRule();
    FakeEntity minion = FakeEntity.of(EntityType.ZOMBIE);
    minion.data.flags.add(LEGACY);
    registry.operationsFor(minion.entity);

    Assertions.assertEquals(1, registry.sweep(System.currentTimeMillis() + 600_000L, 300_000L));
    Assertions.assertEquals(0, registry.cache().size());
    Assertions.assertTrue(ReactOperations.covers(
        registry.operationsFor(minion.entity), ReactOperation.STACK));
  }

  @Test
  void statsReportRulesClaimsAndFaults() {
    ProtectionRegistry registry = registryWithLegacyRule();
    List<String> log = new ArrayList<>();
    registry.withLog(log::add);
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(provider("bad", null), "Bad")));

    FakeEntity pet = FakeEntity.of(EntityType.WOLF);
    registry.protect(pet.entity, "PluginA", ReactOperations.of(ReactOperation.STACK));
    registry.operationsFor(pet.entity);

    ProtectionRegistry.Stats stats = registry.stats();
    Assertions.assertEquals(1, stats.compiledRules());
    Assertions.assertEquals(1, stats.claimWrites());
    Assertions.assertEquals(1, stats.protectedEntities());
    Assertions.assertEquals(1L, stats.providerFaults());
    Assertions.assertFalse(log.isEmpty());
  }

  @Test
  void clearDropsProviderRulesButKeepsBuiltIns() {
    ProtectionRegistry registry = registryWithLegacyRule();
    registry.reconcile(List.of(new ProtectionRegistry.ProviderBinding(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.SLEEP))), "GuardianPets")));
    Assertions.assertEquals(2, registry.ruleSet().size());

    registry.clear();

    Assertions.assertEquals(1, registry.ruleSet().size());
    Assertions.assertEquals(0, registry.cache().size());
  }
}
