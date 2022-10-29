package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.M;
import com.volmit.react.util.PrecisionStopwatch;
import com.volmit.react.util.tick.TickedObject;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class TickedMonitor extends TickedObject implements Monitor {
    protected final Map<Sampler, Double> samplers;
    protected final Map<Sampler, Double> changers;
    @Getter
    protected final Map<Sampler, Boolean> visible;
    protected long sleepingRate;
    protected int sleepDelay;
    protected int currentSleepDelay;
    private final long awakeInterval;

    public TickedMonitor(String id, long interval) {
        super("monitor", id, interval);
        this.awakeInterval = interval;
        this.visible = new HashMap<>();
        this.sleepingRate = interval * 2;
        this.sleepDelay = 35;
        this.currentSleepDelay = 20;
        this.changers = new HashMap<>();
        this.samplers = new HashMap<>();
    }

    public void wakeUp() {
        currentSleepDelay = sleepDelay;
        setInterval(awakeInterval);
    }

    public void setVisible(Sampler sampler, boolean visible) {
        this.visible.put(sampler, visible);
    }

    public void setVisible(List<Sampler> sampler, boolean visible) {
        for (Sampler i : sampler) {
            setVisible(i, visible);
        }
    }

    public void clearVisibility() {
        for(Sampler i : visible.keySet()) {
            visible.put(i, false);
        }
    }

    public double getChanger(Sampler s)
    {
        Double d = changers.get(s);
        return d == null ? 0 : d;
    }

    @Override
    public void onTick() {
        for(Sampler i : changers.k()) {
            changers.put(i, M.lerp(changers.get(i), 0, 0.1));
        }

        boolean flushable = false;

        for(Sampler i : visible.keySet()) {
            if(i == null)
            {
                continue;
            }
           if(visible.get(i) != null && visible.get(i)) {

               try
               {
                   Double old = getSamplers().put(i, i.sample());
                   double s = i.sample();
                   if(old == null || old != s) {
                       changers.put(i, M.lerp(getChanger(i), 1, 0.333));
                       flushable = true;
                   }
               }

               catch(Throwable e)
               {
                   React.error("Failed to sample " + i.getId() + " for monitor " + getId());
                   e.printStackTrace();
               }
           }
        }

        currentSleepDelay = flushable ? sleepDelay : currentSleepDelay - 1;
        setInterval(currentSleepDelay <= 0 ? sleepingRate : awakeInterval);

        if(flushable || isAlwaysFlushing()){
            flush();
        }
    }

    @Override
    public Map<Sampler, Double> getSamplers() {
        return samplers;
    }

    @Override
    public abstract void flush();
}
