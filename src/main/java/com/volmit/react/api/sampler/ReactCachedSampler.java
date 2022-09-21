package com.volmit.react.api.sampler;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.util.ChronoLatch;

public abstract class ReactCachedSampler implements Sampler {
    private final ChronoLatch latch;
    private final AtomicDouble last;
    private final String id;

    public ReactCachedSampler(String id, long sampleDelay) {
        this.id = id;
        this.latch = new ChronoLatch(sampleDelay, true);
        this.last = new AtomicDouble();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    public abstract double onSample();

    @Override
    public double sample() {
        double t = last.get();

        if(latch.flip()) {
            t = onSample();
            last.set(t);
        }

        return t;
    }

    public String getId() {
        return id;
    }
}
