package art.arcane.react.mod;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ModChunkSamplesTest {
    @Test
    void entityCountsAreRecountedOnceTheyAreASecondOld() {
        long counted = 50_000L;

        Assertions.assertFalse(ModChunkSamples.entityCountsFresh(0L, counted));
        Assertions.assertTrue(ModChunkSamples.entityCountsFresh(counted, counted));
        Assertions.assertTrue(ModChunkSamples.entityCountsFresh(counted, counted + ModChunkSamples.ENTITY_COUNT_FRESH_MS - 1L));
        Assertions.assertFalse(ModChunkSamples.entityCountsFresh(counted, counted + ModChunkSamples.ENTITY_COUNT_FRESH_MS));
        Assertions.assertFalse(ModChunkSamples.entityCountsFresh(counted, counted - 1L));
    }

    @Test
    void entityCountsNobodyRefreshesExpireAfterTheKeepWindow() {
        long counted = 50_000L;

        Assertions.assertFalse(ModChunkSamples.entityCountsExpired(counted, counted + ModChunkSamples.ENTITY_COUNT_KEEP_MS));
        Assertions.assertTrue(ModChunkSamples.entityCountsExpired(counted, counted + ModChunkSamples.ENTITY_COUNT_KEEP_MS + 1L));
    }

    @Test
    void freshSamplesWithoutCountsAskForACount() {
        ModChunkSamples samples = new ModChunkSamples();

        Assertions.assertFalse(samples.entityCountsFresh(System.currentTimeMillis()));
        Assertions.assertTrue(samples.chunks().isEmpty());
    }
}
