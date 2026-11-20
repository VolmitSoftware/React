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

import art.arcane.react.platform.EventMeasurement;

import art.arcane.react.React;
import art.arcane.react.api.event.NaughtyRegisteredListener;
import art.arcane.react.api.event.layer.MinecartSpawnEvent;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.platform.cost.OwnerCostMode;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerCostProbe;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@EqualsAndHashCode(callSuper = true)
@Data
@ConfigDescription("Measures event-handler time per plugin by wrapping registered listeners while instrumentation is active.")
public class EventController extends TickedObject implements EventMeasurement, IController, Listener, OwnerCostProbe {
  private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();
  private static final String WRAPPER_CLASS_NAME = NaughtyRegisteredListener.class.getName();
  private static volatile Field allListsField;
  private static volatile Field handlersField;
  private static volatile Field handlerSlotsField;
  private static volatile Field executorField;
  private static volatile boolean reflectionResolved;

  @ConfigDoc(value = "Controls when plugin and event timing wrappers are installed: ON_DEMAND (default) installs them only while a monitor, map, placeholder, or web viewer reads event data, ALWAYS keeps them installed for continuous plugin-cost history, and DUTY_CYCLE additionally opens a periodic measurement window.", impact = "ALWAYS charges every event handler a timing overhead; ON_DEMAND removes that cost at idle but records history gaps; DUTY_CYCLE trades periodic gaps for a small recurring cost.")
  private OwnerCostMode instrumentation = OwnerCostMode.ON_DEMAND;
  @ConfigDoc(value = "Milliseconds after the last event-data read before wrappers are removed in the on-demand and duty-cycle modes.", impact = "Higher values keep instrumentation installed longer after a viewer leaves; lower values remove it sooner.")
  private long samplerActivityWindowMS = OwnerCostModel.DEFAULT_ACTIVITY_WINDOW_MS;
  @ConfigDoc(value = "Length in milliseconds of each periodic measurement window in the duty-cycle mode.", impact = "Longer windows produce more recorded samples per period at a higher recurring cost.")
  private long dutyCycleOnMS = OwnerCostModel.DEFAULT_DUTY_ON_MS;
  @ConfigDoc(value = "Milliseconds between the starts of periodic measurement windows in the duty-cycle mode.", impact = "Shorter periods record event data more often and cost more; longer periods leave wider history gaps.")
  private long dutyCyclePeriodMS = OwnerCostModel.DEFAULT_DUTY_PERIOD_MS;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient final OwnerCostModel costModel = new OwnerCostModel(this);
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient final Map<String, long[]> pendingDrain = new HashMap<>();
  private transient final AtomicBoolean running = new AtomicBoolean(false);
  private transient final AtomicBoolean active = new AtomicBoolean(false);
  private transient final AtomicBoolean instrumentationRequested = new AtomicBoolean(false);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicLong mutationRevision = new AtomicLong();
  private transient final AtomicLong measuredTicks = new AtomicLong();
  private transient volatile boolean spiesInjected = false;
  private transient volatile long instrumentationOwner;
  private transient volatile long cleanupGeneration;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient volatile int listenerCount;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient volatile double callsPerTick;

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
    clearPublishedMetrics();
    syncModelConfig();
    costModel.start();
    instrumentationRequested.set(costModel.mode() == OwnerCostMode.ALWAYS);
    requestReconciliation();
  }

  @Override
  public void stop() {
    active.set(false);
    instrumentationRequested.set(false);
    costModel.stop();
    lifecycleGeneration.incrementAndGet();
    uninstallOwner(instrumentationOwner);
  }

  @Override
  public void postStart() {

  }

  @Override
  public void markSamplerActivity() {
    syncModelConfig();
    costModel.markActivity();
  }

  public OwnerCostModel getCostModel() {
    return costModel;
  }

  @Override
  public OwnerCostModel ownerCostModel() {
    return costModel;
  }

  @Override
  public Map<String, String> ownerCatalog() {
    Map<String, String> owners = new TreeMap<>();
    PluginManager plugins = Bukkit.getPluginManager();
    if (plugins != null) {
      for (Plugin plugin : plugins.getPlugins()) {
        if (plugin != null && plugin != React.instance && plugin.getName() != null && !plugin.getName().isBlank()) {
          owners.put(plugin.getName(), plugin.getName());
        }
      }
    }
    return owners;
  }

  public long getLastSamplerActivity() {
    return costModel.lastActivityMs();
  }

  public void setLastSamplerActivity(long lastSamplerActivity) {
    costModel.lastActivityMs(lastSamplerActivity);
  }

  public long getDutyWindowStartMs() {
    return costModel.dutyWindowStartMs();
  }

  public void setDutyWindowStartMs(long dutyWindowStartMs) {
    costModel.dutyWindowStartMs(dutyWindowStartMs);
  }

  @Override
  public boolean isMeasuring() {
    return costModel.measuring();
  }

  @Override
  public double getEventTimeMsPerSecond() {
    return costModel.totalMsPerSecond();
  }

  public double getPluginEventTimeMsPerSecond(String pluginName) {
    return costModel.msPerSecond(pluginName);
  }

  public double getPluginEventTimeMS(String pluginName) {
    return costModel.ms(pluginName);
  }

  public Map<String, Double> snapshotPluginEventTimeMS() {
    return costModel.snapshotMs();
  }

  public Map<String, Integer> snapshotPluginEventCalls() {
    return costModel.snapshotCalls();
  }

  @Override
  public int getListenerCount() {
    return listenerCount;
  }

  public double getTotalTime() {
    return costModel.totalMs();
  }

  public int getCalls() {
    return saturatingInt(costModel.calls());
  }

  public int getAsyncCalls() {
    return saturatingInt(costModel.asyncCalls());
  }

  @Override
  public double getCallsPerTick() {
    return callsPerTick;
  }

  @Override
  public double getWindowSeconds() {
    return costModel.windowSeconds();
  }

  @Override
  public long getWindowPublishedAtMs() {
    return costModel.publishedAtMs();
  }

  @Override
  public void onTick() {
    if (!active.get()) {
      return;
    }

    syncModelConfig();
    costModel.applyMode();
  }

  @Override
  public boolean available() {
    return resolveReflection();
  }

  @Override
  public String unavailableReason() {
    return available() ? "" : "Bukkit event handler lists could not be instrumented";
  }

  @Override
  public void setActive(boolean wanted) {
    if (!active.get()) {
      instrumentationRequested.set(false);
      return;
    }

    instrumentationRequested.set(wanted);
    if (!wanted && !spiesInjected && cleanupGeneration == 0L) {
      return;
    }

    requestReconciliation();
  }

  @Override
  public boolean active() {
    return spiesInjected;
  }

  @Override
  public void drain(OwnerCostConsumer consumer) {
    Map<String, long[]> drained;
    synchronized (pendingDrain) {
      if (pendingDrain.isEmpty()) {
        return;
      }
      drained = new HashMap<>(pendingDrain);
      pendingDrain.clear();
    }

    String selfName = React.instance == null ? null : React.instance.getName();
    for (Map.Entry<String, long[]> entry : drained.entrySet()) {
      if (!rankedOwner(entry.getKey(), selfName)) {
        continue;
      }
      long[] counters = entry.getValue();
      consumer.accept(entry.getKey(), entry.getKey(), OwnerCostProbe.KIND_EVENT, counters[0], counters[1], counters[2]);
    }
  }

  static boolean rankedOwner(String owner, String selfName) {
    return owner != null && !owner.equals(selfName);
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

  private void syncModelConfig() {
    costModel.mode(instrumentation);
    costModel.activityWindowMs(samplerActivityWindowMS);
    costModel.dutyOnMs(dutyCycleOnMS);
    costModel.dutyPeriodMs(dutyCyclePeriodMS);
    costModel.freshnessMs(3L * Math.max(1000L, getTinterval()));
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
    totals.calls += snapshot.calls();
    long[] counters = totals.owners.computeIfAbsent(naughty.pluginName, ignored -> new long[3]);
    counters[0] += snapshot.timeNanos();
    counters[1] += snapshot.calls();
    counters[2] += snapshot.asyncCalls();
  }

  private void beginWindow() {
    costModel.beginWindow();
    measuredTicks.set(0L);
  }

  private void publishWindow(WindowTotals totals) {
    listenerCount = totals.listeners;
    callsPerTick = averageCallsPerTick(totals.calls, measuredTicks.getAndSet(0L));
    synchronized (pendingDrain) {
      for (Map.Entry<String, long[]> entry : totals.owners.entrySet()) {
        long[] pending = pendingDrain.computeIfAbsent(entry.getKey(), ignored -> new long[3]);
        long[] drained = entry.getValue();
        pending[0] += drained[0];
        pending[1] += drained[1];
        pending[2] += drained[2];
      }
    }
    costModel.collect();
  }

  private void clearPublishedMetrics() {
    listenerCount = 0;
    callsPerTick = 0D;
    measuredTicks.set(0L);
    synchronized (pendingDrain) {
      pendingDrain.clear();
    }
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

  static double averageCallsPerTick(long calls, long ticks) {
    if (calls <= 0L || ticks <= 0L) {
      return 0D;
    }

    return (double) calls / ticks;
  }

  private static final class WindowTotals {
    private final Map<String, long[]> owners = new HashMap<>();
    private int listeners;
    private long calls;
  }
}
