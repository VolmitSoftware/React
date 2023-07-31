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

package com.volmit.react.util.scheduling;

import com.volmit.react.React;
import com.volmit.react.util.math.M;
import com.volmit.react.util.registry.Registered;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TickedObject implements Ticked, Listener, Registered {
    private transient final AtomicLong tlastTick;
    private transient final AtomicLong tinterval;
    private transient final AtomicInteger tskip;
    private transient final AtomicInteger tburst;
    private transient final AtomicLong tticks;
    private transient final AtomicInteger tdieIn;
    private transient final AtomicBoolean tdie;
    private transient final long tstart;
    private transient final String tgroup;
    private transient final String tid;

    public TickedObject() {
        this("null");
    }

    public TickedObject(String group, String tid) {
        this(group, tid, 1000);
    }

    public TickedObject(String group) {
        this(group, UUID.randomUUID().toString(), 1000);
    }

    public TickedObject(String group, long interval) {
        this(group, UUID.randomUUID().toString(), interval);
    }

    public TickedObject(String group, String tid, long interval) {
        this.tgroup = group;
        this.tid = tid;
        this.tdie = new AtomicBoolean(false);
        this.tdieIn = new AtomicInteger(0);
        this.tinterval = new AtomicLong(interval);
        this.tlastTick = new AtomicLong(M.ms());
        this.tburst = new AtomicInteger(0);
        this.tskip = new AtomicInteger(0);
        this.tticks = new AtomicLong(0);
        this.tstart = M.ms();
        React.instance.getTicker().register(this);
    }

    public String getId() {
        return tid;
    }

    public void dieAfter(int ticks) {
        tdieIn.set(ticks);
        tdie.set(true);
    }

    @Override
    public void unregister() {
        React.instance.getTicker().unregister(this);
    }

    @Override
    public long getTlastTick() {
        return tlastTick.get();
    }

    @Override
    public long getTinterval() {
        if (tburst.get() > 0) {
            return 0;
        }

        return tinterval.get();
    }

    @Override
    public void setTinterval(long ms) {
        tinterval.set(ms);
    }


    @Override
    public void tick() {
        if (tskip.getAndDecrement() > 0) {
            return;
        }

        if (tdie.get() && tdieIn.decrementAndGet() <= 0) {
            unregister();
            return;
        }

        tlastTick.set(M.ms());
        tburst.decrementAndGet();
        onTick();
    }

    public abstract void onTick();

    @Override
    public String getTgroup() {
        return tgroup;
    }

    @Override
    public String getTid() {
        return tid;
    }

    @Override
    public long getTickCount() {
        return tticks.get();
    }

    @Override
    public long getAge() {
        return M.ms() - tstart;
    }

    @Override
    public boolean isBursting() {
        return tburst.get() > 0;
    }

    @Override
    public void burst(int ticks) {
        if (tburst.get() < 0) {
            tburst.set(ticks);
            return;
        }

        tburst.addAndGet(ticks);
    }

    @Override
    public boolean isSkipping() {
        return tskip.get() > 0;
    }

    @Override
    public void stopBursting() {
        tburst.set(0);
    }

    @Override
    public void stopSkipping() {
        tskip.set(0);
    }

    @Override
    public void skip(int ticks) {
        if (tskip.get() < 0) {
            tskip.set(ticks);
            return;
        }

        tskip.addAndGet(ticks);
    }
}
