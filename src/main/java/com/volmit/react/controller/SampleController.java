package com.volmit.react.controller;

import art.arcane.amulet.Amulet;
import com.volmit.react.React;
import com.volmit.react.api.Sampler;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import com.volmit.react.util.IController;
import com.volmit.react.util.J;
import com.volmit.react.util.JarScanner;
import com.volmit.react.util.tick.Ticked;
import lombok.Data;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class SampleController implements IController {
    private Map<String, Sampler> samplers;

    @Override
    public String getName() {
        return "Sample";
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
            samplers = samplers.unmodifiable();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
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

    }

    @Override
    public int getTickInterval() {
        return 100000;
    }

    @Override
    public void l(Object l) {

    }

    @Override
    public void v(Object l) {

    }
}
