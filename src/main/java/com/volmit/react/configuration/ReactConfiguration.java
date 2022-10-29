package com.volmit.react.configuration;

import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.sampler.SamplerEventListeners;
import com.volmit.react.sampler.SamplerEventHandlesPerTick;
import com.volmit.react.sampler.SamplerEventTime;
import com.volmit.react.sampler.SamplerPlayers;
import com.volmit.react.sampler.SamplerProcessorOutsideLoad;
import com.volmit.react.sampler.SamplerChunksLoaded;
import com.volmit.react.sampler.SamplerEntities;
import com.volmit.react.sampler.SamplerMemoryPressure;
import com.volmit.react.sampler.SamplerMemoryUsedAfterGC;
import com.volmit.react.sampler.SamplerProcessorProcessLoad;
import com.volmit.react.sampler.SamplerProcessorSystemLoad;
import com.volmit.react.sampler.SamplerReactTasksPerSecond;
import com.volmit.react.sampler.SamplerReactTickTime;
import com.volmit.react.sampler.SamplerTickTime;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import lombok.Data;

@Data
public class ReactConfiguration {
    private static ReactConfiguration configuration;

    private MonitorConfiguration monitorConfiguration = MonitorConfiguration.builder()
        .group(MonitorGroup.builder()
            .name("CPU")
            .color("#00ff73")
            .sampler(SamplerTicksPerSecond.ID)
            .sampler(SamplerTickTime.ID)
            .sampler(SamplerProcessorProcessLoad.ID)
            .sampler(SamplerProcessorSystemLoad.ID)
            .sampler(SamplerProcessorOutsideLoad.ID)
            .sampler(SamplerReactTickTime.ID)
            .sampler(SamplerReactTasksPerSecond.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Memory")
            .color("#ee00ff")
            .sampler(SamplerMemoryUsedAfterGC.ID)
            .sampler(SamplerMemoryPressure.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("World")
            .color("#42cbf5")
            .sampler(SamplerChunksLoaded.ID)
            .sampler(SamplerEntities.ID)
            .sampler(SamplerPlayers.ID)
            .build())
        .group(MonitorGroup.builder()
            .name("Bukkit")
            .color("#f25a02")
            .sampler(SamplerEventHandlesPerTick.ID)
            .sampler(SamplerEventTime.ID)
            .sampler(SamplerEventListeners.ID)
            .build())
        .build();

    public static ReactConfiguration get() {
        if(configuration == null) {
            configuration = new ReactConfiguration();
        }

        return configuration;
    }
}
