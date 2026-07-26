package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class ProtectionMaskCacheTest {

  @Test
  void missingEntryReturnsTheFallback() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    Assertions.assertNull(cache.get(UUID.randomUUID()));
    Assertions.assertEquals(-1, cache.maskOr(UUID.randomUUID(), -1));
  }

  @Test
  void nullIdIsToleratedEverywhere() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    cache.put(null, ReactOperations.all(), "x", 0L);
    cache.invalidate(null);
    Assertions.assertNull(cache.get(null));
    Assertions.assertEquals(0, cache.size());
  }

  @Test
  void putThenGetReturnsTheStoredMaskAndOwners() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    UUID id = UUID.randomUUID();
    cache.put(id, ReactOperations.of(ReactOperation.STACK), "adapt", 1000L);

    ProtectionMaskCache.Entry entry = cache.get(id);
    Assertions.assertEquals(ReactOperations.of(ReactOperation.STACK), entry.mask());
    Assertions.assertEquals("adapt", entry.owners());
    Assertions.assertEquals(1000L, entry.hydratedAtMs());
  }

  @Test
  void storedMaskIsSanitizedToKnownOperations() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    UUID id = UUID.randomUUID();
    cache.put(id, -1, null, 0L);
    Assertions.assertEquals(ReactOperations.all(), cache.get(id).mask());
    Assertions.assertEquals("", cache.get(id).owners());
  }

  @Test
  void invalidateRemovesTheEntry() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    UUID id = UUID.randomUUID();
    cache.put(id, ReactOperations.all(), "", 0L);
    cache.invalidate(id);
    Assertions.assertNull(cache.get(id));
  }

  @Test
  void sweepDropsOnlyEntriesOlderThanTheRetention() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    UUID stale = UUID.randomUUID();
    UUID fresh = UUID.randomUUID();
    cache.put(stale, ReactOperations.all(), "", 1_000L);
    cache.put(fresh, ReactOperations.all(), "", 9_000L);

    Assertions.assertEquals(1, cache.sweep(10_000L, 5_000L));
    Assertions.assertNull(cache.get(stale));
    Assertions.assertNotNull(cache.get(fresh));
  }

  @Test
  void sweepOfAnEmptyCacheIsANoOp() {
    Assertions.assertEquals(0, new ProtectionMaskCache().sweep(10_000L, 1L));
  }

  @Test
  void protectedCountIgnoresKnownUnprotectedEntries() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    cache.put(UUID.randomUUID(), ReactOperations.NONE, "", 0L);
    cache.put(UUID.randomUUID(), ReactOperations.of(ReactOperation.TRIM), "", 0L);

    Assertions.assertEquals(2, cache.size());
    Assertions.assertEquals(1, cache.protectedCount());
  }

  @Test
  void clearEmptiesTheCache() {
    ProtectionMaskCache cache = new ProtectionMaskCache();
    cache.put(UUID.randomUUID(), ReactOperations.all(), "", 0L);
    cache.clear();
    Assertions.assertEquals(0, cache.size());
  }
}
