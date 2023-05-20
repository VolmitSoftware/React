package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.feature.ReactTickedFeature;
import com.volmit.react.api.tweak.ReactTickedTweak;
import com.volmit.react.api.tweak.Tweak;
import com.volmit.react.content.tweak.TweakUnknown;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class TweakController extends TickedObject implements IController {
    private Map<String, Tweak> tweaks;
    private Map<String, Tweak> activeTweaks;
    private Map<String, ReactTickedTweak> tickedTweaks;

    private Tweak unknown;

    public TweakController() {
        super("react", "tweak", 50);
        start();
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Tweaks";
    }

    public Tweak getTweak(String id) {
        Tweak s = tweaks.get(id);

        s = s == null ? unknown : s;

        if (s == null) {
            s = new TweakUnknown();
        }

        return s;
    }

    public void activateTweak(Tweak tweak) {
        if (!activeTweaks.containsKey(tweak.getId())) {
            activeTweaks.put(tweak.getId(), tweak);
            tweak.onActivate();

            if (tweak.getTickInterval() > 0) {
                tickedTweaks.put(tweak.getId(), new ReactTickedTweak(tweak));
            }

            React.verbose("Activated Tweak: " + tweak.getName());
        }
    }

    public void deactivateTweak(Tweak tweak) {
        activeTweaks.remove(tweak.getId());
        ReactTickedTweak t = tickedTweaks.remove(tweak.getId());

        if (t != null) {
            t.unregister();
        }

        tweak.onDeactivate();
        React.verbose("Deactivated Tweak: " + tweak.getName());
    }

    @Override
    public void start() {
        activeTweaks = new HashMap<>();
        tickedTweaks = new HashMap<>();
        tweaks = new HashMap<>();
        tweaks.put(TweakUnknown.ID, new TweakUnknown());
        String p = React.instance.jar().getAbsolutePath();
        p = p.replaceAll("\\Q.jar.jar\\E", ".jar");

        JarScanner j = new JarScanner(new File(p), "com.volmit.react.content.tweak");
        try {
            j.scan();
            j.getClasses().stream()
                    .filter(i -> i.isAssignableFrom(Tweak.class) || Tweak.class.isAssignableFrom(i))
                    .map((i) -> {
                        try {
                            return (Tweak) i.getConstructor().newInstance();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        return null;
                    })
                    .forEach((i) -> {
                        if (i != null) {
                            tweaks.put(i.getId(), i);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void postStart() {
        React.info("Registered " + tweaks.size() + " Tweaks");

        for (String i : tweaks.keySet()) {
            Tweak f = tweaks.get(i);
            f.loadConfiguration();

            if (f.isEnabled()) {
                activateTweak(f);
            }
        }

        React.info("Activated " + tweaks.size() + " Tweaks");
    }

    @Override
    public void stop() {
        new ArrayList<>(activeTweaks.values()).forEach(this::deactivateTweak);
    }
}
