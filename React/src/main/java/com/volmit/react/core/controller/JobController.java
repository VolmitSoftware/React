package com.volmit.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.React;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JobController implements IController {
    private double maxComputeTime = 1;
    private double maxSpikeComputeTime = 5;
    private long maxSpikeInterval = 250;
    private double currentComputeTarget = 0.01;
    private double highUtilizationThresholdPercent = 0.75;
    private double lowUtilizationThresholdPercent = 0.25;
    private transient RollingSequence usage = new RollingSequence(20);
    private transient double costPerJob = 0.1;
    private transient final RollingSequence usageCyclePercent;
    private transient final List<Runnable> jobs;
    private transient ChronoLatch spikeLatch;
    private transient int code;
    private transient double overBudget = 0;

    public JobController() {
        usageCyclePercent = new RollingSequence(20);
        jobs = new ArrayList<>();
        start();
    }

    @Override
    public String getName() {
        return "Job";
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

    public double getQueuedComputeTime() {
        return jobs.size() * costPerJob;
    }

    public void execute() {
        if(overBudget > maxComputeTime) {
            overBudget -= maxComputeTime;
            overBudget = overBudget < 0 ? 0 : overBudget;
            return;
        }

        synchronized(jobs) {
            if(jobs.isEmpty()) {
                return;
            }

            int executed = 0;
            PrecisionStopwatch p = PrecisionStopwatch.start();

            boolean spiking = jobs.size() > 1000 && spikeLatch.couldFlip() && (getQueuedComputeTime() > maxComputeTime * 3) && spikeLatch.flip();

            while(p.getMilliseconds() < (spiking ? maxSpikeComputeTime : currentComputeTarget)) {
                if(jobs.isEmpty()) {
                    break;
                }

                try {
                    jobs.remove(0).run();
                }

                catch(Throwable e) {
                    e.printStackTrace();
                }

                executed++;
            }

            double timeUsed = p.getMilliseconds();
            usage.put(timeUsed);
            if(!spiking) {
                if(timeUsed > currentComputeTarget) {
                    overBudget += timeUsed - currentComputeTarget;
                }

                else {
                    overBudget -= currentComputeTarget - timeUsed;
                    overBudget = overBudget < 0 ? 0 : overBudget;
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
    }

    public void queue(Runnable r) {
        synchronized(jobs){
            jobs.add(r);
        }
    }
}
