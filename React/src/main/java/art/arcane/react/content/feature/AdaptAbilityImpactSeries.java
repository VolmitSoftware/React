package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AdaptAbilityImpactSeries {
  private AdaptAbilityImpactSeries() {
  }

  static Snapshot snapshot() {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return Snapshot.empty();
    }

    RemoteSamplerBridge bridge = controller.getRemoteSamplerBridge();
    return fromSamples(bridge.snapshot("adapt"));
  }

  static Snapshot fromSamples(Map<String, IntegrationMetricSample> samples) {
    if (samples == null || samples.isEmpty()) {
      return Snapshot.empty();
    }

    IntegrationMetricSample aggregateSample = samples.get(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS);
    boolean aggregateAvailable = isUsable(aggregateSample);
    double aggregateGuardChecks = aggregateAvailable ? safeValue(aggregateSample) : 0D;
    Map<String, MutableEntry> entriesByAbility = new HashMap<>();

    for (Map.Entry<String, IntegrationMetricSample> sampleEntry : samples.entrySet()) {
      String key = sampleEntry.getKey();
      IntegrationMetricSample sample = sampleEntry.getValue();
      if (!IntegrationMetricSchema.isAdaptAbilityDetailKey(key)
          || !isUsable(sample)
          || !key.equals(sample.descriptor().key())) {
        continue;
      }

      String abilityId = IntegrationMetricSchema.adaptAbilityId(key);
      String signal = IntegrationMetricSchema.adaptAbilitySignal(key);
      if (abilityId.isBlank() || signal.isBlank()) {
        continue;
      }

      MutableEntry entry = entriesByAbility.computeIfAbsent(abilityId, MutableEntry::new);
      entry.accept(signal, safeValue(sample));
    }

    List<Entry> entries = new ArrayList<>(entriesByAbility.size());
    double detailedExecutionOps = 0D;
    double totalExecutionTimingMs = 0D;
    double totalGuardTimingMs = 0D;
    for (MutableEntry mutableEntry : entriesByAbility.values()) {
      Entry entry = mutableEntry.freeze();
      if (!entry.hasActivity()) {
        continue;
      }
      entries.add(entry);
      detailedExecutionOps += entry.executionOps();
      totalExecutionTimingMs += entry.executionTimingMs();
      totalGuardTimingMs += entry.guardTimingMs();
    }

    entries.sort(Comparator
        .comparingDouble(Entry::executionTimingMs).reversed()
        .thenComparing(Comparator.comparingDouble(Entry::executionOps).reversed())
        .thenComparing(Entry::abilityId));
    return new Snapshot(
        List.copyOf(entries),
        aggregateGuardChecks,
        detailedExecutionOps,
        totalExecutionTimingMs,
        totalGuardTimingMs,
        aggregateAvailable
    );
  }

  private static boolean isUsable(IntegrationMetricSample sample) {
    return sample != null
        && sample.available()
        && sample.numericValue() != null
        && Double.isFinite(sample.numericValue());
  }

  private static double safeValue(IntegrationMetricSample sample) {
    return Math.max(0D, sample == null ? 0D : sample.valueOr(0D));
  }

  private static String displayName(String abilityId) {
    if (abilityId == null || abilityId.isBlank()) {
      return "";
    }

    String spaced = abilityId
        .replace('_', ' ')
        .replace('-', ' ')
        .replace('.', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return abilityId;
    }

    String[] words = spaced.split("\\s+");
    StringBuilder display = new StringBuilder(spaced.length());
    for (String word : words) {
      if (word.isBlank()) {
        continue;
      }
      if (!display.isEmpty()) {
        display.append(' ');
      }
      display.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        display.append(word.substring(1));
      }
    }
    return display.isEmpty() ? abilityId : display.toString();
  }

  record Entry(
      String abilityId,
      String displayName,
      double executionOps,
      double executionTimingMs,
      double guardChecks,
      double guardTimingMs
  ) {
    boolean hasActivity() {
      return executionOps > 0D || executionTimingMs > 0D;
    }
  }

  record Snapshot(
      List<Entry> entries,
      double aggregateGuardChecks,
      double detailedExecutionOps,
      double totalExecutionTimingMs,
      double totalGuardTimingMs,
      boolean aggregateAvailable
  ) {
    private static Snapshot empty() {
      return new Snapshot(List.of(), 0D, 0D, 0D, 0D, false);
    }
  }

  private static final class MutableEntry {
    private final String abilityId;
    private double executionOps;
    private double executionTimingMs;
    private double guardChecks;
    private double guardTimingMs;

    private MutableEntry(String abilityId) {
      this.abilityId = abilityId;
    }

    private void accept(String signal, double value) {
      switch (signal) {
        case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS -> executionOps = value;
        case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS -> executionTimingMs = value;
        case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS -> guardChecks = value;
        case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS -> guardTimingMs = value;
        default -> {
        }
      }
    }

    private Entry freeze() {
      return new Entry(abilityId, displayName(abilityId), executionOps, executionTimingMs, guardChecks, guardTimingMs);
    }
  }
}
