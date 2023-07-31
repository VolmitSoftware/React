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

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.core.controller.JobController;
import com.volmit.react.util.format.Form;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public class SamplerReactJobQueueTime extends ReactCachedSampler {
    public static final String ID = "react-job-queue-time";

    public SamplerReactJobQueueTime() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.MUSIC_DISC_5;
    }

    @Override
    public double onSample() {
        return React.controller(JobController.class).getQueuedComputeTime();
    }

    @Override
    public Component format(Component value, Component suffix) {
        return Component.empty().append(value).append(suffix);
    }

    @Override
    public String formattedValue(double t) {
        return Form.durationSplit(t, 2)[0];
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.durationSplit(t, 2)[1] + " JQT";
    }
}
