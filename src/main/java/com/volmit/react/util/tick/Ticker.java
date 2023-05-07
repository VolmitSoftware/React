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
import com.volmit.react.util.Looper;
import com.volmit.react.util.PrecisionStopwatch;
import com.volmit.react.util.RollingSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Ticker {
    private final Random random;
    private final List<Ticked> ticklist;
    private final List<Ticked> newTicks;
    private final List<String> removeTicks;
    private final RollingSequence tasksPerSecond;
    private final RollingSequence tickTime;
    private final MultiBurst burst;
    private final Looper looper;
    private volatile boolean ticking;
    private boolean closed;

    public Ticker(MultiBurst burst) {
        random = new Random();
        this.burst = burst;
        this.closed = false;
        this.ticklist = new ArrayList<>(4096);
        this.newTicks = new ArrayList<>(128);
        this.removeTicks = new ArrayList<>(128);
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

    public void register(Ticked ticked) {
        synchronized (newTicks) {
            newTicks.add(ticked);
        }
    }

    public void unregister(Ticked ticked) {
        synchronized (removeTicks) {
            removeTicks.add(ticked.getId());
        }
    }

    private int tick() {
        ticking = true;
        int ix = 0;
        AtomicInteger tc = new AtomicInteger(0);
        BurstExecutor e = burst.burst(ticklist.size());
        for (int i = 0; i < ticklist.size(); i++) {
            int ii = i;
            ix++;
            e.queue(() -> {
                Ticked t = ticklist.get(ii);

                if (t != null && t.shouldTick()) {
                    tc.incrementAndGet();
                    try {
                        PrecisionStopwatch p = PrecisionStopwatch.start();
                        t.tick();
                        p.end();

                        if (p.getMilliseconds() > 50) {
                            System.out.println("Tick " + t.getId() + " took " + p.getMilliseconds() + "ms");
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
                ticklist.add(newTicks.remove(random.nextInt(newTicks.size())));
            }
        }

        synchronized (removeTicks) {
            while (!removeTicks.isEmpty()) {
                tc.incrementAndGet();
                String id = removeTicks.remove(random.nextInt(removeTicks.size()));

                for (int i = 0; i < ticklist.size(); i++) {
                    if (ticklist.get(i).getId().equals(id)) {
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
}
