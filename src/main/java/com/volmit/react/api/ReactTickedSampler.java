package com.volmit.react.api;

import com.volmit.react.React;
import com.volmit.react.util.ChronoLatch;
import com.volmit.react.util.RollingSequence;
import com.volmit.react.util.tick.TickedObject;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ReactTickedSampler extends TickedObject implements Sampler {
    private final RollingSequence sequence;
    private final AtomicLong lastSample;
    private boolean sleeping;
    private final long activeInterval;

    public ReactTickedSampler(String id, long tickInterval, int memory) {
        super("sampler", id, tickInterval);
        this.sequence = new RollingSequence(memory);
        this.activeInterval = tickInterval;
        this.lastSample = new AtomicLong(0);
        this.sleeping = true;
    }

    @Override
    public void start() {
        React.instance.getTicker().register(this);
    }

    public void stop() {
        unregister();
    }

    public abstract double onSample();

    @Override
    public double sample() {
        lastSample.set(Math.ms());

        if(sleeping) {
            setSleeping(false);
        }

        return sequence.getAverage();
    }

    @Override
    public abstract String format(double t);

    private void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
        setInterval(sleeping ? 1000 : activeInterval);
        if(!this.sleeping && sleeping) {
            sequence.resetExtremes();
        }
    }

    @Override
    public void onTick() {
        if(sleeping) {
            return;
        }

        if(Math.ms() - lastSample.get() > 1000) {
            setSleeping(true);
        }

        sequence.put(onSample());
    }

    public String getId() {
        return super.getId();
    }
}
