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

package com.volmit.react.api.sampler;

import art.arcane.chrono.ChronoLatch;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class ReactCachedSampler implements Sampler {
    protected transient final long sampleDelay;
    private transient final ChronoLatch slatch;
    private transient final AtomicDouble slast;
    private transient final String sid;

    public ReactCachedSampler(String id, long sampleDelay) {
        this.sid = id;
        this.sampleDelay = sampleDelay;
        this.slatch = new ChronoLatch(sampleDelay, true);
        this.slast = new AtomicDouble();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    public abstract double onSample();

    @Override
    public double sample() {
        double t = slast.get();

        if (slatch.flip()) {
            t = onSample();
            slast.set(t);
        }

        return t;
    }

    public String getId() {
        return sid;
    }
}
