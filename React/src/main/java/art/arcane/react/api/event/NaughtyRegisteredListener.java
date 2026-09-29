/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.LongAdder;

public class NaughtyRegisteredListener extends RegisteredListener {
  private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

  public final String pluginName;
  private final RegisteredListener delegate;
  private final long instrumentationOwner;
  private final LongAdder timeNanos;
  private final LongAdder calls;
  private final LongAdder asyncCalls;

  public NaughtyRegisteredListener(@NotNull RegisteredListener delegate, long instrumentationOwner) {
    super(delegate.getListener(), delegate.getExecutor(), delegate.getPriority(), delegate.getPlugin(),
        delegate.isIgnoringCancelled());
    this.delegate = delegate;
    this.pluginName = resolvePluginName(delegate.getPlugin());
    this.instrumentationOwner = instrumentationOwner;
    this.timeNanos = new LongAdder();
    this.calls = new LongAdder();
    this.asyncCalls = new LongAdder();
  }

  private static String resolvePluginName(Plugin plugin) {
    if (plugin == null || plugin.getName() == null) {
      return "Unknown";
    }

    String name = plugin.getName().trim();
    return name.isBlank() ? "Unknown" : name;
  }

  @Override
  public void callEvent(@NotNull final Event event) throws EventException {
    if (event.isAsynchronous()) {
      asyncCalls.increment();
      delegate.callEvent(event);
      return;
    }

    Frame frame = FRAMES.get();
    long parentChildNanos = frame.childNanos;
    frame.childNanos = 0L;
    frame.depth++;
    long start = System.nanoTime();
    try {
      delegate.callEvent(event);
    } finally {
      long total = System.nanoTime() - start;
      record(total - frame.childNanos);
      frame.depth--;
      frame.childNanos = frame.depth == 0 ? 0L : parentChildNanos + total;
    }
  }

  public RegisteredListener delegate() {
    return delegate;
  }

  public boolean isOwnedBy(long owner) {
    return instrumentationOwner == owner;
  }

  public CounterSnapshot drainCounters() {
    return new CounterSnapshot(timeNanos.sumThenReset(), calls.sumThenReset(), asyncCalls.sumThenReset());
  }

  private void record(long exclusiveNanos) {
    timeNanos.add(Math.max(0L, exclusiveNanos));
    calls.increment();
  }

  public record CounterSnapshot(long timeNanos, long calls, long asyncCalls) {
  }

  private static final class Frame {
    private int depth;
    private long childNanos;
  }
}
