/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util.tick;


import art.arcane.multiburst.BurstExecutor;
import art.arcane.multiburst.MultiBurst;
import com.volmit.react.React;
import com.volmit.react.sampler.SamplerChunksLoaded;
import com.volmit.react.sampler.SamplerMemoryGarbage;
import com.volmit.react.sampler.SamplerMemoryPressure;
import com.volmit.react.sampler.SamplerMemoryUsed;
import com.volmit.react.sampler.SamplerMemoryUsedAfterGC;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import com.volmit.react.util.J;
import com.volmit.react.util.Looper;
import manifold.ext.rt.api.Self;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

public class Ticker {
    private final List<Ticked> ticklist;
    private final List<Ticked> newTicks;
    private final List<String> removeTicks;
    private volatile boolean ticking;
    private final Looper looper;

    public Ticker() {
        this.ticklist = new ArrayList<>(4096);
        this.newTicks = new ArrayList<>(128);
        this.removeTicks = new ArrayList<>(128);
        ticking = false;
        looper = new Looper() {
            @Override
            protected long loop() {
                if(!ticking) {
                    tick();
                }

                return 50;
            }
        };
        looper.start();
    }

    public void register(Ticked ticked) {
        synchronized(newTicks) {
            newTicks.add(ticked);
        }
    }

    public void unregister(Ticked ticked) {
        synchronized(removeTicks) {
            removeTicks.add(ticked.getId());
        }
    }

    private void tick() {
        ticking = true;
//        int ix = 0;
        AtomicInteger tc = new AtomicInteger(0);
        BurstExecutor e = MultiBurst.burst.burst(ticklist.size());
        for(int i = 0; i < ticklist.size(); i++) {
            int ii = i;
//            ix++;
            e.queue(() -> {
                Ticked t = ticklist.get(ii);

                if(t != null && t.shouldTick()) {
                    tc.incrementAndGet();
                    try {
                        t.tick();
                    } catch(Throwable exxx) {
                        exxx.printStackTrace();
                    }
                }
            });
        }

        e.complete();
//        Adapt.info(ix + "");

        synchronized(newTicks) {
            while(newTicks.isNotEmpty()) {
                tc.incrementAndGet();
                ticklist.add(newTicks.popRandom());
            }
        }

        synchronized(removeTicks) {
            while(removeTicks.isNotEmpty()) {
                tc.incrementAndGet();
                String id = removeTicks.popRandom();

                for(int i = 0; i < ticklist.size(); i++) {
                    if(ticklist.get(i).getId().equals(id)) {
                        ticklist.remove(i);
                        break;
                    }
                }
            }
        }

        ticking = false;
        tc.get();
    }

    public void close() {
        looper.interrupt();
    }
}
