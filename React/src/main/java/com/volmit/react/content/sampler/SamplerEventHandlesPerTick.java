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

package com.volmit.react.content.sampler;

import art.arcane.chrono.RollingSequence;
import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.core.controller.EventController;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.event.Listener;

public class SamplerEventHandlesPerTick extends ReactCachedSampler implements Listener {
    public static final String ID = "event-handles-per-tick";
    private transient EventController eventController;
    private transient RollingSequence r = new RollingSequence(36);

    public SamplerEventHandlesPerTick() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.HEART_OF_THE_SEA;
    }

    @Override
    public double onSample() {
        r.put(eventController.getCalls());
        return r.getAverage();
    }

    @Override
    public void start() {
        super.start();
        eventController = React.controller(EventController.class);
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "EVENT/t";
    }
}
