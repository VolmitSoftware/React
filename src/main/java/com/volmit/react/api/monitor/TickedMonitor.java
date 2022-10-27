package com.volmit.react.api.monitor;

import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.PrecisionStopwatch;
import com.volmit.react.util.tick.TickedObject;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class TickedMonitor extends TickedObject implements Monitor {
    protected final Map<Sampler, Double> samplers;
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

    public void clearVisibility()
    {
        for(Sampler i : visible.keySet()) {
            visible.put(i, false);
        }
    }

    @Override
    public void onTick() {
        boolean flushable = false;

        for(Sampler i : visible.keySet()) {
            Double old = getSamplers().put(i, i.sample());

            if(old == null || old != i.sample()) {
                flushable = true;
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
