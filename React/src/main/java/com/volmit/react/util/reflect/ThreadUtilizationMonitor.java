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

package com.volmit.react.util.reflect;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.RollingSequence;
import com.volmit.react.util.math.M;

public class ThreadUtilizationMonitor extends Thread {
    private final Thread target;
    private final RollingSequence average;
    private long lastAccess;
    private boolean active;

    public ThreadUtilizationMonitor(Thread target, int memory) {
        super("React Monitor[" + target.getName() + "]");
        setPriority(Thread.MAX_PRIORITY);
        this.target = target;
        this.average = new RollingSequence(memory);
        this.lastAccess = M.ms();
        active = true;
    }

    public double getAverage() {
        lastAccess = M.ms();
        return average.getAverage();
    }

    public void run() {
        boolean active = false;
        boolean lastActive = false;
        PrecisionStopwatch p = PrecisionStopwatch.start();

        while (!isInterrupted()) {
            lastActive = active;
            active = target.getState() == State.RUNNABLE;

            if (lastActive != active) {
                if (active) {
                    p.reset();
                    p.begin();
                } else {
                    average.put(p.getMilliseconds());
                }
            }

            try {
                Thread.sleep(M.ms() - lastAccess > 1000 ? 50 : 0);
            } catch (Throwable e) {
                break;
            }
        }
    }
}
