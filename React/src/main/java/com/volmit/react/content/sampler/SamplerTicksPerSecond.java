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

import com.volmit.react.api.sampler.ReactTickedSampler;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Material;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SamplerTicksPerSecond extends ReactTickedSampler {
    public static final String ID = "ticks-per-second";
    private transient final AtomicInteger ticks;
    private transient final AtomicLong lastTick;
    private transient final AtomicLong lastTickDuration;
    private transient final AtomicLong lastTickDurationSync;
    private transient final int stickid;
    private int countUpTickTimeThresholdMS = 3000;

    public SamplerTicksPerSecond() {
        super(ID, 50, 7);
        this.ticks = new AtomicInteger(0);
        this.lastTickDuration = new AtomicLong(50);
        this.lastTickDurationSync = new AtomicLong(50);
        this.lastTick = new AtomicLong(System.currentTimeMillis());
        stickid = J.sr(this::onSyncTick, 0);
    }

    @Override
    public Material getIcon() {
        return Material.NAUTILUS_SHELL;
    }

    private void onSyncTick() {
        lastTick.set(System.currentTimeMillis());
        ticks.incrementAndGet();
        lastTickDurationSync.set(System.currentTimeMillis() - lastTick.get());
    }

    @Override
    public double onSample() {
        lastTickDuration.set(System.currentTimeMillis() - lastTick.get());
        return 1000D / Math.max(50D, Math.max((double) lastTickDuration.get(), (double) lastTickDurationSync.get()));
    }

    @Override
    public String formattedValue(double t) {
        long dur = System.currentTimeMillis() - lastTick.get();

        if (dur > countUpTickTimeThresholdMS) {
            return Form.durationSplit(dur, 1)[0];
        }

        if (t > 19.98) {
            return "20";
        }

        return Form.f(Math.round(t), 0);
    }

    @Override
    public String formattedSuffix(double t) {
        long dur = System.currentTimeMillis() - lastTick.get();

        if (dur > countUpTickTimeThresholdMS) {
            return Form.durationSplit(dur, 1)[1];
        }

        return "TPS";
    }

    @Override
    public void unregister() {
        J.csr(stickid);
        super.unregister();
    }
}
