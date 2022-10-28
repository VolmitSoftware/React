package com.volmit.react.controller;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.sampler.SamplerReactTasksPerSecond;
import com.volmit.react.sampler.SamplerUnknown;
import com.volmit.react.util.IController;
import com.volmit.react.util.JarScanner;
import lombok.Data;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Data
public class SampleController implements IController {
    private Map<String, Sampler> samplers;
    private Sampler unknown;

    @Override
    public String getName() {
        return "Sample";
    }

    public Sampler getSampler(String id) {
        Sampler s = samplers.get(id);

        return s == null ? unknown : s;
    }

    @Override
    public void start() {
        samplers = new HashMap<>();
        JarScanner j = new JarScanner(React.instance.jar(), "com.volmit.react.sampler");
        try {
            j.scan();
            j.getClasses().stream()
                .where(i -> i.isAssignableFrom(Sampler.class) || Sampler.class.isAssignableFrom(i))
                .map((i) -> {
                    try
                    {
                        return (Sampler) i.getConstructor().newInstance();
                    }
                    catch(Throwable e)
                    {
                        e.printStackTrace();
                    }

                    return null;
                })
                .forEach((i) -> {
                    if(i != null) {
                        samplers.put(i.getId(), i);
                    }
                });
        } catch(IOException e) {
            throw new RuntimeException(e);
        }

        samplers.put(SamplerUnknown.ID, new SamplerUnknown());
    }

    public void postStart()
    {
        samplers.values().forEach(Sampler::start);
        React.info("Registered " + samplers.size() + " Samplers");
    }

    @Override
    public void stop() {
        samplers.values().forEach(Sampler::stop);
    }

    @Override
    public void tick() {
        React.info("Tasks/s " + getSampler(SamplerReactTasksPerSecond.ID).sampleFormatted());
    }

    @Override
    public int getTickInterval() {
        return 50;
    }

    @Override
    public void l(Object l) {

    }

    @Override
    public void v(Object l) {

    }
}
