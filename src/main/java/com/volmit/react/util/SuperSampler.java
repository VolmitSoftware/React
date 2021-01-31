package com.volmit.react.util;

import com.volmit.react.Config;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;

public class SuperSampler {
    private final boolean frozen;
    private final RQ r;
    private final StackTraceElement[] lockStack;
    private final GMap<Long, GList<StackTraceElement>> spikes;
    private Average ticksPerSecondL;
    private Average tickTimeL;
    private Average mahL;
    private double ticksPerSecond;
    private double ticksPerSecondRaw;
    private double tickTime;
    private double tickTimeRaw;
    private boolean running;
    private double tickUtilizationRaw;
    private double tickUtilization;
    private double leftoverTickTime;
    private long memoryUse;
    private long memoryAllocated;
    private long memoryCollected;
    private long mahs;
    private int totalChunks;
    private int totalEntities;
    private int totalDrops;
    private int totalTiles;
    private int totalLiving;
    private int chunksLoaded = 0;
    private int chunksUnloaded = 0;
    private WorldMonitor worldMonitor;
    private TPSMonitor tpsMonitor;
    private MemoryMonitor memoryMonitor;

    public SuperSampler() {
        r = new RQ();
        r.start();
        frozen = false;
        lockStack = null;
        running = false;
        ticksPerSecondL = new Average(Config.TPS_AVG_RAD);
        mahL = new Average(20);
        tickTimeL = new Average(3);
        totalChunks = 0;
        totalEntities = 0;
        totalDrops = 0;
        totalTiles = 0;
        totalLiving = 0;
        ticksPerSecondRaw = 0;
        ticksPerSecond = 0;
        tickTimeRaw = 0;
        tickTime = 0;
        tickUtilization = 0;
        tickUtilizationRaw = 0;
        memoryUse = 0;
        memoryAllocated = 0;
        memoryCollected = 0;
        chunksLoaded = 0;
        chunksUnloaded = 0;
        mahs = 0;
        spikes = new GMap<Long, GList<StackTraceElement>>();

        worldMonitor = new WorldMonitor() {
            @Override
            public void updated(int totalChunks, int totalDrops, int totalTiles, int totalLiving, int totalEntities, int chunksLoaded, int chunksUnloaded) {
                SuperSampler.this.totalChunks = totalChunks;
                SuperSampler.this.totalDrops = totalDrops;
                SuperSampler.this.totalTiles = totalTiles;
                SuperSampler.this.totalEntities = totalEntities;
                SuperSampler.this.totalLiving = totalLiving;
                SuperSampler.this.chunksLoaded = chunksLoaded;
                SuperSampler.this.chunksUnloaded = chunksUnloaded;
            }
        };

        memoryMonitor = new MemoryMonitor() {
            @Override
            public void onAllocationSet() {
                memoryUse = getMemoryUsedAfterGC();
                memoryAllocated = getMemoryAllocatedPerTick();
                memoryCollected = getMemoryCollectedPerTick();
                mahL.put(getMahs());
                mahs = (long) mahL.getAverage();
            }
        };

        tpsMonitor = new TPSMonitor(memoryMonitor, worldMonitor) {
            @Override
            public void onTicked() {
                ticksPerSecondRaw = getRawTicksPerSecond();
                tickTimeRaw = getActualTickTimeMS();
                ticksPerSecondL.put(ticksPerSecondRaw);
                tickTimeL.put(tickTimeRaw);
                ticksPerSecond = ticksPerSecondL.getAverage();
                tickTime = tickTimeL.getAverage();
                tickUtilizationRaw = tickTimeRaw / 50.0;
                tickUtilization = tickTime / 50.0;
                leftoverTickTime = 50 - tickUtilization < 0 ? 0 : 50 - tickUtilization;
                frozen = isFrozen();
            }

            @Override
            public void onSpike() {
                spikes.put(M.ms(), new GList<StackTraceElement>(getLockedStack()));
            }
        };
    }

    public void start() {
        tpsMonitor.start();
        running = true;
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        tpsMonitor.interrupt();
        tpsMonitor.close();
        running = false;
        r.interrupt();

        try {
            r.stop();
        } catch (Throwable e) {

        }

        try {
            r.stop();
        } catch (Throwable e) {

        }
    }

    public void onTick() {
        if (running) {
            tpsMonitor.markTick();
        }
    }

