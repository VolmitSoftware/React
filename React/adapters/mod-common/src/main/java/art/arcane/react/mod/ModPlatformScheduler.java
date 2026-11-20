package art.arcane.react.mod;

import art.arcane.react.core.controller.JobController;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Mod servers tick on one thread, so region and entity work runs there too, like Iris's modded scheduler. */
final class ModPlatformScheduler implements PlatformScheduler {
    private final MinecraftServer server;
    private final ModTicks ticks;
    private final ExecutorService async;
    private final JobController jobs;
    private volatile boolean active = true;
    private volatile boolean ready;

    ModPlatformScheduler(MinecraftServer server, ModTicks ticks, ExecutorService async, JobController jobs) {
        this.server = server;
        this.ticks = ticks;
        this.async = async;
        this.jobs = jobs;
    }

    void markReady() {
        ready = true;
    }

    void deactivate() {
        active = false;
        ready = false;
    }

    @Override
    public boolean isPrimaryThread() {
        return server.isSameThread();
    }

    @Override
    public boolean isRegionized() {
        return false;
    }

    @Override
    public boolean isStopping() {
        return !active || !server.isRunning();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void queue(Runnable task) {
        jobs.queue(task);
    }

    @Override
    public boolean isOwnedByCurrentRegion(Object handle) {
        return isPrimaryThread();
    }

    @Override
    public boolean isPlaced(Object location) {
        return false;
    }

    @Override
    public boolean global(Runnable task, int delayTicks) {
        return ticks.later(task, delayTicks);
    }

    @Override
    public boolean region(ReactWorld world, int chunkX, int chunkZ, Runnable task, int delayTicks) {
        return world != null && ticks.later(task, delayTicks);
    }

    @Override
    public boolean region(Object location, Runnable task, int delayTicks) {
        return ticks.later(task, delayTicks);
    }

    @Override
    public boolean entity(Object entity, Runnable task, int delayTicks, Runnable retired) {
        if (!(entity instanceof Entity handle)) {
            return false;
        }
        return ticks.later(() -> {
            if (!handle.isRemoved()) {
                task.run();
            } else if (retired != null) {
                retired.run();
            }
        }, delayTicks);
    }

    @Override
    public boolean async(Runnable task, int delayTicks) {
        if (delayTicks > 0) {
            return ticks.later(() -> submit(task), delayTicks);
        }
        return submit(task);
    }

    private boolean submit(Runnable task) {
        try {
            async.execute(task);
            return true;
        } catch (RejectedExecutionException stopped) {
            return false;
        }
    }

    @Override
    public boolean later(Runnable task, int delayTicks) {
        return ticks.later(task, delayTicks);
    }

    @Override
    public void cancelAll() {
    }
}
