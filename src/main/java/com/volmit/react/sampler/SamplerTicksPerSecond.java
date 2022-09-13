package com.volmit.react.sampler;

import com.volmit.react.api.ReactTickedSampler;
import com.volmit.react.util.Form;
import com.volmit.react.util.J;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SamplerTicksPerSecond extends ReactTickedSampler {
    public static final String ID = "ticks-per-second";
    private final AtomicInteger ticks;
    private final AtomicLong lastTick;
    private final AtomicLong lastTickDuration;
    private final AtomicLong lastTickDurationSync;
    private final int stickid;

    public SamplerTicksPerSecond() {
        super(ID, 50, 7);
        this.ticks = new AtomicInteger(0);
        this.lastTickDuration = new AtomicLong(50);
        this.lastTickDurationSync = new AtomicLong(50);
        this.lastTick = new AtomicLong(Math.ms());
        stickid = J.sr(this::onSyncTick, 0);
    }

    private void onSyncTick() {
        lastTick.set(Math.ms());
        ticks.incrementAndGet();
        lastTickDurationSync.set(Math.ms() - lastTick.get());
    }

    @Override
    public double onSample() {
        lastTickDuration.set(Math.ms() - lastTick.get());
        return 1000D / Math.max(50D, Math.max((double)lastTickDuration.get(), (double)lastTickDurationSync.get()));
    }

    @Override
    public String format(double t) {
        long dur = Math.ms() - lastTick.get();

        if(dur > 3000) {
            return Form.duration(dur, 1);
        }

        if(t > 19.85) {
            return "20 TPS";
        }

        return Form.f(t, 2) + " TPS";
    }

    @Override
    public void unregister() {
        J.csr(stickid);
        super.unregister();
    }
}
