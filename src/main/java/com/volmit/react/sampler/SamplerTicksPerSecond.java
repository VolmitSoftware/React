package com.volmit.react.sampler;

import com.sun.jna.platform.unix.X11;
import com.volmit.react.api.ReactTickedSampler;
import com.volmit.react.util.Form;
import com.volmit.react.util.J;
import com.volmit.react.util.M;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SamplerTicksPerSecond extends ReactTickedSampler {
    private final AtomicInteger ticks;
    private final AtomicLong lastTick;
    private final AtomicLong lastTickDuration;
    private final int stickid;

    public SamplerTicksPerSecond() {
        super("ticks-per-second", 50, 7);
        this.ticks = new AtomicInteger(0);
        this.lastTickDuration = new AtomicLong(50);
        this.lastTick = new AtomicLong(Math.ms());
        stickid = J.sr(this::onSyncTick, 0);
    }

    private void onSyncTick() {
        lastTick.set(Math.ms());
        ticks.incrementAndGet();
    }

    @Override
    public double onSample() {
        i(sampleFormatted());
        lastTickDuration.set(Math.ms() - lastTick.get());
        return 1000D / Math.max(50D, (double)lastTickDuration.get());
    }

    @Override
    public String format(double t) {
        long dur = Math.ms() - lastTick.get();

        if(dur > 3000) {
            return Form.duration(dur, 0);
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
