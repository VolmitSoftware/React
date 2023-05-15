package com.volmit.react.model;

import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.content.sampler.*;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.json.JSONObject;
import lombok.Data;

import java.io.File;
import java.io.IOException;

@Data
public class ReactConfiguration {
    private static ReactConfiguration configuration;
    private EntityPriority priority = new EntityPriority();
    private boolean customColors = true;
    private boolean verbose = false;
    private Monitoring monitoring = new Monitoring();

    public static ReactConfiguration get() {
        if (configuration == null) {
            ReactConfiguration dummy = new ReactConfiguration();
            File l = React.instance.getDataFile("config.json");

            if (!l.exists()) {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                    configuration = dummy;
                    return dummy;
                }
            }

            try {
                configuration = new Gson().fromJson(IO.readAll(l), ReactConfiguration.class);
                IO.writeAll(l, new JSONObject(new Gson().toJson(configuration)).toString(4));
            } catch (IOException e) {
                e.printStackTrace();
                configuration = new ReactConfiguration();
            }
        }

        return configuration;
    }

    @Data
    public static class Monitoring {
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
                        .sampler(SamplerReactJobsQueue.ID)
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
    }
}
