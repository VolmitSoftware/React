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

import art.arcane.curse.Curse;
import art.arcane.react.React;
import art.arcane.react.api.event.NaughtyRegisteredListener;
import art.arcane.react.api.event.layer.MinecartSpawnEvent;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.common.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class EventController extends TickedObject implements IController, Listener {
  private transient int listenerCount;
  private transient double totalTime;
  private transient int calls;
  private transient Map<String, Double> pluginEventTimeMS = new ConcurrentHashMap<>();
  private transient Map<String, Integer> pluginEventCalls = new ConcurrentHashMap<>();
  private transient AtomicBoolean running = new AtomicBoolean(false);
  private transient boolean spiesInjected = false;
  private transient long lastSamplerActivity = 0;
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
    pullOut();
    spiesInjected = false;
    lastSamplerActivity = 0;
  }

  @Override
  public void stop() {
    pullOut();
    spiesInjected = false;
  }

  public void markSamplerActivity() {
    lastSamplerActivity = System.currentTimeMillis();
  }

  @Override
  public void postStart() {

  }

  @Override
  public void onTick() {
    if (running.get()) {
      return;
    }

    if (System.currentTimeMillis() - lastSamplerActivity > samplerActivityWindowMS) {
      if (spiesInjected) {
        pullOut();
        spiesInjected = false;
      }

      listenerCount = 0;
      totalTime = 0;
      calls = 0;
      pluginEventTimeMS.clear();
      pluginEventCalls.clear();
      return;
    }

    spiesInjected = true;
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

  public void updateHandlerListInjections() {
    if (running.get()) {
      return;
    }

    running.set(true);

    React.burst.lazy(() -> {
      try {
        totalTime = 0;
        calls = 0;
        int m = 0;
        Map<String, Double> pluginTime = new HashMap<>();
        Map<String, Integer> pluginCalls = new HashMap<>();
        ArrayList<HandlerList> h = new ArrayList<>(Curse.on(HandlerList.class).get("allLists"));

        for (HandlerList i : h) {
          RegisteredListener[] r = Curse.on(i).get("handlers");
          EnumMap<EventPriority, ArrayList<RegisteredListener>> map = Curse.on(i).get("handlerslots");
          if (r != null) {
            for (int j = 0; j < r.length; j++) {
              if (!(r[j] instanceof NaughtyRegisteredListener)) {
                r[j] = new NaughtyRegisteredListener(r[j].getListener(), Curse.on(r[j]).get("executor"),
                    r[j].getPriority(), r[j].getPlugin(), r[j].isIgnoringCancelled());
              } else {
                NaughtyRegisteredListener naughty = (NaughtyRegisteredListener) r[j];
                String pluginName = pluginName(naughty);
                double listenerTime = naughty.time;
                int listenerCalls = naughty.calls;
                totalTime += listenerTime;
                calls += listenerCalls;
                pluginTime.merge(pluginName, listenerTime, Double::sum);
                pluginCalls.merge(pluginName, listenerCalls, Integer::sum);
                naughty.time = 0;
                naughty.calls = 0;
              }
            }

            m += r.length;
          }

          if (map != null) {
            for (ArrayList<RegisteredListener> j : map.values()) {
              for (int k = 0; k < j.size(); k++) {
                if (!(j.get(k) instanceof NaughtyRegisteredListener)) {
                  j.set(k, new NaughtyRegisteredListener(j.get(k).getListener(), Curse.on(j.get(k)).get("executor"),
                      j.get(k).getPriority(), j.get(k).getPlugin(), j.get(k).isIgnoringCancelled()));
                } else {
                  NaughtyRegisteredListener naughty = (NaughtyRegisteredListener) j.get(k);
                  String pluginName = pluginName(naughty);
                  double listenerTime = naughty.time;
                  int listenerCalls = naughty.calls;
                  totalTime += listenerTime;
                  calls += listenerCalls;
                  pluginTime.merge(pluginName, listenerTime, Double::sum);
                  pluginCalls.merge(pluginName, listenerCalls, Integer::sum);
                  naughty.time = 0;
                  naughty.calls = 0;
                }
              }

              m += j.size();
            }
          }
        }

        listenerCount = m;
        pluginEventTimeMS.clear();
        pluginEventTimeMS.putAll(pluginTime);
        pluginEventCalls.clear();
        pluginEventCalls.putAll(pluginCalls);
      } finally {
        running.set(false);
      }
    });
  }

  public void pullOut() {
    int out = 0;
    ArrayList<HandlerList> h = new ArrayList<>(Curse.on(HandlerList.class).get("allLists"));

    for (HandlerList i : h) {
      RegisteredListener[] r = Curse.on(i).get("handlers");
      EnumMap<EventPriority, ArrayList<RegisteredListener>> map = Curse.on(i).get("handlerslots");
      if (r != null) {
        for (int j = 0; j < r.length; j++) {
          if ((r[j] instanceof NaughtyRegisteredListener)) {
            r[j] = new RegisteredListener(r[j].getListener(), Curse.on(r[j]).get("executor"),
                r[j].getPriority(), r[j].getPlugin(), r[j].isIgnoringCancelled());
            out++;
          }
        }
      }

      if (map != null) {
        for (ArrayList<RegisteredListener> j : map.values()) {
          for (int k = 0; k < j.size(); k++) {
            if ((j.get(k) instanceof NaughtyRegisteredListener)) {
              j.set(k, new RegisteredListener(j.get(k).getListener(), Curse.on(j.get(k)).get("executor"),
                  j.get(k).getPriority(), j.get(k).getPlugin(), j.get(k).isIgnoringCancelled()));
              out++;
            }
          }
        }
      }
    }

    React.verbose("Pulled out " + out + " event listener spies.");
  }

  public Map<String, Double> snapshotPluginEventTimeMS() {
    return new HashMap<>(pluginEventTimeMS);
  }

  public Map<String, Integer> snapshotPluginEventCalls() {
    return new HashMap<>(pluginEventCalls);
  }

  private String pluginName(RegisteredListener listener) {
    if (listener == null || listener.getPlugin() == null || listener.getPlugin().getName() == null) {
      return "Unknown";
    }

    String plugin = listener.getPlugin().getName().trim();
    return plugin.isBlank() ? "Unknown" : plugin;
  }
}
