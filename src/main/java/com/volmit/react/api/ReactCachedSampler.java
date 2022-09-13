package com.volmit.react.api;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.util.ChronoLatch;

public abstract class ReactCachedSampler implements Sampler {
    private final ChronoLatch latch;
    private final AtomicDouble last;

    public ReactCachedSampler(long sampleDelay) {
        this.latch = new ChronoLatch(sampleDelay, true);
        this.last = new AtomicDouble();
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
}
