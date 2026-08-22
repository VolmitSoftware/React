package art.arcane.react.content.feature;

import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class AdaptAbilityImpactSeriesTest {
  @Test
  void ranksAbilitiesByMeasuredExecutionTimingAndReportsCallbackOps() {
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> samples = new HashMap<>();
    samples.put(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, sample(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, 250D, now));
    putAbility(samples, "excavation-spelunker", 80D, 1.5D, 100D, 0.25D, now);
    putAbility(samples, "excavation-seismic-ping", 120D, 4.25D, 150D, 0.75D, now);

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(samples);

    Assertions.assertTrue(snapshot.aggregateAvailable());
    Assertions.assertEquals(250D, snapshot.aggregateGuardChecks());
    Assertions.assertEquals(200D, snapshot.detailedExecutionOps());
    Assertions.assertEquals(5.75D, snapshot.totalExecutionTimingMs());
    Assertions.assertEquals(1D, snapshot.totalGuardTimingMs());
    Assertions.assertEquals(2, snapshot.entries().size());
    Assertions.assertEquals("excavation-seismic-ping", snapshot.entries().get(0).abilityId());
    Assertions.assertEquals("Excavation Seismic Ping", snapshot.entries().get(0).displayName());
    Assertions.assertEquals(120D, snapshot.entries().get(0).executionOps());
    Assertions.assertEquals(4.25D, snapshot.entries().get(0).executionTimingMs());
    Assertions.assertEquals(150D, snapshot.entries().get(0).guardChecks());
    Assertions.assertEquals(0.75D, snapshot.entries().get(0).guardTimingMs());
  }

  @Test
  void executionOpsBreakTiesAfterTiming() {
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> samples = new HashMap<>();
    putAbility(samples, "lower-throughput", 4D, 2D, 8D, 0.2D, now);
    putAbility(samples, "higher-throughput", 12D, 2D, 18D, 0.3D, now);

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(samples);

    Assertions.assertEquals("higher-throughput", snapshot.entries().get(0).abilityId());
    Assertions.assertEquals("lower-throughput", snapshot.entries().get(1).abilityId());
  }

  @Test
  void unavailableAndInactiveDetailsDoNotCreateRankedRows() {
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> samples = new HashMap<>();
    String unavailableKey = detailKey("unavailable", IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS);
    samples.put(unavailableKey, IntegrationMetricSample.unavailable(
        IntegrationMetricSchema.descriptor(unavailableKey),
        "provider-missing",
        now
    ));
    putAbility(samples, "inactive", 0D, 0D, 0D, 0D, now);
    samples.put(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, sample(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, 25D, now));

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(samples);

    Assertions.assertTrue(snapshot.entries().isEmpty());
    Assertions.assertTrue(snapshot.aggregateAvailable());
    Assertions.assertEquals(25D, snapshot.aggregateGuardChecks());
  }

  @Test
  void guardOnlyDetailsDoNotClaimMeasuredExecutionImpact() {
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> samples = new HashMap<>();
    putAbility(samples, "guard-only", 0D, 0D, 40D, 1.5D, now);

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(samples);

    Assertions.assertTrue(snapshot.entries().isEmpty());
  }

  @Test
  void operationVolumeWithoutMeasuredExecutionTimeDoesNotCreateRankedRows() {
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> samples = new HashMap<>();
    putAbility(samples, "ops-only", 10_000D, 0D, 10_000D, 0D, now);

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(samples);

    Assertions.assertTrue(snapshot.entries().isEmpty());
  }

  @Test
  void mismatchedDescriptorCannotAttributeValueToAnotherAbility() {
    long now = System.currentTimeMillis();
    String mapKey = detailKey("reported-ability", IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS);
    String descriptorKey = detailKey("actual-ability", IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS);
    IntegrationMetricSample mismatched = IntegrationMetricSample.available(
        IntegrationMetricSchema.descriptor(descriptorKey),
        9D,
        now
    );

    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.fromSamples(Map.of(mapKey, mismatched));

    Assertions.assertTrue(snapshot.entries().isEmpty());
  }

  private static void putAbility(
      Map<String, IntegrationMetricSample> samples,
      String abilityId,
      double executionOps,
      double executionTimingMs,
      double guardChecks,
      double guardTimingMs,
      long now
  ) {
    String executionOpsKey = detailKey(abilityId, IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS);
    String executionTimingKey = detailKey(abilityId, IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS);
    String guardChecksKey = detailKey(abilityId, IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS);
    String guardTimingKey = detailKey(abilityId, IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS);
    samples.put(executionOpsKey, sample(executionOpsKey, executionOps, now));
    samples.put(executionTimingKey, sample(executionTimingKey, executionTimingMs, now));
    samples.put(guardChecksKey, sample(guardChecksKey, guardChecks, now));
    samples.put(guardTimingKey, sample(guardTimingKey, guardTimingMs, now));
  }

  private static String detailKey(String abilityId, String signal) {
    return IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_PREFIX + abilityId + "." + signal;
  }

  private static IntegrationMetricSample sample(String key, double value, long now) {
    return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, now);
  }
}
