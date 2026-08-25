package art.arcane.react.model;

import art.arcane.volmlib.util.math.BlockPosition;

import java.util.HashSet;
import java.util.Set;

public final class Circuit {
  private final long id;
  private final Set<BlockPosition> positions;
  private int events;
  private int pendingEvents;
  private long lastEventMs;
  private volatile long blockedUntilMs;

  public Circuit(long id, long now) {
    this.id = id;
    positions = new HashSet<>();
    lastEventMs = now;
  }

  public long getId() {
    return id;
  }

  public int countBlocks() {
    return positions.size();
  }

  public int getEvents() {
    return events;
  }

  public long getLastEventMs() {
    return lastEventMs;
  }

  public long getBlockedUntilMs() {
    return blockedUntilMs;
  }

  public boolean isBlocked(long now) {
    return blockedUntilMs > now;
  }

  Set<BlockPosition> positions() {
    return positions;
  }

  void add(BlockPosition position) {
    positions.add(position);
  }

  void remove(BlockPosition position) {
    positions.remove(position);
  }

  void recordEvent(long now) {
    pendingEvents++;
    lastEventMs = now;
  }

  void merge(Circuit other) {
    positions.addAll(other.positions);
    events += other.events;
    pendingEvents += other.pendingEvents;
    lastEventMs = Math.max(lastEventMs, other.lastEventMs);
    blockedUntilMs = Math.max(blockedUntilMs, other.blockedUntilMs);
  }

  void rollWindow() {
    events = pendingEvents;
    pendingEvents = 0;
  }

  void blockUntil(long untilMs) {
    blockedUntilMs = Math.max(blockedUntilMs, untilMs);
  }
}
