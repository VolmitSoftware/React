package com.volmit.react.util;

import art.arcane.quill.math.Profiler;
import art.arcane.quill.math.RollingSequence;

public class Meter
{
    private final RollingSequence metrics;
    private Profiler profiler;

    public Meter(int memory)
    {
        metrics = new RollingSequence(memory);
        profiler = new Profiler();
    }

    public void start()
    {
        if(!profiler.isProfiling())
        {
            profiler.begin();
        }
    }

    public void stop()
    {
        if(profiler.isProfiling())
        {
            profiler.end();
            metrics.put(profiler.getMilliseconds());
        }
    }
}
