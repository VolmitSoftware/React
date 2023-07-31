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
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerChunksLoaded extends ReactCachedSampler implements Listener {
    public static final String ID = "chunks-loaded";
    private static final double D1_OVER_SECONDS = 1.0 / 1000D;
    private transient final AtomicInteger generated;
    private transient RollingSequence avg;
    private transient long lastSample = 0L;
    private int sequenceAverageLength = 5;

    public SamplerChunksLoaded() {
        super(ID, 1000); // 1 tick interval for higher accuracy
        generated = new AtomicInteger(0);
    }

    @Override
    public Material getIcon() {
        return Material.COMMAND_BLOCK_MINECART;
    }

    @Override
    public void start() {
        super.start();
        avg = new RollingSequence(sequenceAverageLength);
    }

    @EventHandler
    public void on(ChunkLoadEvent event) {
        generated.incrementAndGet();
    }

    @Override
    public double onSample() {
        if (lastSample == 0) {
            lastSample = M.ms();
        }

        int r = generated.getAndSet(0);
        long dur = Math.max(M.ms() - lastSample, 1000);
        lastSample = M.ms();
        avg.put(r / (dur * D1_OVER_SECONDS));

        return avg.getAverage();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "LOADS/s";
    }
}
