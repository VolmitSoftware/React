package com.volmit.react.util;

public class TM {
    private double fromNs;
    private double fromMs;

    public TM() {
        set();
    }

    public void set() {
        fromMs = M.ms();
        fromNs = M.ns();
    }

    public double markReset() {
        double m = mark();
        set();

        return m;
    }

    public double mark() {
        double nanoTime = ((double) M.ns() - fromNs);
        double millTime = ((double) M.ms() - fromMs);
        double hmil = nanoTime / 1000000D;

        if (Math.abs(hmil - millTime) > 1.25D) {
            return millTime;
        }

        return hmil;
    }
}
