package art.arcane.react.api.sampler;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class SamplerHealth {
  private final LongAdder attempts = new LongAdder();
  private final LongAdder successes = new LongAdder();
  private final LongAdder failures = new LongAdder();
  private final LongAdder cacheHits = new LongAdder();
  private final LongAdder inFlightSkips = new LongAdder();
  private final AtomicLong maximumDuration = new AtomicLong();
  private final AtomicLong maximumQueue = new AtomicLong();
  private volatile long lastDuration;
  private volatile long lastQueue;
  private volatile long lastSuccessAtMs;
  private volatile boolean failed;

  public long begin() {
    attempts.increment();
    return System.nanoTime();
  }

  public void finish(long startedNanos, boolean success) {
    long duration = Math.max(0L, System.nanoTime() - startedNanos);
    lastDuration = duration;
    maximumDuration.accumulateAndGet(duration, Math::max);
    failed = !success;
    if (success) {
      successes.increment();
      lastSuccessAtMs = System.currentTimeMillis();
    } else {
      failures.increment();
    }
  }

  public void queued(long submittedNanos) {
    long delay = Math.max(0L, System.nanoTime() - submittedNanos);
    lastQueue = delay;
    maximumQueue.accumulateAndGet(delay, Math::max);
  }

  public void cacheHit() {
    cacheHits.increment();
  }

  public void skipped() {
    inFlightSkips.increment();
  }

  public boolean failed() {
    return failed;
  }

  public Sampler.Diagnostics snapshot() {
    return new Sampler.Diagnostics(attempts.sum(), successes.sum(), failures.sum(), cacheHits.sum(),
        inFlightSkips.sum(), lastDuration, maximumDuration.get(), lastQueue, maximumQueue.get(), lastSuccessAtMs);
  }
}
