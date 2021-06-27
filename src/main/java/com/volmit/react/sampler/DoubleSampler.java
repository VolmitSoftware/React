package com.volmit.react.sampler;

import art.arcane.quill.math.RollingSequence;
import lombok.Data;

@Data
public abstract class DoubleSampler extends ReactSampler {
    private transient RollingSequence sequence;
    private int averageMemory = 10;

    public DoubleSampler()
    {
        super();
    }

    public double sample()
    {
        if(shouldSample())
        {
            Double value = onSample();
            getSequence().put(value);
            setLastValue(getSequence().getAverage());
        }

        return getLastValue();
    }

    public RollingSequence getSequence()
    {
        if(sequence == null)
        {
            sequence = new RollingSequence(averageMemory);
        }

        return sequence;
    }

    public double max()
    {
        return getSequence().getMax();
    }

    public double min()
    {
        return getSequence().getMin();
    }

    public double median()
    {
        return getSequence().getMedian();
    }

    public abstract double onSample();
}
