/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTickedTweak;
import com.volmit.react.api.tweak.Tweak;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class TweakController extends TickedObject implements IController {
    private transient Registry<Tweak> tweaks;
    private transient Map<String, Tweak> activeTweaks;
    private transient Map<String, ReactTickedTweak> tickedTweaks;

    private Tweak unknown;

    public TweakController() {
        super("react", "tweak", 50);
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Tweaks";
    }

    public Tweak getTweak(String id) {
        return tweaks.get(id);
    }

    public void activateTweak(Tweak tweak) {
        if (!activeTweaks.containsKey(tweak.getId())) {
            activeTweaks.put(tweak.getId(), tweak);
            tweak.onActivate();
            if (tweak instanceof Listener l) {
                React.instance.registerListener(l);
            }

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

        if (tweak instanceof Listener l) {
            React.instance.unregisterListener(l);
        }

        tweak.onDeactivate();
        React.verbose("Deactivated Tweak: " + tweak.getName());
    }

    @Override
    public void start() {
        activeTweaks = new HashMap<>();
        tickedTweaks = new HashMap<>();
        tweaks = new Registry<>(Tweak.class, "com.volmit.react.content.tweak");
    }

    public void postStart() {
        React.info("Registered " + tweaks.size() + " Tweaks");

        for (String i : tweaks.ids()) {
            Tweak f = tweaks.get(i);

            if (f.isEnabled()) {
                activateTweak(f);
            }
        }

        React.info("Activated " + activeTweaks.size() + " Tweaks");
    }

    @Override
    public void stop() {
        new ArrayList<>(activeTweaks.values()).forEach(this::deactivateTweak);
    }
}
