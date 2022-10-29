package com.volmit.react.controller;

import art.arcane.curse.Curse;
import art.arcane.curse.model.CursedComponent;
import art.arcane.curse.util.JarLoader;
import com.volmit.react.React;
import com.volmit.react.api.sampler.ExampleSampler;
import com.volmit.react.api.sampler.ExternalSampler;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.sampler.SamplerUnknown;
import com.volmit.react.util.IController;
import com.volmit.react.util.J;
import com.volmit.react.util.JarScanner;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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

        s = s == null ? unknown : s;

        if(s == null) {
            s = new SamplerUnknown();
        }

        return s;
    }

    public void scanForExternalSamplers() {
        Stream<ExternalSampler> samplers = Stream.of();
        for(Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            samplers = Stream.concat(samplers, Curse.annotatedName(plugin.getClass(), "XReactSampler")
                .filter(i -> i.optionalMethod("sample").isPresent())
                .map(i -> i.method("sample"))
                .filter(i -> ((Method)i.getMember()).getReturnType() == double.class)
                .filter(i -> i.isPublic() && !i.isStatic() && !i.isFinal() && !i.isAbstract() && !i.isSynchronized())

                .filter(i -> Arrays.stream(i.getMember().getDeclaringClass().getDeclaredAnnotations())

                    .anyMatch(j -> j.annotationType().getSimpleName().equals("XReactSampler")
                        && Curse.on(j.annotationType()).optionalMethod("id").isPresent()
                        && ((Method)Curse.on(j.annotationType()).method("id").getMember()).getReturnType() == String.class
                        && Curse.on(j.annotationType()).optionalMethod("interval").isPresent()
                        && ((Method)Curse.on(j.annotationType()).method("interval").getMember()).getReturnType() == int.class
                        && Curse.on(j.annotationType()).optionalMethod("suffix").isPresent()
                        && ((Method)Curse.on(j.annotationType()).method("suffix").getMember()).getReturnType() == String.class
                    )).map(i -> Curse.on(i.getMember().getDeclaringClass()).construct())
                .map(i -> new ExternalSampler(i, i.method("sample"),
                    Curse.on(Arrays.stream(i.type().getDeclaredAnnotations()).where(j -> j.annotationType().getSimpleName().equals("XReactSampler")).findFirst().get()).method("id").invoke(),
                    Curse.on(Arrays.stream(i.type().getDeclaredAnnotations()).where(j -> j.annotationType().getSimpleName().equals("XReactSampler")).findFirst().get()).method("interval").invoke(),
                    Curse.on(Arrays.stream(i.type().getDeclaredAnnotations()).where(j -> j.annotationType().getSimpleName().equals("XReactSampler")).findFirst().get()).method("suffix").invoke())));
        }
        AtomicInteger m = new AtomicInteger();
        samplers.forEach(i -> {
            m.getAndIncrement();
            if(!this.samplers.containsKey(i.getId())) {
                this.samplers.put(i.getId(), i);
                i.start();
            }
        });

        if(m.get() > 0) {
            React.info("Registered " + m.get() + " External Sampler(s)");
            React.instance.getPlayerController().updateMonitors();
        }
    }

    @Override
    public void start() {
        samplers = new HashMap<>();
        samplers.put(SamplerUnknown.ID, new SamplerUnknown());
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

        J.s(() -> React.burst.lazy(this::scanForExternalSamplers), 0);
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
        return 1000;
    }

    @Override
    public void l(Object l) {

    }

    @Override
    public void v(Object l) {

    }
}
