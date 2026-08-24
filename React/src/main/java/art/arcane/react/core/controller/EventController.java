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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
public class EventController extends TickedObject implements IController, Listener {
  private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();
  private static volatile Field allListsField;
  private static volatile Field handlersField;
  private static volatile Field handlerSlotsField;
  private static volatile Field executorField;
  private static volatile boolean handlerFieldsResolved;
  private transient volatile int listenerCount;
  private transient volatile double totalTime;
  private transient volatile int calls;
  private transient volatile Map<String, Double> pluginEventTimeMS = Map.of();
  private transient volatile Map<String, Integer> pluginEventCalls = Map.of();
  private transient final AtomicBoolean running = new AtomicBoolean(false);
  private transient final AtomicBoolean active = new AtomicBoolean(false);
  private transient final AtomicBoolean instrumentationRequested = new AtomicBoolean(false);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicLong mutationRevision = new AtomicLong();
  private transient volatile boolean spiesInjected = false;
  private transient volatile long lastSamplerActivity = 0;
  private transient volatile long instrumentationOwner;
  private transient volatile long cleanupGeneration;
  private long samplerActivityWindowMS = 15000;

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
    instrumentationRequested.set(false);
    spiesInjected = false;
    lastSamplerActivity = 0;
    clearPublishedMetrics();
    requestReconciliation();
  }

  @Override
  public void stop() {
    active.set(false);
    instrumentationRequested.set(false);
    lifecycleGeneration.incrementAndGet();
    requestReconciliation();
  }

  public void markSamplerActivity() {
    lastSamplerActivity = System.currentTimeMillis();
  }

  @Override
  public void postStart() {

  }

  @Override
  public void onTick() {
    if (!active.get()) {
      return;
    }

    if (System.currentTimeMillis() - lastSamplerActivity > samplerActivityWindowMS) {
      instrumentationRequested.set(false);
      requestReconciliation();
      return;
    }

    instrumentationRequested.set(true);
    updateHandlerListInjections();
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

  private static boolean resolveHandlerFields() {
    if (handlerFieldsResolved) {
      return allListsField != null;
    }

    synchronized (EventController.class) {
      if (handlerFieldsResolved) {
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
        allListsField = allLists;
        handlersField = handlers;
        handlerSlotsField = handlerSlots;
        executorField = executor;
      } catch (Throwable e) {
        React.reportError("Failed to resolve Bukkit event-handler instrumentation fields", e);
      }

      handlerFieldsResolved = true;
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

  public void updateHandlerListInjections() {
    if (!active.get()) {
      return;
    }

    instrumentationRequested.set(true);
    requestReconciliation();
  }

  public void pullOut() {
    instrumentationRequested.set(false);
    requestReconciliation();
  }

  public Map<String, Double> snapshotPluginEventTimeMS() {
    return new HashMap<>(pluginEventTimeMS);
  }

  public Map<String, Integer> snapshotPluginEventCalls() {
    return new HashMap<>(pluginEventCalls);
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
    if (!resolveHandlerFields()) {
      return;
    }

    long generation = lifecycleGeneration.get();
    long owner = instrumentationOwner;
    if (!active.get()) {
      uninstallHandlers(owner, false);
      spiesInjected = false;
      return;
    }

    if (cleanupGeneration == generation) {
      uninstallHandlers(owner, true);
      if (cleanupGeneration == generation) {
        cleanupGeneration = 0L;
      }
    }

    if (!active.get() || generation != lifecycleGeneration.get() || owner != instrumentationOwner) {
      return;
    }

    if (!instrumentationRequested.get()
        || System.currentTimeMillis() - lastSamplerActivity > samplerActivityWindowMS) {
      instrumentationRequested.set(false);
      uninstallHandlers(owner, false);
      spiesInjected = false;
      clearPublishedMetrics();
      return;
    }

    WindowTotals totals = new WindowTotals();
    int listeners = instrumentAndDrain(owner, totals);
    publishWindow(listeners, totals);
    spiesInjected = true;
  }

  private int instrumentAndDrain(long owner, WindowTotals totals) {
    int listeners = 0;
    ArrayList<HandlerList> handlerLists = new ArrayList<>(allHandlerLists());
    for (HandlerList handlerList : handlerLists) {
      try {
        listeners += instrumentHandlerList(handlerList, owner, totals);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
    return listeners;
  }

  private int instrumentHandlerList(HandlerList handlerList, long owner, WindowTotals totals) {
    EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = handlerSlotsOf(handlerList);
    if (slots == null) {
      return 0;
    }

    int listeners = 0;
    boolean changed = false;
    try {
      for (ArrayList<RegisteredListener> priorityListeners : slots.values()) {
        for (int index = 0; index < priorityListeners.size(); index++) {
          RegisteredListener registered = priorityListeners.get(index);
          if (registered == null) {
            continue;
          }

          listeners++;
          if (registered instanceof NaughtyRegisteredListener naughty) {
            if (naughty.isOwnedBy(owner)) {
              drainListener(naughty, totals);
              continue;
            }
            naughty.drainCounters();
          }

          priorityListeners.set(index, instrument(registered, owner));
          changed = true;
        }
      }
      return listeners;
    } finally {
      if (changed) {
        rebake(handlerList);
      }
    }
  }

  private NaughtyRegisteredListener instrument(RegisteredListener registered, long owner) {
    return new NaughtyRegisteredListener(
        registered.getListener(),
        executorOf(registered),
        registered.getPriority(),
        registered.getPlugin(),
        registered.isIgnoringCancelled(),
        owner
    );
  }

  private void uninstallHandlers(long owner, boolean allOwners) {
    int removed = 0;
    ArrayList<HandlerList> handlerLists = new ArrayList<>(allHandlerLists());
    for (HandlerList handlerList : handlerLists) {
      try {
        removed += uninstallHandlerList(handlerList, owner, allOwners);
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
    if (removed > 0) {
      React.verbose("Pulled out " + removed + " event listener spies.");
    }
  }

  private int uninstallHandlerList(HandlerList handlerList, long owner, boolean allOwners) {
    EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = handlerSlotsOf(handlerList);
    if (slots == null) {
      return 0;
    }

    int removed = 0;
    try {
      for (ArrayList<RegisteredListener> priorityListeners : slots.values()) {
        for (int index = 0; index < priorityListeners.size(); index++) {
          RegisteredListener registered = priorityListeners.get(index);
          if (!(registered instanceof NaughtyRegisteredListener naughty)
              || (!allOwners && !naughty.isOwnedBy(owner))) {
            continue;
          }

          naughty.drainCounters();
          priorityListeners.set(index, restore(registered));
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

  private RegisteredListener restore(RegisteredListener registered) {
    return new RegisteredListener(
        registered.getListener(),
        executorOf(registered),
        registered.getPriority(),
        registered.getPlugin(),
        registered.isIgnoringCancelled()
    );
  }

  private void drainListener(NaughtyRegisteredListener naughty, WindowTotals totals) {
    NaughtyRegisteredListener.CounterSnapshot snapshot = naughty.drainCounters();
    double listenerTime = snapshot.timeNanos() / 1.0E6D;
    int listenerCalls = saturatingInt(snapshot.calls());
    totals.timeNanos += snapshot.timeNanos();
    totals.calls += snapshot.calls();
    totals.pluginTime.merge(naughty.pluginName, listenerTime, Double::sum);
    totals.pluginCalls.merge(naughty.pluginName, listenerCalls, EventController::saturatingAdd);
  }

  private void publishWindow(int listeners, WindowTotals totals) {
    listenerCount = listeners;
    totalTime = totals.timeNanos / 1.0E6D;
    calls = saturatingInt(totals.calls);
    pluginEventTimeMS = Map.copyOf(totals.pluginTime);
    pluginEventCalls = Map.copyOf(totals.pluginCalls);
  }

  private void clearPublishedMetrics() {
    listenerCount = 0;
    totalTime = 0D;
    calls = 0;
    pluginEventTimeMS = Map.of();
    pluginEventCalls = Map.of();
  }

  private boolean runOnAuthoritativeScheduler(Runnable mutation) {
    try {
      boolean correctThread = J.isFoliaThreading()
          ? Bukkit.isGlobalTickThread()
          : Bukkit.isPrimaryThread();
      if (correctThread) {
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

  private static final class WindowTotals {
    private final Map<String, Double> pluginTime = new HashMap<>();
    private final Map<String, Integer> pluginCalls = new HashMap<>();
    private long timeNanos;
    private long calls;
  }
}
