package com.volmit.react.api.sampler;

import art.arcane.chrono.RollingSequence;
import com.volmit.react.React;
import com.volmit.react.util.scheduling.TickedObject;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ReactTickedSampler extends TickedObject implements Sampler {
    private transient final RollingSequence ssequence;
    private transient final AtomicLong slastSample;
    private transient final long sactiveInterval;
    private transient boolean ssleeping;

    public ReactTickedSampler(String id, long tickInterval, int memory) {
        super("sampler", id, tickInterval);
        this.ssequence = new RollingSequence(memory);
        this.sactiveInterval = tickInterval;
        this.slastSample = new AtomicLong(0);
        this.ssleeping = true;
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
        slastSample.set(System.currentTimeMillis());

        if (ssleeping) {
            setSsleeping(false);
        }

        return ssequence.getAverage();
    }

    private void setSsleeping(boolean ssleeping) {
        this.ssleeping = ssleeping;
        setTinterval(ssleeping ? 1000 : sactiveInterval);
        if (!this.ssleeping && ssleeping) {
            ssequence.resetExtremes();
        }
    }

    @Override
    public void onTick() {
        if (ssleeping) {
            return;
        }

        if (System.currentTimeMillis() - slastSample.get() > 1000) {
            setSsleeping(true);
        }

        try {
            ssequence.put(onSample());
        }

        catch(Throwable e) {

        }
    }

    public String getId() {
        return super.getTid();
    }
}
