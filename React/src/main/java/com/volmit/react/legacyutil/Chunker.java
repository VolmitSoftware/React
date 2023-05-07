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

package com.volmit.react.legacyutil;

import art.arcane.chrono.ChronoLatch;
import com.volmit.react.util.scheduling.J;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Chunker<T> {
    private final List<T> q;
    private ExecutorService executor;
    private int threads;
    private int workload;

    public Chunker(List<T> q) {
        this.q = q;
    }

    public Chunker<T> threads(int threads) {
        this.threads = threads;
        return this;
    }

    public Chunker<T> workload(int workload) {
        this.workload = workload;
        return this;
    }

    public void execute(Consumer<T> consumer, Callback<Double> progress, int progressInterval) {
        ChronoLatch cl = new ChronoLatch(progressInterval);
        Contained<Integer> consumed = new Contained<Integer>(0);
        executor = Executors.newFixedThreadPool(threads);
        int length = q.size();
        int remaining = length;

        while (remaining > 0) {
            int at = remaining;
            remaining -= (remaining > workload ? workload : remaining);
            int to = remaining;

            executor.submit(() ->
            {
                J.dofor(at, (i) -> i >= to, -1, (i) -> J.attempt(() -> consumer.accept(q.get(i))));
                consumed.mod((c) -> c += workload);
                J.doif(() -> progress != null && cl.flip(), () -> progress.run((double) consumed.get() / (double) length));
            });
        }

        executor.shutdown();
        J.attempt(() -> executor.awaitTermination(100, TimeUnit.HOURS));
    }
}
