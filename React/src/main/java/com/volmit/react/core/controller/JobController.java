package com.volmit.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.React;
import com.volmit.react.api.event.layer.ServerTickEvent;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.math.RollingSequence;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import lombok.Data;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

@Data
public class JobController implements IController {
    private double maxComputeTime = 1;
    private long maxSpikeInterval = 250;
    private double currentComputeTarget = 0.01;
    private double highUtilizationThresholdPercent = 0.75;
    private double lowUtilizationThresholdPercent = 0.25;
    private transient ServerTickEvent ste = new ServerTickEvent();
    private transient RollingSequence usage = new RollingSequence(20);
    private transient double costPerJob = 0.1;
    private transient final RollingSequence usageCyclePercent;
    private transient final List<Runnable> jobs;
    private transient ChronoLatch spikeLatch;
    private transient int code;
    private transient double overBudget = 0;

    public JobController() {
        usageCyclePercent = new RollingSequence(7);
        jobs = new ArrayList<>();
    }

    @Override
    public String getName() {
        return "Job";
    }

    @Override
    public String getId() {
        return "job";
    }

    @Override
    public void start() {
        spikeLatch = new ChronoLatch(maxSpikeInterval);
        code = J.sr(this::execute, 0);
    }

    @Override
    public void stop() {
        J.csr(code);

        for(Runnable i : jobs) {
            try {
                i.run();
            }

            catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void postStart() {

    }

    public double getQueuedComputeTime() {
        return jobs.size() * costPerJob;
    }

    public void execute() {
        Bukkit.getPluginManager().callEvent(ste);
        if(overBudget > maxComputeTime) {
            overBudget -= maxComputeTime;
            overBudget = overBudget < 0 ? 0 : overBudget;
            usage.put(0);
            return;
        }

        synchronized(jobs) {
            if(jobs.isEmpty()) {
                return;
            }

            int executed = 0;
            PrecisionStopwatch p = PrecisionStopwatch.start();

            while(p.getMilliseconds() < (currentComputeTarget)) {
                if(jobs.isEmpty()) {
                    break;
                }

                try {
                    if(jobs.size() > 5) {
                        jobs.remove(RNG.r.i(jobs.size()-1)).run();
                    }

                    else {
                        jobs.remove(0).run();
                    }
                }

                catch(Throwable e) {
                    e.printStackTrace();
                }

                executed++;
            }

            double timeUsed = p.getMilliseconds();
            usage.put(timeUsed);
            if(timeUsed > currentComputeTarget) {
                overBudget += timeUsed - currentComputeTarget;
            }

            costPerJob = timeUsed / (double)executed;
            usageCyclePercent.put(timeUsed / currentComputeTarget);

            if(usageCyclePercent.getAverage() > highUtilizationThresholdPercent) {
                currentComputeTarget = M.lerp(currentComputeTarget, maxComputeTime, 0.01);
            }

            else if(usageCyclePercent.getAverage() < lowUtilizationThresholdPercent) {
                currentComputeTarget = M.lerp(currentComputeTarget, 0.01, 0.01);
            }

            currentComputeTarget = M.clip(currentComputeTarget, 0.01, maxComputeTime);
        }
    }

    public void queue(Runnable r) {
        synchronized(jobs){
            jobs.add(r);
        }
    }
}
