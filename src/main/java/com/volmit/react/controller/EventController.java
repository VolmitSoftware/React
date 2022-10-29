package com.volmit.react.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.event.NaughtyRegisteredListener;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.sampler.SamplerUnknown;
import com.volmit.react.util.IController;
import com.volmit.react.util.J;
import lombok.Data;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class EventController implements IController {
    private int listenerCount;
    private double totalTime;
    private int calls;
    private AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public String getName() {
        return "Event";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        pullOut();
    }

    @Override
    public void tick() {
        updateHandlerListInjections();
    }

    public void updateHandlerListInjections() {
        if(running.get()) {
            return;
        }

        running.set(true);

        React.burst.lazy(() -> {
            totalTime = 0;
            calls = 0;
            int m = 0;
            int inj = 0;
            ArrayList<HandlerList> h = Curse.on(HandlerList.class).get("allLists");

            for(HandlerList i : h) {
                RegisteredListener[] r = Curse.on(i).get("handlers");
                EnumMap<EventPriority, ArrayList<RegisteredListener>> map = Curse.on(i).get("handlerslots");
                if(r != null) {
                    for(int j = 0; j < r.length; j++) {
                        if(!(r[j] instanceof NaughtyRegisteredListener)) {
                            r[j] = new NaughtyRegisteredListener(r[j].getListener(), r[j].jailbreak().executor,
                                r[j].getPriority(), r[j].getPlugin(), r[j].isIgnoringCancelled());
                            inj++;
                        }
                        else {
                            totalTime += ((NaughtyRegisteredListener) r[j]).time;
                            ((NaughtyRegisteredListener) r[j]).time = 0;
                            calls += ((NaughtyRegisteredListener) r[j]).calls;
                            ((NaughtyRegisteredListener) r[j]).calls = 0;
                        }
                    }

                    m += r.length;
                }

                if(map != null) {
                    for(ArrayList<RegisteredListener> j : map.values()) {
                        for(int k = 0; k < j.size(); k++) {
                            if(!(j.get(k) instanceof NaughtyRegisteredListener)) {
                                j.set(k, new NaughtyRegisteredListener(j.get(k).getListener(), j.get(k).jailbreak().executor,
                                    j.get(k).getPriority(), j.get(k).getPlugin(), j.get(k).isIgnoringCancelled()));
                                inj++;
                            }
                            else {
                                totalTime += ((NaughtyRegisteredListener)j.get(k)).time;
                                ((NaughtyRegisteredListener)j.get(k)).time = 0;
                                calls += ((NaughtyRegisteredListener)j.get(k)).calls;
                                ((NaughtyRegisteredListener)j.get(k)).calls = 0;
                            }
                        }

                        m += j.size();
                    }
                }
            }

            if(inj > 0)
            {
                React.verbose("Injected " + inj + " event listener spies.");
            }

            listenerCount = m;
            running.set(false);
        });
    }

    public void pullOut() {
        int out = 0;
        ArrayList<HandlerList> h = Curse.on(HandlerList.class).get("allLists");

        for(HandlerList i : h) {
            RegisteredListener[] r = Curse.on(i).get("handlers");
            EnumMap<EventPriority, ArrayList<RegisteredListener>> map = Curse.on(i).get("handlerslots");
            if(r != null) {
                for(int j = 0; j < r.length; j++) {
                    if((r[j] instanceof NaughtyRegisteredListener)) {
                        r[j] = new RegisteredListener(r[j].getListener(), r[j].jailbreak().executor,
                            r[j].getPriority(), r[j].getPlugin(), r[j].isIgnoringCancelled());
                        out++;
                    }
                }
            }

            if(map != null) {
                for(ArrayList<RegisteredListener> j : map.values()) {
                    for(int k = 0; k < j.size(); k++) {
                        if((j.get(k) instanceof NaughtyRegisteredListener)) {
                            j.set(k, new RegisteredListener(j.get(k).getListener(), j.get(k).jailbreak().executor,
                                j.get(k).getPriority(), j.get(k).getPlugin(), j.get(k).isIgnoringCancelled()));
                            out++;
                        }
                    }
                }
            }
        }

        React.verbose("Pulled out " + out + " event listener spies.");
    }

    @Override
    public int getTickInterval() {
        return 0;
    }

    @Override
    public void l(Object l) {

    }

    @Override
    public void v(Object l) {

    }
}
