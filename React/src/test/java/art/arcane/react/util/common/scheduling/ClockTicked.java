package art.arcane.react.util.common.scheduling;

import java.util.function.LongSupplier;

final class ClockTicked implements Ticked {
  private final LongSupplier clock;
  private final String tid;
  private final long interval;
  private final Runnable body;
  private volatile long lastTick;
  volatile int runs;

  ClockTicked(LongSupplier clock, String tid, long interval) {
    this(clock, tid, interval, () -> {
    });
  }

  ClockTicked(LongSupplier clock, String tid, long interval, Runnable body) {
    this.clock = clock;
    this.tid = tid;
    this.interval = interval;
    this.body = body;
    this.lastTick = clock.getAsLong();
  }

  @Override
  public void unregister() {
  }

  @Override
  public boolean isBursting() {
    return false;
  }

  @Override
  public boolean isSkipping() {
    return false;
  }

  @Override
  public void stopBursting() {
  }

  @Override
  public void stopSkipping() {
  }

  @Override
  public long getTickCount() {
    return runs;
  }

  @Override
  public long getAge() {
    return 0L;
  }

  @Override
  public void burst(int ticks) {
  }

  @Override
  public void skip(int ticks) {
  }

  @Override
  public long getTlastTick() {
    return lastTick;
  }

  @Override
  public long getTinterval() {
    return interval;
  }

  @Override
  public void setTinterval(long ms) {
  }

  @Override
  public void tick() {
    lastTick = clock.getAsLong();
    runs++;
    body.run();
  }

  @Override
  public String getTgroup() {
    return "test";
  }

  @Override
  public String getTid() {
    return tid;
  }

  @Override
  public boolean shouldTick() {
    return Ticked.isTickDue(clock.getAsLong(), lastTick, interval);
  }
}
