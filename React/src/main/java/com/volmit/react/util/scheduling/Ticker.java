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


import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.RollingSequence;
import art.arcane.multiburst.BurstExecutor;
import art.arcane.multiburst.MultiBurst;
import com.volmit.react.React;
import com.volmit.react.util.collection.KList;

import java.util.concurrent.atomic.AtomicInteger;

public class Ticker {
    private final KList<Ticked> ticklist;
    private final KList<Ticked> newTicks;
    private final KList<String> removeTicks;
    private final RollingSequence tasksPerSecond;
    private final RollingSequence tickTime;
    private final Looper looper;
    private volatile boolean ticking;
    private boolean closed;

    public Ticker() {
        this.closed = false;
        this.ticklist = new KList<>(4096);
        this.newTicks = new KList<>(128);
        this.removeTicks = new KList<>(128);
        tasksPerSecond = new RollingSequence(20);
        tickTime = new RollingSequence(10);
        ticking = false;
        looper = new Looper() {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            int tps = 0;
            int tv = 0;

            @Override
            protected long loop() {
                if (closed) {
                    return 100;
                }

                if (!ticking) {
                    p = PrecisionStopwatch.start();
                    tps += tick();
                    tickTime.put(p.getMilliseconds());
                    tv++;
                    if (tv >= 20) {
                        tv = 0;
                        tasksPerSecond.put(tps);
                        tps = 0;
                    }
                }

                return 50;
            }
        };
        looper.start();
    }

    public void close() {
        closed = true;
        looper.interrupt();
    }

    public double getTasksPerSecond() {
        return tasksPerSecond.getAverage();
    }

    public double getTickTime() {
        return tickTime.getAverage();
    }

    public void register(Ticked ticked) {
        synchronized (newTicks) {
            newTicks.add(ticked);
        }
    }

    public void unregister(Ticked ticked) {
        synchronized (removeTicks) {
            removeTicks.add(ticked.getTid());
        }
    }

    public void clear() {
        synchronized (ticklist) {
            ticklist.clear();
        }
        synchronized (removeTicks) {
            removeTicks.clear();
        }
        synchronized (newTicks) {
            newTicks.clear();
        }

    }

    private int tick() {
        ticking = true;
        int ix = 0;
        AtomicInteger tc = new AtomicInteger(0);
        BurstExecutor e = MultiBurst.burst.burst(ticklist.size());
        for (int i = 0; i < ticklist.size(); i++) {
            int ii = i;
            ix++;
            e.queue(() -> {
                Ticked t = ticklist.get(ii);

                if (t != null && t.shouldTick()) {
                    tc.incrementAndGet();
                    try {
                        long ms = System.currentTimeMillis();
                        t.tick();
                        if (System.currentTimeMillis() - ms > 50) {
                            React.warn(t.getTgroup() + ":" + t.getTid() + " took " + (System.currentTimeMillis() - ms) + "ms");
                        }
                    } catch (Throwable exxx) {
                        exxx.printStackTrace();
                    }
                }
            });
        }

        e.complete();

        synchronized (newTicks) {
            while (!newTicks.isEmpty()) {
                tc.incrementAndGet();
                ticklist.add(newTicks.popRandom());
            }
        }

        synchronized (removeTicks) {
            while (removeTicks.isNotEmpty()) {
                tc.incrementAndGet();
                String id = removeTicks.popRandom();

                for (int i = 0; i < ticklist.size(); i++) {
                    if (ticklist.get(i).getTid().equals(id)) {
                        ticklist.remove(i);
                        break;
                    }
                }
            }
        }

        ticking = false;
        tc.get();
        return ix;
    }
}
