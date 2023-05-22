package com.volmit.react.core.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.event.NaughtyRegisteredListener;
import com.volmit.react.api.event.layer.MinecartSpawnEvent;
import com.volmit.react.api.event.layer.ServerTickEvent;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
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
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class EventController extends TickedObject implements IController, Listener {
    private transient int listenerCount;
    private transient double totalTime;
    private transient int calls;
    private transient final ServerTickEvent ste = new ServerTickEvent();
    private transient AtomicBoolean running = new AtomicBoolean(false);
    private transient int steTicker;

    public EventController() {
        super("react", "event", 50);
        start();
    }

    @Override
    public String getName() {
        return "Event";
    }

    @Override
    public void start() {
        steTicker = J.sr(() -> Bukkit.getPluginManager().callEvent(ste), 0);
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
        pullOut();
        J.csr(steTicker);
    }

    @Override
    public void postStart() {

    }

    @Override
    public void onTick() {
        updateHandlerListInjections();
    }

    public void call(Event event) {
        Bukkit.getServer().getPluginManager().callEvent(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(BlockDispenseEvent e) {
        if(e.getItem().getType().equals(Material.MINECART)
            ||e.getItem().getType().equals(Material.CHEST_MINECART)
            ||e.getItem().getType().equals(Material.TNT_MINECART)
            ||e.getItem().getType().equals(Material.HOPPER_MINECART)
            ||e.getItem().getType().equals(Material.FURNACE_MINECART)
            ||e.getItem().getType().equals(Material.COMMAND_BLOCK_MINECART)) {
            MinecartSpawnEvent s = new MinecartSpawnEvent(e.getBlock().getLocation());
            call(s);
            if(s.isCancelled()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityPlaceEvent e) {
        if(e.getEntityType().name().startsWith("MINECART")) {
            MinecartSpawnEvent s = new MinecartSpawnEvent(e.getEntity().getLocation(), e.getPlayer());
            call(s);

            if(s.isCancelled()) {
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
            totalTime = 0;
            calls = 0;
            int m = 0;
            int inj = 0;
            ArrayList<HandlerList> h = Curse.on(HandlerList.class).get("allLists");

            for (HandlerList i : h) {
                RegisteredListener[] r = Curse.on(i).get("handlers");
                EnumMap<EventPriority, ArrayList<RegisteredListener>> map = Curse.on(i).get("handlerslots");
                if (r != null) {
                    for (int j = 0; j < r.length; j++) {
                        if (!(r[j] instanceof NaughtyRegisteredListener)) {
                            r[j] = new NaughtyRegisteredListener(r[j].getListener(), Curse.on(r[j]).get("executor"),
                                    r[j].getPriority(), r[j].getPlugin(), r[j].isIgnoringCancelled());
                            inj++;
                        } else {
                            totalTime += ((NaughtyRegisteredListener) r[j]).time;
                            ((NaughtyRegisteredListener) r[j]).time = 0;
                            calls += ((NaughtyRegisteredListener) r[j]).calls;
                            ((NaughtyRegisteredListener) r[j]).calls = 0;
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
                                inj++;
                            } else {
                                totalTime += ((NaughtyRegisteredListener) j.get(k)).time;
                                ((NaughtyRegisteredListener) j.get(k)).time = 0;
                                calls += ((NaughtyRegisteredListener) j.get(k)).calls;
                                ((NaughtyRegisteredListener) j.get(k)).calls = 0;
                            }
                        }

                        m += j.size();
                    }
                }
            }

            if (inj > 0) {
                React.verbose("Injected " + inj + " event listener spies.");
            }

            listenerCount = m;
            running.set(false);
        });
    }

    public void pullOut() {
        int out = 0;
        ArrayList<HandlerList> h = Curse.on(HandlerList.class).get("allLists");

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
}