    public Average getTicksPerSecondL() {
        return ticksPerSecondL;
    }

    public void setTicksPerSecondL(Average ticksPerSecondL) {
        this.ticksPerSecondL = ticksPerSecondL;
    }

    public double getTicksPerSecond() {
        return ticksPerSecond;
    }

    public void setTicksPerSecond(double ticksPerSecond) {
        this.ticksPerSecond = ticksPerSecond;
    }

    public double getTicksPerSecondRaw() {
        return ticksPerSecondRaw;
    }

    public void setTicksPerSecondRaw(double ticksPerSecondRaw) {
        this.ticksPerSecondRaw = ticksPerSecondRaw;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public TPSMonitor getTpsMonitor() {
        return tpsMonitor;
    }

    public void setTpsMonitor(TPSMonitor tpsMonitor) {
        this.tpsMonitor = tpsMonitor;
    }

    public Average getTickTimeL() {
        return tickTimeL;
    }

    public void setTickTimeL(Average tickTimeL) {
        this.tickTimeL = tickTimeL;
    }

    public double getTickTime() {
        return tickTime;
    }

    public void setTickTime(double tickTime) {
        this.tickTime = tickTime;
    }

    public double getTickTimeRaw() {
        return tickTimeRaw;
    }

    public void setTickTimeRaw(double tickTimeRaw) {
        this.tickTimeRaw = tickTimeRaw;
    }

    public double getTickUtilizationRaw() {
        return tickUtilizationRaw;
    }

    public void setTickUtilizationRaw(double tickUtilizationRaw) {
        this.tickUtilizationRaw = tickUtilizationRaw;
    }

    public double getTickUtilization() {
        return tickUtilization;
    }

    public void setTickUtilization(double tickUtilization) {
        this.tickUtilization = tickUtilization;
    }

    public double getLeftoverTickTime() {
        return leftoverTickTime;
    }

    public void setLeftoverTickTime(double leftoverTickTime) {
        this.leftoverTickTime = leftoverTickTime;
    }

    public MemoryMonitor getMemoryMonitor() {
        return memoryMonitor;
    }

    public void setMemoryMonitor(MemoryMonitor memoryMonitor) {
        this.memoryMonitor = memoryMonitor;
    }

    public long getMemoryUse() {
        return memoryUse;
    }

    public void setMemoryUse(long memoryUse) {
        this.memoryUse = memoryUse;
    }

    public long getMemoryAllocated() {
        return memoryAllocated;
    }

    public void setMemoryAllocated(long memoryAllocated) {
        this.memoryAllocated = memoryAllocated;
    }

    public long getMemoryCollected() {
        return memoryCollected;
    }

    public void setMemoryCollected(long memoryCollected) {
        this.memoryCollected = memoryCollected;
    }

    public Average getMahL() {
        return mahL;
    }

    public void setMahL(Average mahL) {
        this.mahL = mahL;
    }

    public long getMahs() {
        return mahs;
    }

    public void setMahs(long mahs) {
        this.mahs = mahs;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getTotalEntities() {
        return totalEntities;
    }

    public void setTotalEntities(int totalEntities) {
        this.totalEntities = totalEntities;
    }

    public int getTotalDrops() {
        return totalDrops;
    }

    public void setTotalDrops(int totalDrops) {
        this.totalDrops = totalDrops;
    }

    public int getTotalTiles() {
        return totalTiles;
    }

    public void setTotalTiles(int totalTiles) {
        this.totalTiles = totalTiles;
    }

    public int getTotalLiving() {
        return totalLiving;
    }

    public void setTotalLiving(int totalLiving) {
        this.totalLiving = totalLiving;
    }

    public WorldMonitor getWorldMonitor() {
        return worldMonitor;
    }

    public void setWorldMonitor(WorldMonitor worldMonitor) {
        this.worldMonitor = worldMonitor;
    }

    public int getChunksLoaded() {
        return chunksLoaded;
    }

    public void setChunksLoaded(int chunksLoaded) {
        this.chunksLoaded = chunksLoaded;
    }

    public int getChunksUnloaded() {
        return chunksUnloaded;
    }

    public void setChunksUnloaded(int chunksUnloaded) {
        this.chunksUnloaded = chunksUnloaded;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public StackTraceElement[] getLockStack() {
        return lockStack;
    }

    public GMap<Long, GList<StackTraceElement>> getSpikes() {
        return spikes;
    }
}
