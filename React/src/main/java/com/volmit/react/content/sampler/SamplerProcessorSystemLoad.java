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

import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.atomics.AsyncRequest;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;
import org.bukkit.Material;

public class SamplerProcessorSystemLoad extends ReactCachedSampler {
    public static final String ID = "processor-system-load";
    private transient final AsyncRequest<Double> poller;

    public SamplerProcessorSystemLoad() {
        super(ID, 100);
        poller = new AsyncRequest<>(Platform.CPU::getCPULoad, 0D);
    }

    @Override
    public Material getIcon() {
        return Material.RED_CANDLE;
    }

    @Override
    public double onSample() {
        return poller.request();
    }

    @Override
    public String formattedValue(double t) {
        return Form.pc(t, 0);
    }

    @Override
    public String formattedSuffix(double t) {
        return "CPU";
    }
}
