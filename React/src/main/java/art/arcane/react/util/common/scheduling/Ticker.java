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

package art.arcane.react.util.scheduling;


import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.RollingSequence;
import art.arcane.multiburst.BurstExecutor;
import art.arcane.multiburst.MultiBurst;
import art.arcane.react.React;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.scheduling.Looper;

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
        if (ticked == null) {
            return;
        }

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
        int ix = ticklist.size();
        if (ix > 0) {
            BurstExecutor e = MultiBurst.burst.burst(ix);
            for (int i = 0; i < ix; i++) {
                Ticked ticked = ticklist.get(i);
                if (ticked == null || !ticked.shouldTick()) {
                    continue;
                }

                e.queue(() -> executeTick(ticked));
            }
            e.complete();
        }

        synchronized (newTicks) {
            while (!newTicks.isEmpty()) {
                Ticked ticked = newTicks.remove(0);
                if (ticked == null) {
                    continue;
                }

                if (containsTickId(ticked.getTid())) {
                    continue;
                }

                ticklist.add(ticked);
            }
        }

        synchronized (removeTicks) {
            while (removeTicks.isNotEmpty()) {
                removeTickById(removeTicks.remove(0));
            }
        }

        ticking = false;
        return ix;
    }

    private void executeTick(Ticked ticked) {
        try {
            long start = System.nanoTime();
            ticked.tick();
            long elapsedMS = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMS > 50) {
                React.warn(ticked.getTgroup() + ":" + ticked.getTid() + " took " + elapsedMS + "ms");
            }
        } catch (Throwable exxx) {
            exxx.printStackTrace();
        }
    }

    private boolean containsTickId(String id) {
        if (id == null) {
            return false;
        }

        for (int i = 0; i < ticklist.size(); i++) {
            if (id.equals(ticklist.get(i).getTid())) {
                return true;
            }
        }

        return false;
    }

    private void removeTickById(String id) {
        if (id == null) {
            return;
        }

        for (int i = ticklist.size() - 1; i >= 0; i--) {
            if (id.equals(ticklist.get(i).getTid())) {
                ticklist.remove(i);
            }
        }
    }
}
