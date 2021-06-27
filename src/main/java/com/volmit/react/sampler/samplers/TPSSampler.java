package com.volmit.react.sampler.samplers;

import art.arcane.quill.format.Form;
import art.arcane.quill.math.Profiler;
import com.volmit.react.React;
import com.volmit.react.sampler.DoubleSampler;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

@Data
public class TPSSampler extends DoubleSampler {
    private transient Profiler profiler;

    public TPSSampler()
    {
        setName("Ticks Per Second");
        setSuffix(" TPS");
        setValueColor(ChatColor.GREEN);
        setMaxSamplesPerSecond(20);
        setLastValue(20);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(React.instance(), this::tick, 0, 0);
        profiler = Profiler.start();
    }

    public void tick()
    {
        profiler.end();
        getSequence().put(profiler.getMilliseconds());
        profiler.begin();
    }

    public double sample()
    {

    }

    @Override
    public double onSample() {
        if(profiler.getMilliseconds() <= 50)
        {
            return 50;
        }
        
        return getSequence().getAverage();
    }

    @Override
    public String format(double value) {
        return getValueColor() + Form.f(value, 0) + getSuffix();
    }
}
