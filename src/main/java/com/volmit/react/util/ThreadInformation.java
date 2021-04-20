package com.volmit.react.util;

public class ThreadInformation {
    private final Average ticksPerSecondAverage;
    private final int id;
    private double ticksPerSecond;
    private int queuedSize;
    private boolean processing;
    private double utilization;
    private long tick;

    public ThreadInformation(int id) {
        this.id = id;
        utilization = 0;
        ticksPerSecond = 0;
        queuedSize = 0;
        processing = false;
        ticksPerSecondAverage = new Average(20);
        tick = TICK.tick;
    }

    public double getTicksPerSecond() {
        return ticksPerSecond;
    }

    public void setTicksPerSecond(double ticksPerSecond) {
        this.ticksPerSecond = ticksPerSecond;
        ticksPerSecondAverage.put(ticksPerSecond);
    }

    public int getQueuedSize() {
        return queuedSize;
    }

    public void setQueuedSize(int queuedSize) {
        this.queuedSize = queuedSize;
    }

    public boolean isProcessing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public double getUtilization() {
        return utilization;
    }

    public void setUtilization(double utilization) {
        this.utilization = utilization;
    }

    public double getTicksPerSecondAverage() {
        return ticksPerSecondAverage.getAverage();
    }

    public long getTick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public long getTickLag() {
        return TICK.tick - getTick();
    }

    public int getId() {
        return id;
    }
}
