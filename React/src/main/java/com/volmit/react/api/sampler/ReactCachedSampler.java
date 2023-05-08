package com.volmit.react.api.sampler;

import art.arcane.chrono.ChronoLatch;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class ReactCachedSampler implements Sampler {
    private transient final ChronoLatch slatch;
    private transient final AtomicDouble slast;
    private transient final String sid;

    public ReactCachedSampler(String id, long sampleDelay) {
        this.sid = id;
        this.slatch = new ChronoLatch(sampleDelay, true);
        this.slast = new AtomicDouble();
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
        double t = slast.get();

        if (slatch.flip()) {
            t = onSample();
            slast.set(t);
        }

        return t;
    }

    public String getId() {
        return sid;
    }
}
