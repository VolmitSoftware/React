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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.event.NaughtyRegisteredListener;
import art.arcane.react.api.event.layer.MinecartSpawnEvent;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@EqualsAndHashCode(callSuper = true)
@Data
@ConfigDescription("Measures event-handler time per plugin by wrapping registered listeners while instrumentation is active.")
public class EventController extends TickedObject implements IController, Listener {
  private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();
  private static final long MIN_DUTY_PERIOD_MS = 1000L;
  private static final String WRAPPER_CLASS_NAME = NaughtyRegisteredListener.class.getName();
  private static final PublishedWindow NO_WINDOW = new PublishedWindow(0, 0D, 0, 0, 0D, 0D, 0L, Map.of(), Map.of());
  private static volatile Field allListsField;
  private static volatile Field handlersField;
  private static volatile Field handlerSlotsField;
  private static volatile Field executorField;
  private static volatile boolean reflectionResolved;

  @ConfigDoc(value = "Controls when listener timing wrappers are installed: ALWAYS keeps them installed, ON_DEMAND installs them only while a monitor, map, placeholder, or web viewer reads event data, and DUTY_CYCLE additionally opens a periodic measurement window.", impact = "ALWAYS charges every event handler a timing overhead; ON_DEMAND removes that cost at idle but records history gaps; DUTY_CYCLE trades periodic gaps for a small recurring cost.")
  private InstrumentationMode instrumentation = InstrumentationMode.ALWAYS;
  @ConfigDoc(value = "Milliseconds after the last event-data read before wrappers are removed in the on-demand and duty-cycle modes.", impact = "Higher values keep instrumentation installed longer after a viewer leaves; lower values remove it sooner.")
  private long samplerActivityWindowMS = 15000;
  @ConfigDoc(value = "Length in milliseconds of each periodic measurement window in the duty-cycle mode.", impact = "Longer windows produce more recorded samples per period at a higher recurring cost.")
  private long dutyCycleOnMS = 5000;
  @ConfigDoc(value = "Milliseconds between the starts of periodic measurement windows in the duty-cycle mode.", impact = "Shorter periods record event data more often and cost more; longer periods leave wider history gaps.")
  private long dutyCyclePeriodMS = 60000;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient volatile PublishedWindow window = NO_WINDOW;
  private transient final AtomicBoolean running = new AtomicBoolean(false);
  private transient final AtomicBoolean active = new AtomicBoolean(false);
  private transient final AtomicBoolean instrumentationRequested = new AtomicBoolean(false);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicLong mutationRevision = new AtomicLong();
  private transient final AtomicLong measuredTicks = new AtomicLong();
  private transient volatile boolean spiesInjected = false;
  private transient volatile long lastSamplerActivity = 0;
  private transient volatile long instrumentationOwner;
  private transient volatile long cleanupGeneration;
  private transient volatile long dutyWindowStartMs;
  private transient volatile long windowStartNanos;

  public EventController() {
    super("react", "event", 5000);
  }

  @Override
  public String getName() {
    return "Event";
  }

  @Override
  public void start() {
    long generation = lifecycleGeneration.incrementAndGet();
    instrumentationOwner = OWNER_SEQUENCE.incrementAndGet();
    cleanupGeneration = generation;
    active.set(true);
    spiesInjected = false;
    lastSamplerActivity = 0;
    dutyWindowStartMs = 0L;
    clearPublishedMetrics();
    instrumentationRequested.set(mode() == InstrumentationMode.ALWAYS);
    requestReconciliation();
  }

  @Override
  public void stop() {
    active.set(false);
    instrumentationRequested.set(false);
    lifecycleGeneration.incrementAndGet();
    uninstallOwner(instrumentationOwner);
  }

  @Override
  public void postStart() {

  }

