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

import art.arcane.chrono.RollingSequence;
import com.volmit.react.React;
import com.volmit.react.util.scheduling.TickedObject;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ReactTickedSampler extends TickedObject implements Sampler {
    private transient final RollingSequence ssequence;
    private transient final AtomicLong slastSample;
    private transient final long sactiveInterval;
    private transient boolean ssleeping;

    public ReactTickedSampler(String id, long tickInterval, int memory) {
        super("sampler", id, tickInterval);
        this.ssequence = new RollingSequence(memory);
        this.sactiveInterval = tickInterval;
        this.slastSample = new AtomicLong(0);
        this.ssleeping = true;
    }

    @Override
    public void start() {
        React.instance.getTicker().register(this);
    }

    public void stop() {
        unregister();
    }

    public abstract double onSample();

    @Override
    public double sample() {
        slastSample.set(System.currentTimeMillis());

        if (ssleeping) {
            setSsleeping(false);
        }

        return ssequence.getAverage();
    }

    private void setSsleeping(boolean ssleeping) {
        this.ssleeping = ssleeping;
        setTinterval(ssleeping ? 1000 : sactiveInterval);
        if (!this.ssleeping && ssleeping) {
            ssequence.resetExtremes();
        }
    }

    @Override
    public void onTick() {
        if (ssleeping) {
            return;
        }

        if (System.currentTimeMillis() - slastSample.get() > 1000) {
            setSsleeping(true);
        }

        try {
            ssequence.put(onSample());
        } catch (Throwable e) {

        }
    }

    public String getId() {
        return super.getTid();
    }
}
