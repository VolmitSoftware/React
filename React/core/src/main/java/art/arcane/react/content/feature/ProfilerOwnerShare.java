package art.arcane.react.content.feature;

import art.arcane.react.api.web.profiler.ProfileDtos;
import art.arcane.react.api.web.profiler.SamplingProfiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The running profiler's samples per owner, by display name, for a share pie; empty while it is stopped. Ranking walks
 * the whole call tree, so the result is kept for {@link #REFRESH_MS}.
 */
public final class ProfilerOwnerShare implements Supplier<Map<String, Long>> {
  static final long REFRESH_MS = 2_000L;
  private final Supplier<Optional<SamplingProfiler>> profiler;
  private final LongSupplier clock;
  private volatile long refreshedAt = Long.MIN_VALUE;
  private volatile Map<String, Long> cached = Map.of();

  public ProfilerOwnerShare(Supplier<Optional<SamplingProfiler>> profiler) {
    this(profiler, System::currentTimeMillis);
  }

  ProfilerOwnerShare(Supplier<Optional<SamplingProfiler>> profiler, LongSupplier clock) {
    this.profiler = profiler;
    this.clock = clock;
  }

  @Override
  public Map<String, Long> get() {
    long now = clock.getAsLong();
    if (refreshedAt != Long.MIN_VALUE && now - refreshedAt < REFRESH_MS) {
      return cached;
    }
    refreshedAt = now;
    Map<String, Long> shares = new LinkedHashMap<>();
    Optional<SamplingProfiler> current = profiler.get();
    if (current.isPresent() && current.get().running()) {
      for (ProfileDtos.OwnerRank owner : current.get().owners(false).owners()) {
        if (owner.samples() > 0) {
          shares.merge(owner.name(), owner.samples(), Long::sum);
        }
      }
    }
    cached = shares;
    return shares;
  }
}