  public void markSamplerActivity() {
    lastSamplerActivity = System.currentTimeMillis();
    if (active.get()
        && !spiesInjected
        && mode() != InstrumentationMode.ALWAYS
        && instrumentationRequested.compareAndSet(false, true)) {
      requestReconciliation();
    }
  }

  public boolean isMeasuring() {
    return isFresh(window);
  }

  public double getEventTimeMsPerSecond() {
    PublishedWindow published = window;
    return isFresh(published) ? published.totalTime() / published.windowSeconds() : 0D;
  }

  public double getPluginEventTimeMsPerSecond(String pluginName) {
    PublishedWindow published = window;
    return isFresh(published) ? pluginTime(published, pluginName) / published.windowSeconds() : 0D;
  }

  public double getPluginEventTimeMS(String pluginName) {
    PublishedWindow published = window;
    return isFresh(published) ? pluginTime(published, pluginName) : 0D;
  }

  public Map<String, Double> snapshotPluginEventTimeMS() {
    PublishedWindow published = window;
    return isFresh(published) ? new HashMap<>(published.pluginTime()) : new HashMap<>();
  }

  public Map<String, Integer> snapshotPluginEventCalls() {
    PublishedWindow published = window;
    return isFresh(published) ? new HashMap<>(published.pluginCalls()) : new HashMap<>();
  }

  public int getListenerCount() {
    return window.listenerCount();
  }

  public double getTotalTime() {
    return window.totalTime();
  }

  public int getCalls() {
    return window.calls();
  }

  public int getAsyncCalls() {
    return window.asyncCalls();
  }

  public double getCallsPerTick() {
    return window.callsPerTick();
  }

  public double getWindowSeconds() {
    return window.windowSeconds();
  }

  public long getWindowPublishedAtMs() {
    return window.publishedAtMs();
  }

  @Override
  public void onTick() {
    if (!active.get()) {
      return;
    }

    boolean wanted = evaluateInstrumentation(System.currentTimeMillis());
    instrumentationRequested.set(wanted);
    if (!wanted && !spiesInjected && cleanupGeneration == 0L) {
      return;
    }

    requestReconciliation();
  }

