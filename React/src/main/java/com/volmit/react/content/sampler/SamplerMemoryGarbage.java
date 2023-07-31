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
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;

public class SamplerMemoryGarbage extends ReactCachedSampler {
    public static final String ID = "memory-garbage";
    private transient Sampler memoryUsedAfterGC;
    private transient Sampler memoryUsed;

    public SamplerMemoryGarbage() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.WATER_BUCKET;
    }

    @Override
    public void start() {
        memoryUsedAfterGC = getSampler(SamplerMemoryUsedAfterGC.ID);
        memoryUsed = getSampler(SamplerMemoryUsed.ID);
    }

    @Override
    public double onSample() {
        return Math.max(memoryUsed.sample() - memoryUsedAfterGC.sample(), 0);
    }

    @Override
    public String formattedValue(double t) {
        String[] s = Form.memSizeSplit((long) t, 1);
        if (s[1].equalsIgnoreCase("mb")) {
            return Form.memSizeSplit((long) t, 0)[0];
        } else {
            return s[0];
        }
    }

    @Override
    public String formattedSuffix(double t) {
        return Form.memSizeSplit((long) t, 1)[1];
    }
}
