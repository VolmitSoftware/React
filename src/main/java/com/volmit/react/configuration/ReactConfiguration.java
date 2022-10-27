package com.volmit.react.configuration;

import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.sampler.SamplerProcessorOutsideLoad;
import com.volmit.react.sampler.SamplerChunksLoaded;
import com.volmit.react.sampler.SamplerEntities;
import com.volmit.react.sampler.SamplerMemoryPressure;
import com.volmit.react.sampler.SamplerMemoryUsedAfterGC;
import com.volmit.react.sampler.SamplerProcessorProcessLoad;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import com.volmit.react.util.C;
import lombok.Data;

@Data
public class ReactConfiguration {
    private static ReactConfiguration configuration;

    private MonitorConfiguration monitorConfiguration = MonitorConfiguration.builder()
        .group(MonitorGroup.builder()
            .name("CPU")
            .color(C.GREEN)
            .sampler(SamplerTicksPerSecond.ID)
            .sampler(SamplerProcessorProcessLoad.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Memory")
            .color(C.GOLD)
            .sampler(SamplerMemoryUsedAfterGC.ID)
            .sampler(SamplerMemoryPressure.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("World")
            .color(C.RED)
            .sampler(SamplerChunksLoaded.ID)
            .sampler(SamplerEntities.ID)
            .build())
        .build();

    public static ReactConfiguration get() {
        if(configuration == null) {
            configuration = new ReactConfiguration();
        }

        return configuration;
    }
}