  public void call(Event event) {
    Bukkit.getServer().getPluginManager().callEvent(event);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(BlockDispenseEvent e) {
    if (e.getItem().getType().equals(Material.MINECART)
        || e.getItem().getType().equals(Material.CHEST_MINECART)
        || e.getItem().getType().equals(Material.TNT_MINECART)
        || e.getItem().getType().equals(Material.HOPPER_MINECART)
        || e.getItem().getType().equals(Material.FURNACE_MINECART)
        || e.getItem().getType().equals(Material.COMMAND_BLOCK_MINECART)) {
      MinecartSpawnEvent s = new MinecartSpawnEvent(e.getBlock().getLocation());
      call(s);
      if (s.isCancelled()) {
        e.setCancelled(true);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityPlaceEvent e) {
    if (e.getEntityType().name().startsWith("MINECART")) {
      MinecartSpawnEvent s = new MinecartSpawnEvent(e.getEntity().getLocation(), e.getPlayer());
      call(s);

      if (s.isCancelled()) {
        e.setCancelled(true);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ServerTickEvent event) {
    if (spiesInjected) {
      measuredTicks.incrementAndGet();
    }
  }

  private static boolean resolveReflection() {
    if (reflectionResolved) {
      return allListsField != null;
    }

    synchronized (EventController.class) {
      if (reflectionResolved) {
        return allListsField != null;
      }

      try {
        Field allLists = HandlerList.class.getDeclaredField("allLists");
        Field handlers = HandlerList.class.getDeclaredField("handlers");
        Field handlerSlots = HandlerList.class.getDeclaredField("handlerslots");
        Field executor = RegisteredListener.class.getDeclaredField("executor");
        allLists.setAccessible(true);
        handlers.setAccessible(true);
        handlerSlots.setAccessible(true);
        executor.setAccessible(true);
        executorField = executor;
        handlersField = handlers;
        handlerSlotsField = handlerSlots;
        allListsField = allLists;
      } catch (Throwable e) {
        React.reportError("Failed to resolve Bukkit event-handler instrumentation fields", e);
      }

      reflectionResolved = true;
      return allListsField != null;
    }
  }

  @SuppressWarnings("unchecked")
  private static ArrayList<HandlerList> allHandlerLists() {
    try {
      return (ArrayList<HandlerList>) allListsField.get(null);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static EnumMap<EventPriority, ArrayList<RegisteredListener>> handlerSlotsOf(HandlerList list) {
    try {
      return (EnumMap<EventPriority, ArrayList<RegisteredListener>>) handlerSlotsField.get(list);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static EventExecutor executorOf(RegisteredListener listener) {
    try {
      return (EventExecutor) executorField.get(listener);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void rebake(HandlerList handlerList) {
    try {
      handlersField.set(handlerList, null);
      handlerList.bake();
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static boolean isWrapper(RegisteredListener registered) {
    if (registered instanceof NaughtyRegisteredListener) {
      return true;
    }

    Class<?> type = registered.getClass();
    return type != RegisteredListener.class && WRAPPER_CLASS_NAME.equals(type.getName());
  }

  private static RegisteredListener unwrapAll(RegisteredListener registered) {
    RegisteredListener current = registered;
    while (current != null && isWrapper(current)) {
      current = current instanceof NaughtyRegisteredListener naughty ? naughty.delegate() : foreignDelegateOf(current);
    }
    return current;
  }

  private static RegisteredListener foreignDelegateOf(RegisteredListener wrapper) {
    try {
      Field delegate = wrapper.getClass().getDeclaredField("delegate");
      delegate.setAccessible(true);
      return (RegisteredListener) delegate.get(wrapper);
    } catch (NoSuchFieldException missing) {
      return new RegisteredListener(wrapper.getListener(), executorOf(wrapper), wrapper.getPriority(),
          wrapper.getPlugin(), wrapper.isIgnoringCancelled());
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private InstrumentationMode mode() {
    InstrumentationMode configured = instrumentation;
    return configured == null ? InstrumentationMode.ALWAYS : configured;
  }

  private long windowFreshnessMS() {
    return 3L * Math.max(1000L, getTinterval());
  }

  private boolean isFresh(PublishedWindow published) {
    long publishedAtMs = published.publishedAtMs();
    return active.get() && publishedAtMs > 0L && System.currentTimeMillis() - publishedAtMs <= windowFreshnessMS();
  }

  private static double pluginTime(PublishedWindow published, String pluginName) {
    Double time = published.pluginTime().get(pluginName);
    return time == null ? 0D : time;
  }

  private boolean evaluateInstrumentation(long nowMs) {
    boolean demanded = nowMs - lastSamplerActivity <= samplerActivityWindowMS;
    return switch (mode()) {
      case ALWAYS -> true;
      case ON_DEMAND -> demanded;
      case DUTY_CYCLE -> demanded || dutyWindowOpen(nowMs);
    };
  }

  private boolean dutyWindowOpen(long nowMs) {
    long period = Math.max(MIN_DUTY_PERIOD_MS, dutyCyclePeriodMS);
    long on = Math.max(1L, Math.min(dutyCycleOnMS, period));
    long windowStart = dutyWindowStartMs;
    if (windowStart == 0L || nowMs - windowStart >= period) {
      dutyWindowStartMs = nowMs;
      return true;
    }

    return nowMs - windowStart < on;
  }

  private void requestReconciliation() {
    mutationRevision.incrementAndGet();
    scheduleReconciliation();
  }

  private void scheduleReconciliation() {
    if (!running.compareAndSet(false, true)) {
      return;
    }

    if (runOnAuthoritativeScheduler(this::runReconciliation)) {
      return;
    }

    running.set(false);
  }

  private void runReconciliation() {
    long revision = mutationRevision.get();
    try {
      reconcileLatestState();
    } catch (Throwable throwable) {
      React.reportError(throwable);
    } finally {
      running.set(false);
      if (revision != mutationRevision.get()) {
        scheduleReconciliation();
      }
    }
  }

  private void reconcileLatestState() {
    if (!resolveReflection()) {
      return;
    }

    long generation = lifecycleGeneration.get();
    long owner = instrumentationOwner;
    if (!active.get()) {
      uninstallHandlers(owner, false, new WindowTotals());
      spiesInjected = false;
      return;
    }

    if (cleanupGeneration == generation) {
      uninstallHandlers(owner, true, new WindowTotals());
      if (cleanupGeneration == generation) {
        cleanupGeneration = 0L;
      }
    }

    if (!active.get() || generation != lifecycleGeneration.get() || owner != instrumentationOwner) {
      return;
    }

    if (!instrumentationRequested.get()) {
      if (spiesInjected) {
        WindowTotals totals = new WindowTotals();
        uninstallHandlers(owner, false, totals);
        spiesInjected = false;
        publishWindow(totals);
      }
      return;
    }

    boolean hadSpies = spiesInjected;
    WindowTotals totals = new WindowTotals();
    instrumentAndDrain(owner, totals);
    spiesInjected = true;
    if (hadSpies) {
      publishWindow(totals);
      return;
    }

    beginWindow();
  }

  private void uninstallOwner(long owner) {
    if (!resolveReflection()) {
      return;
    }

    uninstallHandlers(owner, false, new WindowTotals());
    spiesInjected = false;
  }

  private void instrumentAndDrain(long owner, WindowTotals totals) {
    ArrayList<HandlerList> handlerLists = new ArrayList<>(allHandlerLists());
    for (HandlerList handlerList : handlerLists) {
      try {
        instrumentHandlerList(handlerList, owner, totals);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
  }

  private void instrumentHandlerList(HandlerList handlerList, long owner, WindowTotals totals) {
    EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = handlerSlotsOf(handlerList);
    if (slots == null) {
      return;
    }

    boolean changed = false;
    try {
      for (ArrayList<RegisteredListener> priorityListeners : slots.values()) {
        for (int index = 0; index < priorityListeners.size(); index++) {
          RegisteredListener registered = priorityListeners.get(index);
          if (registered == null) {
            continue;
          }

          totals.listeners++;
          if (registered instanceof NaughtyRegisteredListener naughty && naughty.isOwnedBy(owner)) {
            drainListener(naughty, totals);
            continue;
          }

          RegisteredListener original = unwrapAll(registered);
          priorityListeners.set(index, new NaughtyRegisteredListener(original, executorOf(original), owner));
          changed = true;
        }
      }
    } finally {
      if (changed) {
        rebake(handlerList);
      }
    }
  }

  private void uninstallHandlers(long owner, boolean allOwners, WindowTotals totals) {
    int removed = 0;
    ArrayList<HandlerList> handlerLists = new ArrayList<>(allHandlerLists());
    for (HandlerList handlerList : handlerLists) {
      try {
        removed += uninstallHandlerList(handlerList, owner, allOwners, totals);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
    if (removed > 0) {
      React.verbose("Pulled out " + removed + " event listener spies.");
    }
  }

  private int uninstallHandlerList(HandlerList handlerList, long owner, boolean allOwners, WindowTotals totals) {
    EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = handlerSlotsOf(handlerList);
    if (slots == null) {
      return 0;
    }

    int removed = 0;
    try {
      for (ArrayList<RegisteredListener> priorityListeners : slots.values()) {
        for (int index = 0; index < priorityListeners.size(); index++) {
          RegisteredListener registered = priorityListeners.get(index);
          if (registered == null) {
            continue;
          }

          totals.listeners++;
          if (!isWrapper(registered)) {
            continue;
          }

          boolean owned = registered instanceof NaughtyRegisteredListener naughty && naughty.isOwnedBy(owner);
          if (!owned && !allOwners) {
            continue;
          }

          if (owned) {
            drainListener((NaughtyRegisteredListener) registered, totals);
          }
          priorityListeners.set(index, unwrapAll(registered));
          removed++;
        }
      }
      return removed;
    } finally {
      if (removed > 0) {
        rebake(handlerList);
      }
    }
  }

  private void drainListener(NaughtyRegisteredListener naughty, WindowTotals totals) {
    NaughtyRegisteredListener.CounterSnapshot snapshot = naughty.drainCounters();
    double listenerTime = snapshot.timeNanos() / 1.0E6D;
    int listenerCalls = saturatingInt(snapshot.calls());
    totals.timeNanos += snapshot.timeNanos();
    totals.calls += snapshot.calls();
    totals.asyncCalls += snapshot.asyncCalls();
    totals.pluginTime.merge(naughty.pluginName, listenerTime, Double::sum);
    totals.pluginCalls.merge(naughty.pluginName, listenerCalls, EventController::saturatingAdd);
  }

  private void beginWindow() {
    windowStartNanos = System.nanoTime();
    measuredTicks.set(0L);
  }

  private void publishWindow(WindowTotals totals) {
    long nowNanos = System.nanoTime();
    long elapsedNanos = nowNanos - windowStartNanos;
    windowStartNanos = nowNanos;
    window = new PublishedWindow(
        totals.listeners,
        totals.timeNanos / 1.0E6D,
        saturatingInt(totals.calls),
        saturatingInt(totals.asyncCalls),
        averageCallsPerTick(totals.calls, measuredTicks.getAndSet(0L)),
        Math.max(1.0E-6D, elapsedNanos / 1.0E9D),
        System.currentTimeMillis(),
        Map.copyOf(totals.pluginTime),
        Map.copyOf(totals.pluginCalls)
    );
  }

  private void clearPublishedMetrics() {
    window = NO_WINDOW;
    windowStartNanos = 0L;
    measuredTicks.set(0L);
  }

  private boolean isAuthoritativeThread() {
    try {
      return J.isFoliaThreading() ? Bukkit.isGlobalTickThread() : Bukkit.isPrimaryThread();
    } catch (Throwable throwable) {
      return false;
    }
  }

  private boolean runOnAuthoritativeScheduler(Runnable mutation) {
    try {
      if (isAuthoritativeThread()) {
        mutation.run();
        return true;
      }
      if (FoliaScheduler.runGlobal(React.instance, mutation)) {
        return true;
      }
      React.reportError(new IllegalStateException(
          "Failed to schedule event handler-list mutation on the authoritative server thread"));
    } catch (Throwable throwable) {
      React.reportError(new IllegalStateException(
          "Failed to route event handler-list mutation to the authoritative server thread", throwable));
    }
    return false;
  }

  private static int saturatingInt(long value) {
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
  }

  private static int saturatingAdd(int left, int right) {
    return saturatingInt((long) left + right);
  }

  static double averageCallsPerTick(long calls, long ticks) {
    if (calls <= 0L || ticks <= 0L) {
      return 0D;
    }

    return (double) calls / ticks;
  }

  public enum InstrumentationMode {
    ALWAYS,
    ON_DEMAND,
    DUTY_CYCLE
  }

  private record PublishedWindow(int listenerCount, double totalTime, int calls, int asyncCalls, double callsPerTick,
                                 double windowSeconds, long publishedAtMs, Map<String, Double> pluginTime,
                                 Map<String, Integer> pluginCalls) {
  }

  private static final class WindowTotals {
    private final Map<String, Double> pluginTime = new HashMap<>();
    private final Map<String, Integer> pluginCalls = new HashMap<>();
    private int listeners;
    private long timeNanos;
    private long calls;
    private long asyncCalls;
  }
}
