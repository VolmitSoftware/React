package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.content.sampler.SamplerUnknown;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class SampleController extends TickedObject implements IController {
    private Map<String, Sampler> samplers;
    private Sampler unknown;

    public SampleController() {
        super("react", "sample", 30000);
        start();
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Sample";
    }

    public Sampler getSampler(String id) {
        Sampler s = samplers.get(id);

        s = s == null ? unknown : s;

        if (s == null) {
            s = new SamplerUnknown();
        }

        return s;
    }

    @Override
    public void start() {
        samplers = new HashMap<>();
        samplers.put(SamplerUnknown.ID, new SamplerUnknown());
        String p = React.instance.jar().getAbsolutePath();
        p = p.replaceAll("\\Q.jar.jar\\E", ".jar");

        JarScanner j = new JarScanner(new File(p), "com.volmit.react.content.sampler");
        try {
            j.scan();
            j.getClasses().stream()
                    .filter(i -> i.isAssignableFrom(Sampler.class) || Sampler.class.isAssignableFrom(i))
                    .map((i) -> {
                        try {
                            return (Sampler) i.getConstructor().newInstance();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        return null;
                    })
                    .forEach((i) -> {
                        if (i != null) {
                            samplers.put(i.getId(), i);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //J.s(() -> React.burst.lazy(this::scanForExternalSamplers), 0);
    }

    public void postStart() {
        samplers.values().forEach((i) -> {
            i.loadConfiguration();
            i.start();
        });
        React.info("Registered " + samplers.size() + " Samplers");
        React.instance.getPlayerController().updateMonitors();
    }

    @Override
    public void stop() {
        samplers.values().forEach(Sampler::stop);
    }
}
