package com.volmit.react.sampler;

import art.arcane.quill.execution.ChronoLatch;
import lombok.Data;
import org.bukkit.ChatColor;

@Data
public abstract class ReactSampler implements Sampler {

    private transient String name;
    private transient String description;
    private transient String prefix;
    private transient String suffix;
    private transient ChronoLatch sampleLimit;
    private transient double lastValue;
    private ChatColor valueColor;
    private double maxSamplesPerSecond = 20;

    public ReactSampler()
    {
        setName("Unknown");
        setDescription("Unknown");
        setPrefix("");
        setSuffix("");
        setValueColor(ChatColor.WHITE);
    }

    public double sample()
    {
        if(shouldSample())
        {
            setLastValue(onSample());
        }

        return getLastValue();
    }

    public abstract double onSample();

    public boolean shouldSample()
    {
        if(sampleLimit == null)
        {
            sampleLimit = new ChronoLatch((long) maxSamplesPerSecond, true);
        }

        return sampleLimit.flip();
    }

    public String toString()
    {
        return format(getLastValue());
    }

    public double getLastValue()
    {
        return lastValue;
    }

    public void setLastValue(double t)
    {
        this.lastValue = t;
    }

    @Override
    public abstract String format(double value);
}
