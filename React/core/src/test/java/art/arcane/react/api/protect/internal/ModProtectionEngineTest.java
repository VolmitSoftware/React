package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactModProtection;
import art.arcane.react.api.protect.ReactModProtectionProvider;
import art.arcane.react.api.protect.ReactModProtectionRule;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModProtectionEngineTest {
  private final List<String> log = new ArrayList<>();
  private final ModProtectionEngine engine = new ModProtectionEngine(new FakeEntities(), log::add);

  @AfterEach
  void uninstall() {
    engine.uninstall();
  }

  @Test
  void claimsPersistAsTagsAndMergePerOwner() {
    FakeEntity pet = new FakeEntity("minecraft:wolf", "world");

    assertTrue(engine.protect(pet, "PetMod", ReactOperations.of(ReactOperation.TRIM)));
    assertTrue(engine.protect(pet, "PetMod", ReactOperations.of(ReactOperation.STACK)));
    assertTrue(engine.protect(pet, "guards", ReactOperations.of(ReactOperation.PURGE)));

    assertEquals(2, pet.tags.size());
    assertTrue(engine.isProtected(pet, ReactOperation.TRIM));
    assertTrue(engine.isProtected(pet, ReactOperation.STACK));
    assertTrue(engine.isProtected(pet, ReactOperation.PURGE));
    assertFalse(engine.isProtected(pet, ReactOperation.SLEEP));
    assertEquals("guards,petmod", engine.ownerOf(pet));

    assertTrue(engine.release(pet, "PetMod"));
    assertFalse(engine.isProtected(pet, ReactOperation.TRIM));
    assertEquals("guards", engine.ownerOf(pet));
  }

  @Test
  void rulesMatchTypeWorldTagsAndMarkers() {
    ReactModProtectionProvider provider = () -> List.of(
        ReactModProtectionRule.of("villagers", ReactOperation.TRIM).withEntityTypes("villager").withWorlds("World"),
        ReactModProtectionRule.of("tagged", ReactOperation.PURGE).withScoreboardTags("keep"),
        ReactModProtectionRule.of("marked", ReactOperation.SLEEP).withMarkerKeys("petmod:pet"));
    engine.reconcile(List.of(new ModProtectionEngine.Provider(provider, "petmod")));

    FakeEntity villager = new FakeEntity("minecraft:villager", "world");
    FakeEntity elsewhere = new FakeEntity("minecraft:villager", "world_nether");
    FakeEntity tagged = new FakeEntity("minecraft:zombie", "world");
    tagged.tags.add("keep");
    tagged.tags.add("petmod:pet");

    assertTrue(engine.isProtected(villager, ReactOperation.TRIM));
    assertFalse(engine.isProtected(elsewhere, ReactOperation.TRIM));
    assertEquals(ReactOperations.of(ReactOperation.PURGE, ReactOperation.SLEEP), engine.operationsFor(tagged));
  }

  @Test
  void spawnCapRulesHonourSpawnReasons() {
    ReactModProtectionProvider provider = () -> List.of(
        ReactModProtectionRule.of("spawners", ReactOperation.SPAWN_CAP).withSpawnReasons("spawner"));
    engine.reconcile(List.of(new ModProtectionEngine.Provider(provider, "petmod")));

    assertTrue(engine.spawnProtected("minecraft:zombie", "world", "SPAWNER"));
    assertFalse(engine.spawnProtected("minecraft:zombie", "world", "NATURAL"));
  }

  @Test
  void failingProviderKeepsItsLastGoodRules() {
    boolean[] fail = {false};
    ReactModProtectionProvider provider = new ReactModProtectionProvider() {
      @Override
      public List<ReactModProtectionRule> rules() {
        if (fail[0]) {
          throw new IllegalStateException("boom");
        }
        return List.of(ReactModProtectionRule.of("all", ReactOperation.TRIM));
      }

      @Override
      public String providerId() {
        return "petmod-rules";
      }
    };
    List<ModProtectionEngine.Provider> providers = List.of(new ModProtectionEngine.Provider(provider, "petmod"));
    engine.reconcile(providers);
    fail[0] = true;

    assertEquals(1, engine.reconcile(providers));
    assertEquals(1, log.size());
    assertTrue(engine.isProtected(new FakeEntity("minecraft:cow", "world"), ReactOperation.TRIM));
  }

  @Test
  void facadeAnswersOnlyWhileInstalled() {
    FakeEntity pet = new FakeEntity("minecraft:cat", "world");

    assertFalse(ReactModProtection.available());
    assertFalse(ReactModProtection.protect(pet, "petmod", ReactOperation.TRIM));

    engine.install();

    assertTrue(ReactModProtection.protect(pet, "petmod", ReactOperation.TRIM));
    assertTrue(ReactModProtection.isProtected(pet, ReactOperation.TRIM));
    assertFalse(ReactModProtection.isProtected("not an entity", ReactOperation.TRIM));
  }

  private static final class FakeEntity {
    private final String type;
    private final String world;
    private final Set<String> tags = new LinkedHashSet<>();

    private FakeEntity(String type, String world) {
      this.type = type;
      this.world = world;
    }
  }

  private static final class FakeEntities implements ProtectionEntities {
    @Override
    public boolean isEntity(Object entity) {
      return entity instanceof FakeEntity;
    }

    @Override
    public String typeId(Object entity) {
      return ((FakeEntity) entity).type;
    }

    @Override
    public String worldName(Object entity) {
      return ((FakeEntity) entity).world;
    }

    @Override
    public Set<String> tags(Object entity) {
      return ((FakeEntity) entity).tags;
    }

    @Override
    public boolean addTag(Object entity, String tag) {
      return ((FakeEntity) entity).tags.add(tag);
    }

    @Override
    public boolean removeTag(Object entity, String tag) {
      return ((FakeEntity) entity).tags.remove(tag);
    }
  }
}
