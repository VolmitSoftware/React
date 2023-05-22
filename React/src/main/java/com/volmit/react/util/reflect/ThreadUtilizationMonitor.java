package com.volmit.react.util.reflect;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.RollingSequence;
import com.volmit.react.util.math.M;

public class ThreadUtilizationMonitor extends Thread {
    private final Thread target;
    private final RollingSequence average;
    private long lastAccess;
    private boolean active;

    public ThreadUtilizationMonitor(Thread target, int memory) {
        super("React Monitor[" + target.getName() + "]");
        setPriority(Thread.MAX_PRIORITY);
        this.target = target;
        this.average = new RollingSequence(memory);
        this.lastAccess = M.ms();
        active = true;
    }

    public double getAverage() {
        lastAccess = M.ms();
        return average.getAverage();
    }

    public void run() {
        boolean active = false;
        boolean lastActive = false;
        PrecisionStopwatch p = PrecisionStopwatch.start();

        while (!isInterrupted()) {
            lastActive = active;
            active = target.getState() == State.RUNNABLE;

            if (lastActive != active) {
                if (active) {
                    p.reset();
                    p.begin();
                } else {
                    average.put(p.getMilliseconds());
                }
            }

            try {
                Thread.sleep(M.ms() - lastAccess > 1000 ? 50 : 0);
            } catch (Throwable e) {
                break;
            }
        }
    }
}
