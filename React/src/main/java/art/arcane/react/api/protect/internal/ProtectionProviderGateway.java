package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ProtectionProviderGateway {
  public static final int DEFAULT_FAULT_LIMIT = 5;
  public static final long DEFAULT_SLOW_PROVIDER_MILLIS = 5L;
  public static final int MAX_RULES_PER_PROVIDER = 64;

  private final int faultLimit;
  private final long slowProviderMillis;
  private final Consumer<String> log;
  private final Map<String, Integer> faults = new ConcurrentHashMap<>();
  private final Set<String> quarantined = ConcurrentHashMap.newKeySet();
  private final Set<String> syntheticWarned = ConcurrentHashMap.newKeySet();
  private final Set<String> slowWarned = ConcurrentHashMap.newKeySet();
  private final AtomicLong totalFaults = new AtomicLong();
  private final AtomicLong totalRefusals = new AtomicLong();

  public ProtectionProviderGateway(int faultLimit, long slowProviderMillis, Consumer<String> log) {
    this.faultLimit = Math.max(0, faultLimit);
    this.slowProviderMillis = Math.max(0L, slowProviderMillis);
    this.log = log == null ? message -> {
    } : log;
  }

  public Collected collect(ReactProtectionProvider provider, String pluginName) {
    if (provider == null) {
      return Collected.REFUSED;
    }

    String owner = pluginName == null || pluginName.isBlank() ? "unknown" : pluginName;
    String providerId;

    try {
      providerId = ProtectionText.sanitize(provider.providerId(), 64);
    } catch (Throwable e) {
      totalRefusals.incrementAndGet();
      log.accept("[protect] provider from " + owner + " refused: providerId() threw "
          + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
      return Collected.REFUSED;
    }

    if (providerId.isEmpty()) {
      totalRefusals.incrementAndGet();
      log.accept("[protect] provider from " + owner + " refused: blank providerId()");
      return Collected.REFUSED;
    }

    if (ProtectionText.isSyntheticProviderId(providerId) && syntheticWarned.add(providerId)) {
      log.accept("[protect] provider from " + owner + " has a synthetic providerId (" + providerId
          + "); it changes on every restart. Implement providerId().");
    }

    if (quarantined.contains(providerId)) {
      return new Collected(providerId, null);
    }

    List<ReactProtectionRule> accepted = new ArrayList<>();
    boolean returnedNull = false;
    boolean overflowed = false;
    long startedAt = System.nanoTime();

    try {
      List<ReactProtectionRule> rules = provider.rules();

      if (rules == null) {
        returnedNull = true;
      } else {
        for (ReactProtectionRule rule : rules) {
          if (rule == null) {
            continue;
          }

          if (accepted.size() >= MAX_RULES_PER_PROVIDER) {
            overflowed = true;
            break;
          }

          accepted.add(rule);
        }
      }
    } catch (Throwable e) {
      recordFault(providerId, owner, "rules() threw " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()));
      return new Collected(providerId, null);
    }

    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

    if (slowProviderMillis > 0L && elapsedMillis >= slowProviderMillis && slowWarned.add(providerId)) {
      log.accept("[protect] provider " + providerId + " from " + owner + " took " + elapsedMillis
          + "ms in rules(); do not block on this call");
    }

    if (returnedNull) {
      recordFault(providerId, owner, "rules() returned null");
      return new Collected(providerId, null);
    }

    if (overflowed) {
      log.accept("[protect] provider " + providerId + " from " + owner + " declared more than "
          + MAX_RULES_PER_PROVIDER + " rules; the remainder is ignored");
    }

    return new Collected(providerId, new ProtectionDeclaration(providerId, owner, accepted));
  }

  public record Collected(String providerId, ProtectionDeclaration declaration) {
    public static final Collected REFUSED = new Collected("", null);

    public Collected {
      providerId = providerId == null ? "" : providerId;
    }
  }

  public boolean isQuarantined(String providerId) {
    return providerId != null && quarantined.contains(providerId);
  }

  public int faultCount(String providerId) {
    return providerId == null ? 0 : faults.getOrDefault(providerId, 0);
  }

  public long totalFaults() {
    return totalFaults.get();
  }

  public long totalRefusals() {
    return totalRefusals.get();
  }

  public Set<String> quarantinedProviders() {
    return Set.copyOf(quarantined);
  }

  public void forget(String providerId) {
    if (providerId == null) {
      return;
    }

    faults.remove(providerId);
    quarantined.remove(providerId);
    slowWarned.remove(providerId);
  }

  private void recordFault(String providerId, String pluginName, String detail) {
    totalFaults.incrementAndGet();
    int count = faults.merge(providerId, 1, Integer::sum);
    log.accept("[protect] provider " + providerId + " from " + pluginName + " faulted (" + count + "): " + detail);

    if (faultLimit > 0 && count >= faultLimit && quarantined.add(providerId)) {
      log.accept("[protect] provider " + providerId + " from " + pluginName + " quarantined after "
          + count + " faults; its last known rules stay in force until it re-registers");
    }
  }
}
