package com.volmit.react.api;

import com.volmit.react.React;
import com.volmit.react.util.ChronoLatch;
import com.volmit.react.util.RollingSequence;
import com.volmit.react.util.tick.TickedObject;

import java.util.concurrent.atomic.AtomicReference;

public abstract class ReactTickedSampler extends TickedObject implements Sampler {
    private final RollingSequence sequence;

    public ReactTickedSampler(String id, long tickInterval, int memory) {
        super("sampler", id, tickInterval);
        this.sequence = new RollingSequence(memory);
        React.instance.getTicker().register(this);
    }

    public abstract double onSample();

    @Override
    public double sample() {
        return sequence.getAverage();
    }

    @Override
    public abstract String format(double t);

    @Override
    public void onTick() {
        sequence.put(onSample());
    }

    public String getId() {
        return super.getId();
    }
}
