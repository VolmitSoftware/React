package com.volmit.react.sampler;

import com.volmit.react.Lang;
import com.volmit.react.api.IFormatter;
import com.volmit.react.api.MSampler;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.F;
import primal.util.text.C;

public class SampleMemoryMax extends MSampler {
    private final IFormatter formatter;

    public SampleMemoryMax() {
        formatter = new IFormatter() {
            @Override
            public String from(double d) {
                return F.memSize((long) d);
            }
        };
    }

    @Override
    public void construct() {
        setName(Lang.getString("sampler.memory-max.name")); //$NON-NLS-1$
        setDescription(Lang.getString("sampler.memory-max.description")); //$NON-NLS-1$
        setID(SampledType.MAXMEM.toString());
        setValue(1);
        setColor(C.GOLD, C.GOLD);
        setInterval(1);
    }

    @Override
    public void sample() {
        setValue(ss().getMemoryMonitor().getMemoryMax());
    }

    @Override
    public String get() {
        return getFormatter().from(getValue());
    }

    @Override
    public IFormatter getFormatter() {
        return formatter;
    }
}
